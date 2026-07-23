# 知己：基于大模型的智能穿搭推荐系统

“知己”是一个面向个人衣橱的智能穿搭推荐毕业设计项目。用户可以录入或上传衣物，填写城市、场合和风格要求，系统结合天气与衣橱数据生成可解释的搭配方案，并支持保存、反馈和历史查看。

当前项目的验收边界是“账号注册/登录 -> 衣橱录入 -> 场景输入 -> 推荐生成 -> 保存反馈”的可运行闭环，属于毕业设计原型。账号系统采用本地用户名、密码和服务端会话，不包含第三方统一登录、找回密码、在线交易、虚拟试衣或后台管理。

## 技术栈

- 前端：Vue 3、Vite、Lucide Vue，入口位于 frontend/。
- 后端：Java 17、Spring Boot 3.4.2、Spring Web、Spring Security、JDBC、Validation、Flyway、Actuator，入口位于 backend/。
- 数据与存储：PostgreSQL 16（使用 pgvector 镜像）、MinIO 私有对象存储、Caffeine 进程内缓存。
- 智能能力：阿里云百炼兼容 OpenAI Chat Completions 协议；推荐模型和视觉识别默认可关闭。
- 本地编排：Docker Compose，包含 PostgreSQL、MinIO，以及可选的前后端应用服务。

## 环境要求

完整 Docker 启动需要 Docker Desktop。直接运行源码还需要：

- Java 17+
- Maven 3.9+
- Node.js 20+ 和 npm

## 启动项目

### 方式一：Docker 完整启动（推荐演示）

在项目根目录执行：

~~~powershell
Copy-Item .env.example .env
docker compose --profile app up --build -d
docker compose ps
~~~

打开前端：<http://localhost:8090>

相关地址：

- 后端 API：<http://localhost:8088>
- MinIO 控制台：<http://localhost:9001>
- 后端健康检查：<http://localhost:8088/actuator/health>
- Prometheus 指标：<http://localhost:8088/actuator/prometheus>

停止服务：

~~~powershell
docker compose down
~~~

`docker compose down -v` 会同时删除 PostgreSQL 和 MinIO 的本地数据，包括账号、衣橱、推荐记录和上传图片，只应在确认不再需要这些数据时使用。

### 方式二：本地运行前后端

先启动 PostgreSQL 和 MinIO：

~~~powershell
Copy-Item .env.example .env
docker compose up -d
~~~

在第一个终端启动后端：

~~~powershell
Set-Location backend
mvn spring-boot:run
~~~

在第二个终端安装依赖并启动前端：

~~~powershell
Set-Location frontend
npm ci
npm run dev
~~~

打开前端：<http://localhost:5173>。Vite 会把 /api 和 /actuator 请求代理到 http://localhost:8080。

注意：.env 会被 Docker Compose 自动读取，但不会被 mvn spring-boot:run 自动读取。直接运行后端时，数据库和 MinIO 默认连接配置已经与 Compose 的主机端口匹配；如需使用百炼 API，需要在启动后端的终端显式设置环境变量，例如：

~~~powershell
$env:DASHSCOPE_API_KEY = "你的百炼 API Key"
mvn spring-boot:run
~~~

## 账号与会话

正常启动不会创建默认账号。首次打开页面后切换到“注册”，创建账号并自动登录；以后使用同一用户名和密码登录。用户名为 3-32 位小写字母、数字、下划线或连字符，密码至少 8 个字符且 UTF-8 编码不超过 72 字节。注册可通过 `AUTH_REGISTRATION_ENABLED=false` 关闭。

密码使用 BCrypt 存储。Spring Security 将认证状态保存在服务端 Session 中，浏览器只接收 HttpOnly、SameSite=Lax 的 `JSESSIONID` Cookie，前端不保存可伪造的用户 ID 或认证 Token。个人接口从认证上下文取得用户名；匿名访问返回 401，退出登录会使当前 Session 失效并清除会话数据。

前端在同源请求中携带 Cookie，并从 `GET /api/v1/auth/csrf` 取得 CSRF Token；所有 POST、PUT、DELETE 请求发送服务端返回的 `X-XSRF-TOKEN` 请求头。令牌过期导致 403 时前端只刷新一次令牌并重试。生产部署应启用 HTTPS，并设置 `SESSION_COOKIE_SECURE=true`。

## 准备答辩演示数据

完整应用启动且 Flyway 迁移完成后，在项目根目录执行：

~~~powershell
.\scripts\seed-demo-data.ps1
~~~

脚本在单个事务中幂等重置 `demo-user`，准备 8 件衣物、3 条推荐、1 条收藏反馈、1 份风格档案和 1 条识别失败后人工修正记录。登录凭据仅用于本地演示：

~~~text
用户名：demo-user
密码：demo-password-2026
~~~

