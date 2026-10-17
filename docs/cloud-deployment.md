# 单机云部署

适用于本项目的低并发 Linux 单机部署。当前目标服务器为 OpenCloudOS 9.6、x86_64、4 核、约 3.57GiB 可用物理内存、40GB 系统盘。使用独立 Compose 项目 `fashion-recommendation-cloud`，不会复用本地开发卷。

## 访问方式

默认仅监听服务器回环地址，先通过 SSH 隧道验收。没有域名也能完成部署与本机访问。公网 HTTP 在技术上可行，但配套扩展只支持 HTTPS 公网站点和 localhost；广州服务器对公网提供网站服务前，应完成适用的备案手续，不能将 IP 访问当作免备案方式。参见[腾讯云备案场景](https://cloud.tencent.com/document/product/243/18910)。

在本机打开隧道后访问 `http://localhost:8090`：

```powershell
ssh -N -L 127.0.0.1:8090:127.0.0.1:8090 -i <本机私钥路径> root@193.112.24.191
```

当前工作区已准备专用 SSH 连接时，也可以运行 `powershell -ExecutionPolicy Bypass -File scripts/open-cloud-tunnel.ps1 -OpenBrowser`，在后台开启隧道并打开页面。该辅助脚本默认使用 `http://127.0.0.1:18090`，对应服务器 `PUBLIC_ORIGIN` 也应为该地址。使用独立端口避免占用开发环境入口；使用 127.0.0.1 可与通常位于 localhost 的开发站点分开登录 Cookie，因为 Cookie 不按端口隔离。脚本使用本机忽略目录下受权限保护的密钥副本与已核验服务器记录，不把密钥写入源码。

若本机 8090 已被开发环境占用，改用其他本机端口，并将服务器 `.env.cloud` 的 `PUBLIC_ORIGIN` 改为相应的 `http://localhost:端口`，重新创建后端。不要覆盖或停止无关的本机服务。

## 准备与部署

在本机打包当前工作树，不包含真实环境文件、私钥、用户数据或开发依赖：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-cloud-deploy.ps1
```

将生成的源码包上传到专用发布目录，例如 `/opt/zhiji/releases/<发布编号>`，解压后在该目录执行。服务器需已有 Docker Engine、Docker Compose 2.24.4+、Python 3；在小内存主机上顺序构建，必要时配置适量 swap 缓冲构建峰值。安装 Docker 应使用系统或 Docker 官方可信软件源，保持 GPG 验证。

首次初始化独立环境：

```bash
python3 scripts/cloud-init-env.py
bash scripts/cloud-deploy.sh http
```

初始化脚本生成数据库密码、对象存储密码和 32 字节 Base64 加密密钥，文件权限为 600。若 `.env.cloud` 已存在则拒绝覆盖。应把该文件长期保存在服务器专用配置目录（例如 `/opt/zhiji/shared/.env.cloud`），各发布目录通过符号链接使用；升级不能重新生成密码或加密密钥。

模型配置可在初始化时从已有环境文件中选择性导入：

```bash
python3 scripts/cloud-init-env.py --model-env-file /安全路径/已有环境文件
```

只导入明确允许的模型 Key、模型名、开关、服务地址和超时，不导入开发数据库密码或本地照片路径。不在终端打印 Key，也不把真实 `.env.cloud` 放入源码包。已启用的模型调用会使用对应服务的额度。

部署脚本先验证密码、加密密钥与访问来源，随后依次构建后端、前端和采集器，等待服务健康并检查网页与后端健康接口。脚本不会安装系统软件、修改防火墙或开放公网端口。

若服务器首次下载构建基础镜像较慢，可在本地构建 Linux x86_64 镜像，再通过 SSH 传送离线镜像包。在服务器执行 `docker load -i 镜像包.tar.gz` 后，使用 `bash scripts/cloud-deploy.sh http --no-build`，避免重复构建或拉取。该模式要求六个镜像全部已导入。应用镜像名须为 `fashion-recommendation-cloud-backend`、`fashion-recommendation-cloud-frontend`、`fashion-recommendation-cloud-trend-collector`。本次低带宽部署可复用本地既有项目运行时层，覆盖最新构建的 JAR、前端静态资源和采集源码；源码包仍保留完整原始 Dockerfile，便于后续干净重建。

固定版本的数据库和对象存储镜像也可以离线导入。因 Docker 导出包不一定保留原 registry 的 RepoDigest 关联，需要先核对本机原固定 digest，将其标记为以下离线名称，并在导入后核对 image ID 一致，再在 `.env.cloud` 加入：

```dotenv
CLOUD_POSTGRES_IMAGE=fashion-recommendation-cloud-postgres:16-3c5c8892d184
CLOUD_MINIO_IMAGE=fashion-recommendation-cloud-minio:14cea493d9a3
CLOUD_MC_IMAGE=fashion-recommendation-cloud-mc:a7fe349ef4bd
```

未设置这些变量时仍使用原固定 digest。离线名称代表本机已核验的同一镜像，不应指向任意其他版本。

## 资源与应用边界

云覆盖配置为主要容器设置内存上限、后端 JVM 堆上限、自动重启和日志轮转，保留趋势采集器；实际峰值仍需监控。采集器使用无头 Chromium，平台登录墙和网络问题会如实表现为采集失败或内容缺额，服务启动不表示采集成功。

效果图客户端在解码阶段对子图采样，最长边限制为 2048px；每个后端实例只允许一路在途生图，忙碌请求返回明确失败信息供稍后重试。没有新增本地模型或 GPU 依赖。

新环境不创建公开的默认账号，也不导入本地演示账号或个人照片。正常注册后即可使用个人功能；管理员角色应针对明确的账号单独授予，不能运行本地 demo seed 来建立生产管理员。

## HTTPS 公网接入

本次计划使用 `www.jaime.ltd`。DNS 控制台中的 `www` A 记录应指向 `193.112.24.191`；若同时使用根域名，可再添加 `@` A 记录到同一 IP。控制台保存记录后，还需确认公共 DNS 查询能取得该 IP。购买、实名认证、DNS 解析和 ICP 备案是不同步骤；广州服务器对公网开放网站前，需先完成适用的 ICP 备案，可按[腾讯云首次备案说明](https://cloud.tencent.com/document/product/243/18958)办理。

域名、备案和可信证书准备完成后，在服务器的 `.env.cloud` 中设置：

```dotenv
PUBLIC_ORIGIN=https://实际域名
TLS_CERT_DIR=/etc/zhiji/tls
FRONTEND_TLS_BIND_IP=0.0.0.0
FRONTEND_TLS_PORT=443
```

证书目录应包含覆盖访问域名的有效可信证书 `fullchain.pem` 和对应的 `privkey.pem`。若证书工具使用指向其他目录的符号链接，应复制为上述目录中的普通文件，或确保容器挂载完整覆盖链接目标；只挂载 `/etc/letsencrypt/live/域名` 通常不能访问其指向的 `archive` 目录。然后运行：

```bash
bash scripts/cloud-deploy.sh tls
```

TLS 检查需要系统 OpenSSL 和可信 CA 根证书。部署脚本在替换服务前检查证书有效期、访问域名、可信链、私钥匹配和 Nginx 配置；启动后以实际域名进行本机 HTTPS 握手，并验证首页和认证接口，不跳过证书校验。此检查不代替从外部网络验证 DNS、云防火墙和公网连通性。

TLS 在现有前端容器内终止，后端使用安全 Cookie 和正确的转发协议。仅放行网站所需的公网端口，数据库、MinIO 控制台和后端继续保持回环访问。TLS 覆盖默认只绑定 `127.0.0.1:8443`，公网发布需显式设置上述绑定和端口。

TLS 覆盖会替换原有 HTTP 监听，因此切换后使用 `PUBLIC_ORIGIN` 中的 HTTPS 域名访问，原 `open-cloud-tunnel.ps1` 的 HTTP 地址不再适用。现有 TLS 配置只提供 HTTPS，不包含证书申请、自动续签或 HTTP 跳转；正式接入时应另行配置证书续签和成功后的 Nginx 检查与重载。

## 检查与更新

以下命令均在发布目录执行：

```bash
docker compose --env-file .env.cloud -f docker-compose.yml -f docker-compose.cloud.yml --profile app ps
docker compose --env-file .env.cloud -f docker-compose.yml -f docker-compose.cloud.yml --profile app stats --no-stream
docker compose --env-file .env.cloud -f docker-compose.yml -f docker-compose.cloud.yml --profile app logs --tail 100 backend
```

TLS 部署需追加 `-f docker-compose.cloud.tls.yml`。不要把完整 `docker compose config` 或包含密钥的日志公开分享。升级前备份 PostgreSQL、MinIO 数据和环境文件，备份至少另存一份到服务器之外；Flyway 迁移不代替备份。新版本使用相同项目名和卷，先检查健康再切换常用发布目录链接。不得使用 `docker compose down -v`，该命令会删除业务数据。

本次部署已将初始化后的 PostgreSQL、MinIO 和环境配置备份到当前电脑的 `.codex_tmp/cloud-deploy/initial-backup/`，目录仅当前 Windows 用户可访问，且不进入 Git 或部署包。已检查备份可以解压、数据库结构存在且验收账号已清理；未进行完整恢复演练。此为初始恢复点，后续新增数据仍需定期另存备份。
