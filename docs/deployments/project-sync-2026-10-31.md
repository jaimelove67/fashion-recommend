# 后端与采集器同步发布

## 结果

- 发布时间：2026-10-31 12:03:34（北京时间）。
- 发布编号：`backend-sync-20261031-01a18fad`。
- 后端与采集器代码版本：`3cb0f7638c32eeea33c06c12aa6eca61572f8f56`。
- 已保留同日发布的分享首页前端；前端镜像与入口文件 SHA-256 未变化。
- `/opt/zhiji/current` 已切换至 `/opt/zhiji/releases/backend-sync-20261031-01a18fad`，该目录包含当前源码和部署文件。
- V15 迁移成功。既有 V1–V14 迁移内容一致，现有账户、权限、资料、衣橱、推荐、反馈、模型配置等业务表记录数量在切换前后相同。
- PostgreSQL、MinIO 容器 ID 未变化；只重建后端、采集器和需要重新挂载配置的前端容器。
- 模型与存储凭据、加密密钥、HTTPS 和证书维护脚本保持不变，证书续期定时任务仍为 active。

## 镜像与制品

| 服务 | 镜像 ID |
| --- | --- |
| 前端，保留分享首页版本 | `sha256:a6685b4e195884795eaa32320b87ab319beae15080ae7f01dfe418779721f7d7` |
| 更新后的后端 | `sha256:2af90b19c9ab6bf406e2dbc1317136f4031acd956349663a9cebc3c408842886` |
| 更新后的采集器 | `sha256:75dd7f1bc8b6586e0600989efea43aa98561721e72b7e232bbef38415a9d2822` |

后端与采集器镜像标签均为对应服务的 `backend-sync-20261031-01a18fad`，并已更新各自的 `latest`。两个新镜像都带有 `org.opencontainers.image.revision` 标签。

- 后端 JAR SHA-256：`e4bceac97cc19ced79f96f9df6857f2c7156849c010950f0705b5b2fee2646b3`。
- 源码包：`fashion-cloud-deploy-20261031-113456-46239b67.tar.gz`，263 个文件。
- 源码包 SHA-256：`e9f516eaf2cea2bd1c47b447ac22bb3ee3361c2838725d54e370f5cffbe6a7d0`。
- 服务器发布操作、校验结果和制品目录：`/opt/zhiji/shared/backend-releases/backend-sync-20261031-01a18fad`。

源码包包含当前工作树中的分享首页与 HTTPS 部署文件，并非单纯导出 Git 提交；本次未创建或推送 Git 提交。新运行镜像基于服务器已有的同服务运行时层构建，覆盖已验证的新 JAR 或采集脚本；Java 依赖随 JAR 打包，采集器所需 Scrapling 版本仍为 0.4.15。

## 数据与部署目录

为避免切换发布目录后读取空目录，新增可配置的 `TREND_COLLECTOR_DATA_PATH`，本次固定为原有采集目录：

```text
/opt/zhiji/releases/20261016-130832/tools/trend-collector/data
```

后端继续通过 `TREND_IMPORT_PATH` 读取 `/opt/zhiji/shared/reviewed-trend-imports`。两个路径均已与更新前实际容器挂载核对。旧发布目录目前仍承载采集数据，不能当作纯源码目录删除。

打包脚本同时补齐了插画背景、受信技巧目录、证书续期脚本和 systemd 定时任务文件，避免未来切换源码目录时丢失这些运行所需文件。

## 备份与演练

- 服务器备份：`/opt/zhiji/shared/backups/backend-sync-20261031-01a18fad/initial-r1` 和 `cutover`。
- 包含 PostgreSQL 自定义格式转储、数据库角色定义、MinIO 数据卷及原部署配置；目录为 700，文件受限。
- 两份有效备份均已复制到本机忽略目录 `.codex_tmp/cloud-deploy/backend-sync-20261031-01a18fad/private-backup/`，限定当前 Windows 用户和 SYSTEM 访问，并与服务器 SHA-256 核对。
- `cutover` 在停止后端和采集器写入后制作，作为正式切换前的一致性恢复点。
- 在独立 PostgreSQL 容器和仅内部连通的 Docker 网络恢复备份，运行真实新版 JAR 完成 V15 迁移，确认既有记录数量不变。
- 仅在数据库副本创建临时验收账户，实测新版登录、个人衣橱读取及管理员配图预算接口；再用旧版 JAR 启动同一个 V15 副本，复测登录与读取，确认应用回滚兼容。
- 演练环境不能访问外网，禁用了自动模型任务，未调用真实模型。演练容器、网络和数据库卷已清理。
- `initial` 是对象存储备份方法调整前的未完成尝试，不是完整恢复点；恢复时使用 `initial-r1` 或 `cutover`。

## 验证

- 后端 `mvn -B -ntp verify`：333 项，失败 0、错误 0、跳过 5 项真实模型测试，生产 JAR 构建成功。
- 采集器离线测试：59/59 通过。
- PostgreSQL 副本恢复、V15 迁移、业务记录保留、新版鉴权接口、旧版兼容 V15、采集器导入检查均通过。
- 正式服务的 JAR、三个采集脚本、前端入口哈希均与待发布制品匹配。
- 正式数据库的 V14、V15 迁移状态均为成功，后端健康检查 UP。
- 公网 Chrome、Edge 与手机视口验证分享首页、知己入口、登录表单、CSRF、验证码、HTTPS；没有页面脚本错误。
- 新的公开技巧接口返回成功；未知配图资产返回 404，未登录访问管理员配图状态返回 401；CSRF Cookie 仍带 Secure。
- 未用真实账户提交线上登录、注册、上传或付费模型请求。外部平台实时采集质量及真实模型生成质量不属于本次验证结论。

## 应用回滚

旧后端与采集器镜像保留为各自的 `before-backend-sync-20261031-01a18fad`。确认没有后续发布依赖当前版本后，可以在服务器执行：

```bash
python3 /opt/zhiji/shared/backend-releases/backend-sync-20261031-01a18fad/deploy.py rollback
```

脚本恢复原运行镜像、原环境配置及旧发布目录，并重新挂载前端配置；不会删除 V15 表，也不会覆盖业务数据。旧后端在 V15 数据库上的兼容性已通过隔离演练验证。完整数据库恢复应另外制定恢复步骤，不能在有后续业务写入时直接覆盖生产库。
