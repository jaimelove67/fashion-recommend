# 知己：基于大模型的智能穿搭推荐系统

“知己”是一个面向个人衣橱的智能穿搭推荐毕业设计项目。用户可以录入或上传衣物，填写城市、场合和风格要求，系统结合天气与衣橱数据生成可解释的搭配方案，并支持保存、反馈和历史查看。

当前项目的验收边界是“账号注册/登录 -> 衣橱录入 -> 场景输入 -> 推荐生成 -> 保存反馈”的可运行闭环，另提供受 `ROLE_ADMIN` 保护的后台运营工作台（概览、账号治理、反馈处理、模型配置和操作审计）。项目属于毕业设计原型，账号系统采用本地用户名、密码和服务端会话，暂不包含第三方统一登录、找回密码或在线交易。趋势页另提供 Anywear 实时试衣与配套跨站浮窗，将喜欢的试穿单品保存到账号的“喜欢穿搭”；试穿生成依赖外部服务。

## 技术栈

- 前端：Vue 3、Vite、Lucide Vue；运营总览使用本地 lieflat-charts 的 Lupi Basics 语法实现原生 SVG 图表，入口位于 frontend/。
- 后端：Java 17、Spring Boot 3.4.2、Spring Web、Spring Security、JDBC、Validation、Flyway、Actuator，入口位于 backend/。
- 数据与存储：PostgreSQL 16、MinIO 私有对象存储、Caffeine 进程内缓存。
- 智能能力：阿里云百炼及 OpenAI Chat Completions 兼容调用；管理员可按识图、推荐和每日生图分别设置厂商、模型和 API Key，密钥使用 AES-GCM 加密存储。
- 本地编排：Docker Compose，包含 PostgreSQL、MinIO，以及可选的前后端应用服务。

## 环境要求

完整 Docker 启动需要 Docker Desktop。直接运行源码还需要：

