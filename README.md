# 知己：基于大模型的智能穿搭推荐系统

“知己”是一个面向个人衣橱的智能穿搭推荐毕业设计项目。用户可以录入或上传衣物，填写城市、场合和风格要求，系统结合天气与衣橱数据生成可解释的搭配方案，并支持保存、反馈和历史查看。

当前项目的验收边界是“衣橱录入 -> 场景输入 -> 推荐生成 -> 保存反馈”的可运行闭环，属于毕业设计原型，不包含真实登录、在线交易、虚拟试衣或后台管理。

## 技术栈

- 前端：Vue 3、Vite、Lucide Vue，入口位于 frontend/。
- 后端：Java 17、Spring Boot 3.4.2、Spring Web、JDBC、Validation、Actuator，入口位于 backend/。
- 数据与存储：PostgreSQL 16（使用 pgvector 镜像）、MinIO 私有对象存储、Caffeine 天气缓存。
- 智能能力：阿里云百炼兼容 OpenAI Chat Completions 协议；推荐模型和视觉识别默认可关闭。
- 本地编排：Docker Compose。Redis 服务已纳入本地基础设施，便于后续缓存和趋势快照扩展。

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

docker compose down -v 会同时删除 PostgreSQL、Redis 和 MinIO 的本地数据，只应在需要清空演示环境时使用。

### 方式二：本地运行前后端

先启动数据库、Redis 和 MinIO：

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

## 演示账号

项目当前没有登录页和密码认证。前端固定使用开发期用户标识：

~~~text
demo-user
~~~

后端通过 X-User-Id 请求头隔离数据；未提供请求头时也默认使用 demo-user。这只是演示边界，不能作为生产环境的身份认证方案。

## 准备答辩演示数据

完整应用启动并完成数据库初始化后，在项目根目录执行：

~~~powershell
.\scripts\seed-demo-data.ps1
~~~

脚本会在一个事务内只重置 demo-user，并准备：

- 8 件衣物，其中 1 件经历“识别失败 -> 人工修正”，最终在衣橱中标记为“人工确认”。
- 3 条不同场景的推荐历史。
- 1 条已收藏且已提交 5 星反馈的推荐。
- 1 份用于首页和个人档案页展示的风格档案。

脚本可以重复运行，数据量不会持续增加。预置推荐明确标记为 development-rule-v1，仅用于稳定演示，不伪装成真实大模型调用。

## 核心演示流程

1. 进入“衣橱”，手动添加或上传衣物。建议准备上装、下装、鞋履各一件。
2. 上传图片后，系统把图片写入 MinIO 私有桶，并通过后端图片代理展示。
3. 未启用视觉模型时，上传记录会标记为“待完善”；在衣橱中补充名称、类别、颜色和风格后保存修正。
4. 进入“推荐”，填写城市、场合和可选的风格要求，生成搭配。
5. 查看天气、推荐单品和推荐理由，保存方案并提交满意度反馈。
6. 进入“历史”查看已生成记录；“风潮”页面展示当前内置的开发期趋势样本。

推荐至少需要两件可组合的、已完善的不同类别衣物。衣物为空、只剩待人工确认记录或类别无法组合时，接口会返回明确错误，不会伪造推荐结果。

## 当前能力边界

- 未配置 DASHSCOPE_API_KEY 时，推荐使用可测试的开发期规则引擎 development-rule-v1。
- 配置百炼 Key 后，推荐会尝试调用 qwen-plus；请求超时、响应不合规或模型返回了非衣橱单品时，会回退到规则引擎。
- BAILIAN_VISION_ENABLED 默认为 false。视觉识别未启用或失败时，系统保留图片并要求人工确认，不猜测衣物属性。
- “风潮”当前返回内置的三条开发期样本数据，页面会明确标注开发样本，不代表实时平台热度。
- X-User-Id 是开发期用户上下文，用于验证数据分区，不是生产认证方案。
- 衣橱、推荐历史、反馈和个人风格档案均保存在 PostgreSQL 中。

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
| REDIS_PORT / REDIS_PASSWORD | 6380 / fashion_2404 | Redis 主机端口和密码 |
| MINIO_PORT / MINIO_CONSOLE_PORT | 9000 / 9001 | MinIO API 和控制台端口 |
| MINIO_ROOT_USER / MINIO_ROOT_PASSWORD | fashion_minio_admin / fashion_2404 | MinIO 管理账号 |
| MINIO_BUCKET | garments-private | 私有图片桶名称 |
| MINIO_MAX_FILE_SIZE | 10485760 | 最大图片大小，单位为字节 |

应用和模型变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| BACKEND_PORT / FRONTEND_PORT | 8088 / 8090 | Docker 应用的主机端口 |
| DASHSCOPE_API_KEY | 空 | 百炼 API Key；为空时使用规则推荐 |
| BAILIAN_MODEL | qwen-plus | 文本推荐模型 |
| BAILIAN_VISION_ENABLED | false | 是否启用图片视觉识别 |
| BAILIAN_VISION_MODEL | qwen-vl-plus | 视觉识别模型 |
| BAILIAN_ENDPOINT | 百炼兼容接口 | 模型请求地址 |
| BAILIAN_CONNECT_TIMEOUT / BAILIAN_READ_TIMEOUT | 3s / 8s | 模型连接和读取超时 |

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

Docker 模式可修改 .env 中的 POSTGRES_PORT、REDIS_PORT、BACKEND_PORT 或 FRONTEND_PORT 后重新启动。源码模式下，后端默认使用 8080，前端默认使用 5173，代理地址见 frontend/vite.config.js。

### 如何清空本地演示数据

~~~powershell
docker compose down -v
docker compose --profile app up --build -d
~~~

这会删除所有本地数据库、对象存储和 Redis 数据，请确认不再需要当前演示数据后再执行。