每次执行都会重置该演示账号的密码和业务数据，不影响其他账号。预置推荐标记为 `development-rule-v1`，不会伪装成真实模型结果；生产环境不应运行此脚本。

## 核心演示流程

1. 注册账号或登录已有账号。
2. 进入“衣橱”，手动添加或上传衣物。建议准备上装、下装、鞋履各一件。
3. 上传图片后，系统把图片写入 MinIO 私有桶，并通过需要登录的后端图片代理展示。
4. “使用 AI 自动识别”默认不勾选；此时需填写名称、类别和颜色。只有本次上传明确勾选后，后端才允许进入识别流程，实际外部调用还要求视觉开关和密钥均已配置。
5. 进入“推荐”，填写城市、场合和可选的风格要求，生成搭配。
6. 查看天气、推荐单品和推荐理由，保存方案并提交满意度反馈。
7. 进入“历史”查看已生成记录；“风潮”优先展示已配置的授权 JSON 趋势源，不可用时显示明确标注的开发样本。

推荐至少需要两件可组合的、已完善的不同类别衣物。衣物为空、只剩待人工确认记录或类别无法组合时，接口会返回明确错误，不会伪造推荐结果。

## 当前能力边界

- 未配置 DASHSCOPE_API_KEY 时，推荐使用可测试的开发期规则引擎 development-rule-v1。
- 配置百炼 Key 后，推荐会尝试调用 qwen-plus；请求超时、响应不合规或模型返回了非衣橱单品时，会回退到规则引擎。
- BAILIAN_VISION_ENABLED 默认为 false。即使服务端已启用，仍需用户在每次上传时明确勾选 AI 识别；未同意、未启用或识别失败时，系统不会调用或不会采纳视觉模型结果，并要求人工确认不完整信息。
- “风潮”仅在 `TREND_JSON_URL` 返回符合严格契约的授权数据时标记 `demoMode=false`；未配置、请求失败或数据不合规时回退到明确标注的三条开发样本，不代表实时平台热度。
- 个人数据接口要求 Spring Security Session 认证，服务端从认证上下文取得用户身份；客户端自定义用户请求头不会改变身份。
- 衣橱、推荐历史、反馈和个人风格档案均保存在 PostgreSQL 中。

## 数据库迁移

数据库结构由 Flyway 管理，运行时 SQL 初始化已关闭。当前基线迁移为 `backend/src/main/resources/db/migration/V1__baseline_schema.sql`：新数据库直接执行 V1；已有表但没有 Flyway 历史的旧数据库会先以版本 0 建立基线，再执行 V1。V1 使用兼容旧结构的建表、补列和建索引语句，迁移测试覆盖了保留既有衣物数据和重复启动不重复执行。

`baseline-on-migrate=true` 只用于接管本项目旧数据库，`clean-disabled=true` 禁止 Flyway 清库。已执行的迁移文件不应修改；后续结构变化应新增 V2、V3 等迁移。迁移不能替代备份，升级包含重要数据的环境前仍应先备份 PostgreSQL。

这些边界的设计理由、安全失败方式和生产化替换方案见 [开发期边界与生产化路径](docs/development-boundaries.md)。

## 大模型证明材料

项目保留了一次脱敏的真实百炼 qwen-plus 推荐调用，包含 provider call ID、token usage、结构化结果和衣橱 ID 校验；同时给出推荐调用流程图及 prompt 约束说明：

- [百炼大模型接入与调用证据](docs/llm-integration-evidence.md)
- [真实调用脱敏 JSON](docs/llm-evidence/bailian-recommendation-2026-07-14.json)

证明材料不包含 DASHSCOPE_API_KEY、Authorization 头或完整环境变量。预置演示数据仍明确标记为 development-rule-v1，与真实调用证据分开保存。

## 环境变量

.env.example 是 Compose 配置的完整示例。复制为 .env 后按需修改，尤其不要把真实密钥和生产密码提交到仓库。

基础设施变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| POSTGRES_DB | fashion_recommendation | 数据库名 |
| POSTGRES_USER / POSTGRES_PASSWORD | fashion / fashion_2404 | 数据库账号 |
| POSTGRES_PORT | 5433 | PostgreSQL 主机端口 |
| MINIO_PORT / MINIO_CONSOLE_PORT | 9000 / 9001 | MinIO API 和控制台端口 |
| MINIO_ROOT_USER / MINIO_ROOT_PASSWORD | fashion_minio_admin / fashion_2404 | MinIO 管理账号 |
| MINIO_BUCKET | garments-private | 私有图片桶名称 |
| MINIO_MAX_FILE_SIZE | 10485760 | 最大图片大小，单位为字节 |