- Java 17+
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0` 和 npm（Vite 8 的运行时要求）

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

安全策略保持默认不对外开放：Actuator 仅暴露 `health` 与 `info` 两个端点，不提供 Prometheus 或 metrics 指标端点。

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

前端在同源请求中携带 Cookie，并从 `GET /api/v1/auth/csrf` 取得 CSRF Token；所有 POST、PUT、DELETE 请求发送服务端返回的 `X-XSRF-TOKEN` 请求头。令牌过期导致 403 时前端只刷新一次令牌并重试。后端默认使用可兼容本地 HTTP 的 Session Cookie，源码启动后刷新页面仍会保留登录状态。生产部署应启用 HTTPS，并显式设置 `SESSION_COOKIE_SECURE=true`。

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

管理员演示账号仅由本地 demo seed 创建，不应带入生产环境：

~~~text
用户名：demo-admin
密码：demo-password-2026
角色：ROLE_ADMIN
~~~

每次执行都会重置该演示账号的密码和业务数据，不影响其他账号。预置推荐标记为 `development-rule-v1`，不会伪装成真实模型结果；生产环境不应运行此脚本。

## 核心演示流程

1. 注册账号或登录已有账号。
2. 进入“衣橱”，手动添加或上传衣物。建议准备上装、下装、鞋履各一件。
3. 上传图片后，系统把图片写入 MinIO 私有桶，并通过需要登录的后端图片代理展示。
4. “使用 AI 自动识别”默认不勾选；此时需填写名称、类别和颜色。只有本次上传明确勾选后，后端才允许进入识别流程，实际外部调用还要求视觉开关和密钥均已配置。
5. 进入“推荐”，填写城市、场合和可选的风格要求，生成搭配。
6. 查看天气、推荐单品和推荐理由，保存方案并提交满意度反馈。
7. 进入“历史”查看已生成记录；“风潮”优先展示已接入来源的真实穿搭内容（授权 JSON 源、公开网页源，以及抖音/微博采集导入内容）。没有任何可用真实内容时，默认显示空状态与来源状态说明；仅在显式开启 `TREND_CONFIGURED_DEMO_ENABLED=true` 的演示环境中，才显示带 `demoMode=true`、`primarySource=configured-demo` 标识的固定本地样本，并明确注明非实时。
8. 管理员进入独立的“管理工作台”管理账号、反馈、模型配置和操作日志；符合来源契约的趋势内容采集后直接展示。

推荐需要已完善的上装与下装，或连体装与鞋履等配套单品，共 2–4 件且同一类别最多一件。鞋履与配饰不能单独组成完整搭配。衣物为空、只剩待人工确认记录或类别无法组合时，接口会返回明确错误，不会伪造推荐结果。

## 个人形象分析与推荐

个人信息页的身高、体重、照片、形象观察和穿衣建议保存在后端个人档案中。照片写入 MinIO 私有桶，由登录后的 `GET /api/v1/me/style-profile/photo` 按当前用户代理读取；不再使用浏览器中的共享演示数据作为分析结果。

1. 编辑基础资料，通过 `POST /api/v1/me/style-profile/refresh` 保存身高、体重、模特性别和偏好。
2. 选择照片，通过 `POST /api/v1/me/style-profile/photo` 上传；也可跳过照片，手动填写形象特征和建议。
3. 用户明确同意本次 AI 分析后，调用 `POST /api/v1/me/style-profile/analyze`。个人分析复用“视觉识别（衣物与个人形象）”的模型配置，要求 `BAILIAN_VISION_ENABLED=true` 且已配置可用密钥，默认模型为 qwen-vl-plus。读取超时由 `BAILIAN_PROFILE_ANALYSIS_READ_TIMEOUT` 控制，默认 30 秒。

本地模型启用配置：在 `.env` 中显式设置 `BAILIAN_ENABLED=true`、`BAILIAN_VISION_ENABLED=true`、`BAILIAN_IMAGE_ENABLED=true` 并配置 `DASHSCOPE_API_KEY`；修改后需重新创建后端容器。`.env.example` 已包含这三个启用开关。视觉识别仍需用户明确同意，项目启动不会自动发送个人照片或生成效果图。

可选真实模型检查：`AiModelLiveSmokeTest` 默认跳过，不会在普通测试中产生模型调用。将上述配置导出为进程环境变量，设置 `MODEL_LIVE_SMOKE=true`，并将 `MODEL_SMOKE_PHOTO` 指向包含可见面部和衣物的合成人物 PNG 绝对路径，再在 `backend` 中运行 `mvn -Dtest=AiModelLiveSmokeTest test`。检查使用真实模型客户端和虚构衣物，覆盖个人分析、衣物识别、推荐与效果图生成；不创建用户、不访问数据库、不读取用户照片。生图使用项目模特素材，检查会产生实际模型调用费用。
4. 模型只描述可见特征；照片无法确认的观察留空。合法结果以 `analysisSource=MODEL` 保存，并记录实际响应模型和分析时间。未启用、缺少密钥、超时或结果无效时，界面明确显示失败，并保留已保存资料供重试或人工填写。
5. 人工编辑通过 `POST /api/v1/me/style-profile/analysis` 保存，以 `analysisSource=MANUAL` 标识。修正模型观察时，页面清空尚未重新确认的衍生建议；可再次分析或手动填写剪裁、颜色等建议。
6. 之后生成的穿搭推荐会读取当前档案，把身高体重和有效分析加入模型上下文；规则降级也按风格、颜色、剪裁和单品建议匹配衣橱元数据。衣橱未记录真实尺寸时，系统不会声称已验证衣物尺码或合身程度。

上传照片时会询问是否使用本人形象生成每日穿搭效果图，默认选择系统提供的模特。选择“使用我的照片”后，通过 `POST /api/v1/me/style-profile/outfit-model` 单独保存 `usePersonalPhotoForOutfit=true`；该授权独立于本次 AI 形象分析的授权，个人档案中可随时切回默认模特。生图只读取当前登录用户的私有照片，不接受前端指定照片地址或其他用户的对象键。更换照片后此设置自动重置为默认模特，需要对新照片重新选择。旧账号和未选择的账号均不会把个人照片用于生图。

穿搭效果图的第一张参考图决定人物形象，后续参考图仅用于服饰；保持自然的头身比例、写实风格和纯色浅灰背景。返回值 `modelSource` 标识 `DEFAULT` 或 `PERSONAL`，页面相应显示默认模特或本人形象。图片缓存区分人物来源和照片版本；用户切换来源、更换照片或退出登录后，迟到的生图结果不会恢复旧人物图像。

更换照片，或修改身高体重、模特性别、风格、颜色、场合偏好后，旧分析标记为 `stale=true`。旧分析及其衍生建议暂不进入模型输入和规则评分，直到重新分析或人工确认。资料版本检查阻止较慢的分析请求覆盖后续修改，退出账号后返回的前端请求也不会恢复旧账号资料。

## Anywear 实时试衣

在趋势页“趋势单品解析”中选择单品并点击“实时试穿”。同意后允许 Anywear 使用摄像头，看到上身效果后可点击“喜欢，保存到项目”。当前趋势图片为款式示意图，界面及收藏标明“非原帖商品图”；支持有图片的上装、裤装、外套和连体装，鞋履及配饰暂不支持。

跨站使用时，从趋势页下载并解压配套扩展，在 Chrome / Edge 扩展管理页启用开发者模式并加载。在项目网页打开工具栏扩展，点击“连接此项目网站”并授权，再点击趋势页“开启跨站试穿”。购物网站首次使用时逐站授权浮窗，拖入商品图片，同意并允许摄像头，满意后点击喜欢。项目标签页需保持打开；收藏原图、类别与来源链接持久保存到导航的“喜欢穿搭”，可再次试穿或返回原商品。

使用本机 localhost、127.0.0.1 或 HTTPS 地址访问项目。用户同意前不加载第三方试衣页面，扩展不默认注入所有网站；图片字节传给独立的 Anywear 页面，项目登录凭证只用于项目自身请求。试穿效果不用于判定尺码；服务配额、排队、计费与数据处理由 Anywear / Decart 管理。

接入方式、外部服务边界及验证步骤见 [Anywear 实时试衣接入说明](docs/anywear-live-try-on.md)。

## 当前能力边界

- 推荐大模型是首选引擎：`BAILIAN_ENABLED` 默认为 `true`，且配置 `DASHSCOPE_API_KEY` 后优先调用 qwen-plus。未配置 Key、请求超时、响应不合规、结果越界或模型返回了非衣橱单品时，系统才降级到规则引擎；降级记录会保留稳定的 `fallback_reason`（`missing-api-key`、`request-failed`、`response-invalid`、`result-invalid`、`duplicate-item-ids`、`foreign-item-ids`、`same-category`）。如需离线测试或避免模型费用，必须显式设置 `BAILIAN_ENABLED=false`，此时原因是 `llm-disabled`。
- 每日推荐主区域按参考效果展示为“中央全身模特 + 四角衣橱单品卡”，下方固定解释天气适配、场合适配、风格方向、衣橱依据。页面会按当前衣橱 id 重新校验推荐快照，并排除 `NEEDS_MANUAL_REVIEW` 衣物；失效图片不使用通用时装图回退。模特性别只取个人档案中的 `gender`，每日模特图通过 `/api/v1/me/recommendations/{id}/visual` 调用阿里云万相图像编辑接口生成。发送前将模特原型及本地/Base64 衣橱参考图转换为 RGB JPEG，透明背景铺白、小图等比例放大至宽高均至少 240 像素；损坏、无法解码或超限的衣橱图片不发送，单品描述仍保留，原始图片不修改。未配置或失败时保留画板和已确认单品并明确显示未生成状态，不把原型或静态图冒充当日生图。
- 每次生成都会持久化审计元数据：合法 LLM 结果保存真实 provider 元数据（provider call ID、模型名、prompt 版本与三类 token），规则降级只保存稳定的枚举式 fallback 原因，不保存异常原文。API 通过嵌套 `generationAudit` 返回这些字段，`engine` 仍保留在顶层。旧历史与降级路径保持为空，不伪造模型元数据。
- BAILIAN_VISION_ENABLED 默认为 false。即使服务端已启用，仍需用户在每次上传时明确勾选 AI 识别；未同意、未启用或识别失败时，系统不会调用或不会采纳视觉模型结果，并要求人工确认不完整信息。
- “风潮”仅展示面向日常穿搭人群的图文帖子，排除视频、纯文字、时装周、秀场、红毯、明星造型和杂志大片。平台不设硬性名单，默认由 Scrapling 搜索微博、抖音和小红书，其他平台可通过通用来源接口接入。每天北京时间12点自动采集，结果限制在近24小时范围；来源访问失败保留上次真实结果并显示失败状态，没有符合条件的真实内容时显示空状态。本地环境关闭预置样本回退。画廊要求已审核的全身照片，自动采集器不会伪造审核标记；合格但未审核的图文仍可在内容列表展示。粉丝数量及互动计数未取得时保留为未知。
- 登录后的全局天气条会优先请求浏览器当前位置；用户拒绝定位时可输入城市。天气由后端调用 Open-Meteo/wttr.in 并标注数据来源，当前位置坐标只保存在浏览器本地，不写入业务数据库。
- 个人数据接口要求 Spring Security Session 认证，服务端从认证上下文取得用户身份；客户端自定义用户请求头不会改变身份。
- 衣橱、推荐历史、反馈和个人风格档案均保存在 PostgreSQL 中。推荐历史接口按页返回，页码从 0 开始，默认每页 20 条、最大 50 条；`page * size` 不得超过 1,000,000，越界返回 400，限制深分页查询成本。到达此边界时 `hasNext=false`，`totalElements` 仍为该用户的实际记录总数。图片删除在数据库事务内写入清理任务，由后台调度器异步重试 MinIO 清理，避免对象存储瞬时故障阻塞业务删除。
- 管理端接口由服务端 `ROLE_ADMIN` 强制保护：`/api/v1/admin/overview` 返回真实聚合指标，`/api/v1/admin/users` 与 `/api/v1/admin/users/{username}/status` 支持账号查询和启停治理，`/api/v1/admin/feedback` 与 `/api/v1/admin/feedback/{recommendationId}/status` 支持反馈筛选和处理，`/api/v1/admin/audit-logs` 支持管理操作审计查询。运营总览将推荐引擎结果和账号状态分别可视化，图表只使用当前数据库聚合，不虚构历史趋势。后台只返回脱敏元数据、反馈处理状态和推荐运行统计，不返回密码哈希、私有图片或推荐正文；普通用户、伪造 `X-User-Id` 或未认证请求均不能进入管理分支，当前管理员不能停用自己。已停用账号的后续已认证请求返回 401，并清除认证信息和已有登录会话；重新启用后可重新登录。
- 管理员可通过 `/api/v1/admin/ai-models` 配置三种模型能力及启停状态：识图、穿搭推荐、每日搭配图生成。前两类支持百炼/OpenAI 兼容接口；每日生图使用百炼专用接口，默认 `wan2.6-image` 采用同步调用，不发送 `X-DashScope-Async` 请求头；若服务返回任务 ID，仍兼容任务轮询。API Key 单独以 AES-GCM 密文写入数据库；空白输入保留已有密钥，显式操作才清除密钥覆盖，恢复环境配置会删除该能力的整条自定义记录。接口与审计日志不返回密钥或密文；密钥保存需要部署端提供 `AI_SETTINGS_ENCRYPTION_KEY`。
- 管理员可通过受 `ROLE_ADMIN` 保护的 `/api/v1/admin/trends/refresh` 手动刷新趋势来源；趋势审核接口已移除。

## 数据库迁移

数据库结构由 Flyway 管理，运行时 SQL 初始化已关闭。V1 是兼容旧结构的基线迁移，V2 增加图片清理任务表，V3 为推荐增加审计元数据列，V4 增加管理员治理，V5 增加趋势快照，V6 曾增加趋势审核状态及审计字段，V7 增加个人档案 `gender` 字段，V8 增加管理员 AI 模型配置表，V9 增加个人身高体重、私有照片引用、结构化形象分析、来源、时间和资料版本：新数据库依次执行 V1 至 V9；已有表但没有 Flyway 历史的旧数据库会先以版本 0 建立基线，再依次执行。V6 审核列作为已执行的历史结构保留，当前运行时不读取它们；本次刷新会清除旧趋势内容和互动快照。迁移测试覆盖既有衣物/推荐数据保留、V2–V9 升级和重复启动不重复执行。

`baseline-on-migrate=true` 只用于接管本项目旧数据库，`clean-disabled=true` 禁止 Flyway 清库。已执行的迁移文件不应修改；后续结构变化应继续新增版本迁移。迁移不能替代备份，升级包含重要数据的环境前仍应先备份 PostgreSQL。

这些边界的设计理由、安全失败方式和生产化替换方案见 [开发期边界与生产化路径](docs/development-boundaries.md)。

趋势内容的来源范围、编辑订阅源配置、抖音/微博的内容导入步骤和博主分层见 [趋势内容来源与接入说明](docs/trend-collection.md)。

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
| MINIO_CLEANUP_INTERVAL | 60s | 已删除图片对象的后台清理重试间隔 |

应用和模型变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| BACKEND_PORT / FRONTEND_PORT | 8088 / 8090 | Docker 应用的主机端口 |
| AUTH_REGISTRATION_ENABLED | true | 是否允许创建本地账号 |
| SESSION_TIMEOUT | 30m | 服务端 Session 有效期 |
| AI_SETTINGS_ENCRYPTION_KEY | 空 | 管理后台保存模型 API Key 所需的 AES-256 主密钥，必须是 Base64 编码的 32 字节随机值；所有后端实例必须使用同一值，轮换前需迁移已有密文 |
| SESSION_COOKIE_SECURE | false（本地 HTTP 默认值） | 生产 HTTPS 必须设置为 true |
| DASHSCOPE_API_KEY | 空 | 百炼 API Key；配置后由首选 LLM 引擎调用 |
| BAILIAN_ENABLED | true | 是否启用文本推荐大模型（默认优先使用；离线测试需显式设为 false） |
| BAILIAN_MODEL | qwen-plus | 文本推荐模型 |
| BAILIAN_VISION_ENABLED | false | 是否启用图片视觉识别 |
| BAILIAN_VISION_MODEL | qwen-vl-plus | 衣物与个人形象共用的视觉识别模型 |
| BAILIAN_PROFILE_ANALYSIS_READ_TIMEOUT | 30s | 个人形象分析读取超时 |
| BAILIAN_ENDPOINT | 百炼兼容接口 | 模型请求地址 |
| BAILIAN_CONNECT_TIMEOUT / BAILIAN_READ_TIMEOUT | 3s / 30s | 模型连接和读取超时；包含个人分析的完整推荐请求可能超过原来的 8 秒 |
| BAILIAN_IMAGE_ENABLED | true | 是否启用每日模特生图 |
| BAILIAN_IMAGE_MODEL | wan2.6-image | 阿里云万相图像编辑模型 |
| BAILIAN_IMAGE_ENDPOINT | 万相多模态生成接口 | 图像生成任务地址 |
| BAILIAN_IMAGE_TASK_ENDPOINT | `/api/v1/tasks` | 异步任务轮询地址 |
| BAILIAN_IMAGE_PROTOTYPE_MALE / FEMALE | classpath 原型资源 | 男/女模特原型图 |
| BAILIAN_IMAGE_REFERENCE_BASE_URL | 空 | 仅用于把相对演示衣物图片转换为公网参考地址；上传衣物优先走私有图片 Base64 |
| BAILIAN_IMAGE_CONNECT_TIMEOUT / READ_TIMEOUT | 5s / 120s | 生图连接和读取超时；同步调用需要等待图片生成完成 |
| BAILIAN_IMAGE_TASK_TIMEOUT / POLL_INTERVAL | 90s / 2s | 生图任务最长等待时间和轮询间隔 |

管理员首次通过界面保存模型 API Key 前，应生成一次 `AI_SETTINGS_ENCRYPTION_KEY` 并安全备份；示例生成命令见 `.env.example`。部署端未配置该值时，读取现有环境变量密钥和修改非敏感模型信息仍可用，但不能保存新的数据库密钥。

授权趋势源变量：`TREND_CONFIGURED_DEMO_ENABLED`、`TREND_HOT_BOARDS_ENABLED`、`TREND_MAINSTREAM_FOLLOWERS`、`TREND_NICHE_SHARE`、`TREND_IMPORT_DIRECTORY`、`TREND_JSON_URL`、`TREND_PLATFORM`、`TREND_WEB_URLS`、`TREND_WEB_PLATFORM`、`TREND_WEB_MAX_PAGE_CHARS`、`TREND_WEB_MAX_ARTICLES_PER_PAGE`、`TREND_CONNECT_TIMEOUT`、`TREND_READ_TIMEOUT`、`TREND_CACHE_TTL`。`TREND_CONFIGURED_DEMO_ENABLED` 默认 `false`；开启后仅在真实结果为空且查询未选择具体平台/主题时返回固定捕获时间的本地演示内容，真实内容永远优先，演示内容不入库且不计算互动增长。旧的 `TREND_EDITORIAL_ENABLED` / `TREND_EDITORIAL_FEEDS` 设置不再让出版物 RSS 进入趋势结果。匿名热榜默认关闭；显式开启后，同一平台的优先级是配置端点 > 本地导入文件 > 公开热榜。任何来源都没有返回可用内容时，趋势接口返回空列表，并在 `sources` 中给出每个来源的状态，不回退到开发样本（除非显式开启上述演示开关）。JSON 源必须返回只含 `items` 的对象；每条记录必须包含 `id`、`platform`、`title`、`topicTags`、`heatScore`、`publishedAt`、`sourceUrl`、`imageUrl`，可选 `summary`。互动证据中的 `authorFollowers` 是可选的非负整数；画廊用 `fullBodyImageUrl` 必须来自同一条目的 `imageUrl` 或 `evidence.images`。条目数为 1-50，热度为 0-100 整数，时间为 ISO-8601，链接为 HTTP(S)，`imageUrl` 和 `summary` 可为 `null`。任一 JSON 条目约束失败时整批拒绝，不会把部分脏数据标记为实时趋势。

网页源是逗号或换行分隔的、由服务端管理员配置的公开 HTTP(S) URL，不接受前端传入任意地址。`ConfiguredWebTrendSourceAdapter` 会限制 URL 数量、单页大小和文章数量，拒绝 localhost、内网/回环地址、带用户信息的 URL，并按缓存 TTL 复用结果。它优先读取 OpenGraph/JSON-LD，缺失时回退到页面标题、`article` 标题、可见摘要、标签、`time` 和图片；网页没有统一可信热度，因此界面会显示“来源页信号评分”。配置前仍需确认来源授权、服务条款和 robots 规则，不能绕过登录、验证码或访问控制。

天气变量：WEATHER_PRIMARY_BASE_URL、WEATHER_FALLBACK_GEOCODING_BASE_URL、WEATHER_FALLBACK_FORECAST_BASE_URL、WEATHER_CONNECT_TIMEOUT、WEATHER_READ_TIMEOUT、WEATHER_CACHE_TTL、WEATHER_CACHE_MAX_SIZE。默认使用 wttr.in，失败后回退到 Open-Meteo，并在进程内缓存天气结果。连接超时默认 5 秒，读取超时默认 15 秒，避免真实接口在较慢网络下被过早中断；可通过环境变量调整。两个真实来源均失败时仍返回 503，不用虚构天气替代。

离线答辩演示（默认关闭）：WEATHER_CONFIGURED_DEMO_ENABLED 默认 `false`，仅当两个真实天气 provider 都失败且请求城市与 WEATHER_CONFIGURED_CITY 一致时，才返回静态快照，`source` 为 `configured-demo`，前端明确标注“配置演示天气/非实时”。这是静态演示数据，不是实时天气，绝不能冒充实时天气；启用时需在 `.env` 显式填全 WEATHER_CONFIGURED_CITY、WEATHER_CONFIGURED_TEMPERATURE_C、WEATHER_CONFIGURED_APPARENT_TEMPERATURE_C、WEATHER_CONFIGURED_PRECIPITATION_MM、WEATHER_CONFIGURED_WEATHER_CODE、WEATHER_CONFIGURED_WIND_SPEED_KMH，任一字段缺失、非有限、温度不合理或降水风速为负都会导致应用启动失败（fail-fast）。城市未找到（NOT_FOUND）不会被静态快照掩盖，仍返回 404。

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
npm audit --audit-level=high
~~~

`npm audit` 只接受当前锁文件真实结果；若报告 high 或 critical，先修复依赖并重新生成 `package-lock.json`，不要把历史审计结果当作当前通过证明。

Compose 文件校验：

~~~powershell
docker compose config -q
~~~

前端 E2E 冒烟验证：

~~~powershell
docker compose -f docker-compose.yml -f docker-compose.e2e.yml --profile app up --build -d
Set-Location frontend
npm ci
npm run test:e2e
~~~

E2E 默认使用系统已安装的 Google Chrome，不需要额外下载浏览器。它会使用独立测试用户，通过真实页面完成“打开页面 -> 添加衣物 -> 生成推荐 -> 收藏推荐”，并从后端历史接口再次确认 saved=true。失败时的 trace、截图、视频和 HTML 报告位于 output/playwright/，不会进入 Git。

可通过 E2E_BASE_URL、E2E_API_URL、E2E_CITY 和 E2E_BROWSER_CHANNEL 覆盖默认的前端地址、后端地址、测试城市和浏览器通道。需要在可见浏览器中演示时运行 npm run test:e2e:headed。

个人档案真实模型联调单独使用 `docker-compose.profile-live.yml`。它使用独立 Compose 项目和数据卷，前端端口 8190、后端端口 8108，避免测试账号、照片和推荐进入常用环境。需先确认允许使用已配置的模型额度；个人形象分析使用程序绘制的虚构头像，穿搭生图使用项目内置的男、女模特原型图和衣物图片。卡通头像仅用于个人档案分析，不得替换生图模特原型。默认测试不会运行这组付费用例。

~~~powershell
docker compose -f docker-compose.yml -f docker-compose.profile-live.yml --profile app up --build -d --wait
Set-Location frontend
$env:E2E_BASE_URL = 'http://127.0.0.1:8190'
$env:E2E_LIVE_PROFILE = 'true'
$env:E2E_LIVE_PROJECT = 'fashion-profile-live-local'
# 可选：同时验证 wan2.6-image 返回的图片在推荐页显示。
$env:E2E_LIVE_VISUAL = 'true'
npx playwright test e2e/personal-profile-live.spec.js --project=chromium
~~~

如果 Docker CLI 不在 PATH，可将 `E2E_DOCKER_PATH` 设置为 docker.exe 的实际路径。测试会检查数据库所属项目，仅读写隔离环境；临时管理员用于切换该测试环境的模型启停状态，结束后恢复环境配置。调用付费模型前会检查生图模特配置并预检真实天气；模特原型被测试头像替换，或两个天气来源均不可用时，停止测试。验证覆盖真实视觉分析、人工修正、模型推荐、收藏反馈、规则降级、旧分析失效、照片权限与手机布局；启用生图联调时也检查图片显示，以及页面天气、城市和场合与推荐记录的一致性。结果 JSON 和截图保存在 `output/live-profile/results/`，不包含 API Key。

`e2e/personal-profile-analysis.spec.js` 和 `e2e/recommendation-context.spec.js` 使用接口桩验证页面状态与推荐上下文，可在不调用模型的情况下单独运行。

提交前建议再检查：

~~~powershell
git diff --check
git status --short
~~~

## 研究评估（离线、可复现）

推荐结果输入边界与规则引擎的评估协议默认离线运行，只读取本地文件并计算，不联网、不调用百炼或任何模型 API：

~~~powershell
python scripts/evaluation/evaluate_recommendations.py
python -m unittest discover -s scripts/evaluation -p "test_*.py"
~~~

指标定义、数据集格式、输入边界、固定 seed 与对抗性用例见 [推荐评估协议](docs/evaluation-protocol.md)。仓库内的 `fixture_wardrobe.json` 仅为演示 fixture，不作为实验结论。

## 常见问题

### 页面能打开，但推荐失败

请先确认衣橱中至少有两件已完善且类别不同的衣物。推荐还需要读取城市天气；如果网络无法访问天气服务，请检查 WEATHER_* 地址或稍后重试。

### 图片上传失败

确认 MinIO 和 minio-init 已成功运行，并使用 JPG、PNG 或 WEBP 图片，大小不超过 MINIO_MAX_FILE_SIZE。minio-init 显示 Exited (0) 是一次性初始化成功，不是错误。

默认单张图片上限为 10 MiB；Nginx 与后端 multipart 请求上限为 11 MiB，为表单字段和请求头预留空间。如果提高 `MINIO_MAX_FILE_SIZE`，也应同步提高 `frontend/nginx.conf` 的 `client_max_body_size` 与后端 `spring.servlet.multipart.max-request-size`。

### 没有百炼 API Key 是否无法使用

没有 Key 时仍然可以使用规则降级推荐；配置 `DASHSCOPE_API_KEY` 后，默认优先使用 qwen-plus。若要离线运行或避免模型费用，请显式设置 `BAILIAN_ENABLED=false` 和 `BAILIAN_IMAGE_ENABLED=false`。视觉识别另外受 BAILIAN_VISION_ENABLED 控制。

### 端口被占用

Docker 模式可修改 .env 中的 POSTGRES_PORT、MINIO_PORT、BACKEND_PORT 或 FRONTEND_PORT 后重新启动。源码模式下，后端默认使用 8080，前端默认使用 5173，代理地址见 frontend/vite.config.js。

### 如何清空本地演示数据

~~~powershell
docker compose down -v
docker compose --profile app up --build -d
~~~

这会删除所有本地数据库和对象存储数据，包括账号、衣橱、推荐记录和上传图片，请确认不再需要当前演示数据后再执行。


## 推荐与记录体验（2026-10-07）

- 完整搭配约束对大模型和规则降级同时生效。规则先补足上装、下装（或连体装），天气较冷时优先补外套，再选择鞋履与配饰，最多四件；高温排除名称或风格明确标注的羽绒、棉服、加绒等单品，低温排除明确标注的短裤、凉鞋等。不推断未录入的材质、厚薄与真实合身程度。
- 推荐页展示方案真实生成时间，移除固定星期模板及前后日期切换。历史方案不作为未来天气预报或穿搭计划展示。
- 推荐页及助手结果提供“保留”和“换一件”。替换单件会锁定其他单品；服务端验证 `lockedItemIds` 和 `excludedItemIds` 的当前用户归属、确认状态、数量与冲突，并校验模型遵守保留条件。无可用替代时保留原方案并解释原因。
- 天气不可用时，助手优先引导开启定位；定位被拒绝、不支持或用户不方便授权时，改为选择城市获取真实天气。只有城市查询仍失败后才展示手动温度（-50 至 60℃）入口，首屏不展示该选项。请求字段为 `manualTemperatureC`，结果以 `weather.source=user-provided` 持久化。体感、降水、天气编码与风速保持未知，手填温度不会覆盖导航中的实时天气。默认仍请求真实天气。
- 历史接口支持 `filter=all|saved|rated` 和最长 120 字的 `query`，筛选、检索与分页均由服务端执行；查询范围包含当前用户全部历史。响应中的 `statistics` 提供全账号总数、收藏数、评分数、平均评分及推荐覆盖单品数，不随筛选改变。近七天图表仍明确按当前已加载记录计算。
- 界面不会接纳较旧搜索请求覆盖新筛选结果；会话切换后丢弃原账号请求结果。无待办时通知不显示红点。

验证方式：后端 Maven 测试、前端 `npm run test:unit`、`npm run build`，以及 `e2e/product-improvements.spec.js`、`e2e/global-assistant.spec.js`、`e2e/recommendation-context.spec.js`。新增浏览器用例使用接口模拟，不能代替真实模型质量评估。

定位成功后，推荐请求使用成对的 `latitude`、`longitude` 查询当前位置天气，不将“当前位置”当作城市名称进行地理编码。显式选择其他城市时不携带旧坐标，坐标不写入推荐记录；权限被拒绝时给出开启网站定位权限的说明并允许改用城市。

趋势来源在应用启动后读取一次，此后按北京时间每天 12:00 刷新（`TREND_REFRESH_CRON=0 0 12 * * *`，时区固定为 `Asia/Shanghai`）。原 `TREND_REFRESH_INTERVAL` 不再控制趋势调度。默认微博/抖音/小红书来源连接内部 `trend-collector` 服务，由 Scrapling 动态搜索新帖子并筛选最近24小时的图文；自定义 `TREND_DOUYIN_URL` / `TREND_WEIBO_URL` / `TREND_XIAOHONGSHU_URL` 可覆盖默认来源。浏览器访问受限时记录采集失败，真实结果为空时显示空状态；如显式取消采集端点而使用本地导入文件，文件仍需由外部采集器先更新。采集失败保留上次结果；预置参考图片的固定时间不作为真实来源的最近采集时间展示。
