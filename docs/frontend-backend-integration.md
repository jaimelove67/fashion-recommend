# 前后端联调记录

联调日期：2026-10-16。使用当前工作区代码，验证 Vue 页面通过同源代理调用真实 Spring Boot 服务和 PostgreSQL；接口响应没有用前端测试数据替换。

## 环境与数据边界

- 前端：`http://127.0.0.1:5173`，Vite 代理后端 `http://127.0.0.1:8088`。
- 后端：Java 17、Spring Boot 3.4.2，数据库迁移 V1–V13 在 PostgreSQL 16.14 上实际执行成功。
- 图片存储：真实 MinIO，S3 端口 `127.0.0.1:9200`，控制台 `127.0.0.1:9201`，独立数据目录 `output/integration/minio-data`。本机 Docker 启动受运行时目录访问错误影响，本次采用本地进程完成联调，不将其计为 Docker 部署验证。MinIO 从官方源码标签 `RELEASE.2025-04-22T22-12-26Z` 以 Go 1.24.2 构建，来源及校验信息记录在 `output/integration/minio-build/build-metadata.json`；`live`、`ready` 健康检查均返回 200。
- 数据库：本次新建 `127.0.0.1:5546/fashion_integration`，数据目录 `output/integration/postgres-data`。没有重置原有数据库或账号。
- 用户已明确授权仅在该隔离库创建联调管理员和临时普通账号，以及衣橱、推荐等测试数据。管理员权限只授予该隔离库的测试账号。
- 五条导入内容的 ID 为 `weibo:integration-1`、`douyin:integration-2`、`xiaohongshu:integration-3`、`weibo:integration-4`、`xiaohongshu:integration-5`。标题和摘要均标明“联调样例”；分类是人为构造的测试输入，配图使用项目已有素材，不能作为真实原帖分析或当日平台采集成果。
- 百炼推荐、识图和生图开关关闭；推荐验证规则降级及 `generationAudit.fallbackReason=llm-disabled`。杭州天气为明确标识的 `configured-demo`，不将其计为实时天气验证。
- 浏览器测试中禁止访问外部图片或原帖；所有 `/api` 请求实际到达后端。穿搭技巧仍使用已整理并附来源的本地内容，不是后台持续自动抓取/归纳服务。

## 本次补齐的连接

### 管理员图片复核

原后端已提供复核接口，但管理页面没有入口，采集到的新内容只能停留在待复核状态。本次新增“穿搭图片复核”页面：

1. 从 `GET /api/v1/admin/trends/review-queue?limit=20` 读取当前候选。
2. 展示文字、标签、原帖链接、作者、日期、封面和全部配图，去掉相同 URL 的重复展示。
3. 通过需要全部图片加载成功、填写说明，并明确确认已逐一检查。图片失败的内容仍可填写原因后拒绝。
4. 向 `POST /api/v1/admin/trends/{id}/content-review` 原样提交当前 `fingerprint`，携带现有 CSRF 请求头。
5. 遇到 409 内容变更，清空旧确认并重新加载候选；成功后刷新队列、鉴赏列表和审计日志。退出会话后丢弃旧响应。

后端继续负责权限、版本、隐藏状态和内容规则，前端不能通过本地标签自行批准内容。

### 开发环境图片代理

新浪图片展示使用 `/trend-images/...` 同源路径。生产 Nginx 已配置固定 CDN 代理，本次在 Vite 开发服务补齐对应路径，避免返回 SPA HTML 导致图片失效。代理仅允许 `wx1–wx4.sinaimg.cn` HTTPS、既有尺寸和图片文件路径；出站仅构造固定 Host、User-Agent、Accept，不转发浏览器 Cookie、认证信息及来源头，不接受任意目标地址。非法路径在本地拒绝，启用 TLS 校验，阻止重定向和非 JPG/PNG/WebP 响应，隐藏 Set-Cookie。

代理测试连接本机上游服务检验路径、实际图片字节及头部处理，未将其计为真实新浪 CDN 可达性验证。

## 验证结果

| 检查 | 结果与证据 |
| --- | --- |
| 真实浏览器服务联调 | 4 项通过。游客 401 / CSRF 403 / 输入验证 400；管理员复核至每日 2/2/1、3D 选择、分析弹窗、技巧来源及衣橱跳转；新普通账号衣橱/规则推荐/收藏/评分/刷新持久化/退出；PNG 上传与账号隔离。最终日志 `output/integration/final-e2e.log`。 |
| 图片复核前端回归 | 3 项通过，接口和图片在测试内隔离；覆盖全部图片门槛、版本变化 409 后重新确认、手机端加载失败后拒绝。与真实联调串行运行，最终合计 7 项通过。 |
| 前端单元测试 | 74 项通过，包括 5 项 API/图片代理专项测试；上游图片测试只连接本机服务。 |
| 后端相关回归 | 32 项通过：复核接口、每日列表、每日选择、内容仓储、公开趋势接口。测试库为 H2；另由真实 PostgreSQL 联调验证迁移与业务写入。日志 `output/integration/backend-tests.log`。 |
| 构建 | 前端构建与后端打包均通过。 |
| 隔离环境负例 | 让实际联调测试指向没有五条标识的本机临时服务，测试在预检时拒绝执行；账号注册及业务请求为 0。结果 `output/integration/isolation-guard-result.json`。 |

实际上传的 PNG 经 Spring 写入 MinIO，再通过当前用户图片接口读回，68 字节与输入逐字节一致；响应为 `image/png` 且带 `nosniff`，浏览器成功解码。刷新后图片仍可读取；另一普通账号访问返回 404，退出后返回 401。

完成前的反向检查发现测试原先只校验本机地址，存在误连原服务后创建测试数据的风险；已修复为整个测试组强制校验五条隔离样例，并通过上述负例验证。未发现其他有证据的本次新增缺陷。

未验证真实社交平台当日抓取、视频读取、外部新浪 CDN 连通性、实时天气、付费推荐/识图/生图、Anywear 外部试衣或 Docker 完整部署。联调结果不代表这些外部服务已经接通。

## 测试入口

- `frontend/e2e/integration-live.spec.js`：显式设置 `RUN_LOCAL_INTEGRATION=true` 后运行的真实服务链路。所有用例运行前，先用专用管理员会话读取公开列表和复核队列，验证五条精确样例 ID、标题、作者、人工构造摘要和同源图片/来源路径；标识缺失时整个测试组停止，不创建账号或写入业务数据。管理员复核场景要求上述五条联调样例处于待复核状态；重复运行时只能恢复隔离库中的这五条测试记录，不重置其他内容。
- `frontend/e2e/admin-trend-review.spec.js`：接口和图片隔离的前端回归，验证通过门槛、409 版本变化、手机端图片失败后拒绝。
- `frontend/tests/vite-proxy.test.mjs`：开发代理的同源、CSRF 与图片代理边界检查。

示例命令（仅连接上面的隔离联调服务）：

```powershell
$env:RUN_LOCAL_INTEGRATION = 'true'
$env:E2E_BASE_URL = 'http://127.0.0.1:5173'
$env:INTEGRATION_ADMIN_USERNAME = '<隔离库测试管理员>'
$env:INTEGRATION_ADMIN_PASSWORD = '<该测试账号密码>'
Set-Location frontend
node node_modules/@playwright/test/cli.js test e2e/integration-live.spec.js
```

运行日志及页面截图保存在 `output/integration`、`output/integration-live` 和 `output/integration-ui`。这些目录不纳入版本控制。