应用和模型变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| BACKEND_PORT / FRONTEND_PORT | 8088 / 8090 | Docker 应用的主机端口 |
| AUTH_REGISTRATION_ENABLED | true | 是否允许创建本地账号 |
| SESSION_TIMEOUT | 30m | 服务端 Session 有效期 |
| SESSION_COOKIE_SECURE | false | HTTPS 部署时应设置为 true |
| DASHSCOPE_API_KEY | 空 | 百炼 API Key；为空时使用规则推荐 |
| BAILIAN_MODEL | qwen-plus | 文本推荐模型 |
| BAILIAN_VISION_ENABLED | false | 是否启用图片视觉识别 |
| BAILIAN_VISION_MODEL | qwen-vl-plus | 视觉识别模型 |
| BAILIAN_ENDPOINT | 百炼兼容接口 | 模型请求地址 |
| BAILIAN_CONNECT_TIMEOUT / BAILIAN_READ_TIMEOUT | 3s / 8s | 模型连接和读取超时 |

授权趋势源变量：`TREND_JSON_URL`、`TREND_PLATFORM`、`TREND_CONNECT_TIMEOUT`、`TREND_READ_TIMEOUT`、`TREND_CACHE_TTL`。`TREND_JSON_URL` 为空时使用开发样本。数据源必须返回只含 `items` 的 JSON 对象；每条记录必须且只能包含 `id`、`platform`、`title`、`topicTags`、`heatScore`、`publishedAt`、`sourceUrl`、`imageUrl`。条目数为 1-50，热度为 0-100 整数，时间为 ISO-8601，链接为 HTTP(S)，`imageUrl` 可为 `null`。任一约束失败时整批拒绝并降级，不会把部分脏数据标记为实时趋势。

天气变量：WEATHER_PRIMARY_BASE_URL、WEATHER_FALLBACK_GEOCODING_BASE_URL、WEATHER_FALLBACK_FORECAST_BASE_URL、WEATHER_CONNECT_TIMEOUT、WEATHER_READ_TIMEOUT、WEATHER_CACHE_TTL、WEATHER_CACHE_MAX_SIZE。默认使用 wttr.in，失败后回退到 Open-Meteo，并在进程内缓存天气结果。

直接运行后端时还可以使用 SERVER_PORT、CORS_ALLOWED_ORIGINS、SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD、MINIO_ENDPOINT 覆盖 application.yml 中的默认配置。

## 测试与构建

后端单元测试和控制器测试：

~~~powershell
Set-Location backend
mvn test
~~~

前端生产构建：

~~~powershell
Set-Location frontend
npm ci
npm run build
~~~

Compose 文件校验：

~~~powershell
docker compose config -q
~~~

前端 E2E 冒烟验证：

~~~powershell
docker compose --profile app up --build -d
Set-Location frontend
npm ci
npm run test:e2e
~~~

E2E 默认使用系统已安装的 Google Chrome，不需要额外下载浏览器。它会使用独立测试用户，通过真实页面完成“打开页面 -> 添加衣物 -> 生成推荐 -> 收藏推荐”，并从后端历史接口再次确认 saved=true。失败时的 trace、截图、视频和 HTML 报告位于 output/playwright/，不会进入 Git。

可通过 E2E_BASE_URL、E2E_API_URL、E2E_CITY 和 E2E_BROWSER_CHANNEL 覆盖默认的前端地址、后端地址、测试城市和浏览器通道。需要在可见浏览器中演示时运行 npm run test:e2e:headed。

提交前建议再检查：

~~~powershell
git diff --check
git status --short
~~~

## 常见问题

### 页面能打开，但推荐失败

请先确认衣橱中至少有两件已完善且类别不同的衣物。推荐还需要读取城市天气；如果网络无法访问天气服务，请检查 WEATHER_* 地址或稍后重试。

### 图片上传失败

确认 MinIO 和 minio-init 已成功运行，并使用 JPG、PNG 或 WEBP 图片，大小不超过 MINIO_MAX_FILE_SIZE。minio-init 显示 Exited (0) 是一次性初始化成功，不是错误。

### 没有百炼 API Key 是否无法使用

不会。推荐会使用开发期规则引擎；只有需要真实文本推荐或视觉识别时才需要配置 DASHSCOPE_API_KEY。视觉识别另外受 BAILIAN_VISION_ENABLED 控制。

### 端口被占用

Docker 模式可修改 .env 中的 POSTGRES_PORT、MINIO_PORT、BACKEND_PORT 或 FRONTEND_PORT 后重新启动。源码模式下，后端默认使用 8080，前端默认使用 5173，代理地址见 frontend/vite.config.js。

### 如何清空本地演示数据

~~~powershell
docker compose down -v
docker compose --profile app up --build -d
~~~

这会删除所有本地数据库和对象存储数据，包括账号、衣橱、推荐记录和上传图片，请确认不再需要当前演示数据后再执行。
