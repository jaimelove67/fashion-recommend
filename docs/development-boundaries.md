# 开发期边界与生产化路径

本文记录毕业设计原型中仍然主动保留的边界。当前实现优先保证身份可信、业务闭环可运行、外部能力失败时不伪造结果；它不是完整的互联网生产账号系统或模型治理平台。

## 边界总览

| 边界 | 当前实现 | 安全失败方式 | 生产化补充 |
| --- | --- | --- | --- |
| 本地账号与会话 | Spring Security、BCrypt、服务端 Session、CSRF | 匿名个人请求返回 401；伪造用户头无效 | OIDC/统一身份、找回密码、登录限流和审计 |
| 规则推荐 fallback | `llm` 失败时显式返回 `development-rule-v1` | 只使用当前用户已完善衣物，不接受模型编造 ID | 模型网关、熔断告警、质量和降级率监控 |
| 趋势数据源 | 可配置授权 JSON 源或服务端配置的公开网页源；内容经过 AI 初审和人工终审后才进入公共趋势 | 严格校验并限制网页采集目标；不接受前端任意 URL，不把派生评分冒充平台热度；AI 失败或未人工通过时保持隔离 | 授权源治理、签名、快照存档、细粒度审核权限和 robots/条款审查 |
| 视觉识别 | 服务端开关与本次上传用户同意同时成立才允许调用 | 默认不调用；信息不完整时人工确认 | 同意审计、配额、图片保留期限和删除策略 |
| 管理员运营后台 | `ROLE_ADMIN` 保护的概览、账号治理、反馈审核、趋势 AI 初审/人工终审、推荐运行聚合和操作审计 | 普通用户/匿名请求分别返回 403/401；后台不返回密码、私有图片和推荐正文；AI 不能直接发布；管理员不能停用自己 | 细粒度权限、审批流、批量治理、实时基础设施监控和告警 |

## 本地账号与 Session

个人接口不再接受 `X-User-Id` 或图片查询参数作为身份。注册密码以 BCrypt 保存；登录成功后，服务端创建 Session，浏览器仅持有 HttpOnly、SameSite=Lax 的 `JSESSIONID` Cookie。控制器从 `Principal` 取得用户名，仓储查询仍同时约束记录 ID 与用户名，因此用户 A 请求用户 B 的推荐或图片返回 404。

所有写请求受 CSRF 保护。前端先从 `GET /api/v1/auth/csrf` 获取 token，再发送服务端声明的请求头；403 时只刷新一次 token。Session 过期或退出后，前端清空私有状态，进行中的旧请求也不能回写新会话。

这仍是本地账号原型：没有邮箱验证、找回密码、多因素认证、登录限流或设备管理。公网部署至少应启用 HTTPS、设置 `SESSION_COOKIE_SECURE=true`、关闭公开注册或接入 OIDC，并增加认证事件审计。

## 管理员运营第一批

当前管理端只做平台级治理，不把管理员权限扩展成读取普通用户私有内容的后门。`GET /api/v1/admin/overview` 返回账号、衣物和推荐的聚合计数；`GET /api/v1/admin/users` 按用户名分页返回脱敏账号元数据；`PUT /api/v1/admin/users/{username}/status` 修改目标账号的 `enabled` 状态。所有 `/api/v1/admin/**` 请求由 Spring Security 的 `ROLE_ADMIN` 在服务端保护，前端是否显示“管理”入口不构成安全边界。

账号停用会影响后续登录；当前登录管理员不能停用自己，以避免管理入口被自锁。第一批尚未包含密码重置、角色编辑、批量操作、趋势配置写入、审计日志查询或管理员对用户衣橱/推荐内容的查看。`docker/postgres/demo/seed.sql` 中的 `demo-admin` 仅用于本地演示，生产环境必须使用受控账号 provisioning，不得沿用示例密码。

## 管理员运营后台第二批

当前前端管理区使用独立工作台布局，包含运营总览、账号与权限、反馈审核、趋势内容审核和操作日志五个真实模块。概览从现有业务表聚合账号启用状态、衣物/推荐/收藏数量、反馈平均评分、LLM 结果与规则降级数量、待处理反馈、待人工识别衣物和图片清理队列；推荐“成功”只按已落库的真实 `engine=llm` 统计，零数据时不显示虚假的成功率。运营总览的推荐运行和账号构成图表采用本地 lieflat-charts Lupi Basics 的 F1 Rung Bars、F4 Tick Donut 模板语法，以原生 SVG 按真实记录/百分比绘制，不添加没有快照支撑的时间趋势。

反馈审核在 V4 中给 `recommendation_feedback` 增加 `moderation_status`、`handled_by` 和 `handled_at`。管理员可以把反馈标记为待处理、已查看或已解决，但不改变用户原始评分和评论；用户重新提交反馈时状态会回到 `PENDING`。账号状态变化和反馈处理均写入 `admin_audit_logs`，日志只保存操作者、动作、对象、结果和安全说明。

当前没有独立的公共服装目录表，因此后台不提供虚假的服装上架、下架或图片编辑入口；用户私有衣橱、推荐正文和图片仍按隐私边界隔离。推荐运行区展示已持久化的 LLM/规则降级聚合，不等同于完整的基础设施监控或告警系统。

## 趋势内容轻量审核 Workflow（V6）

趋势采集与管理员会话解耦：定时采集任务只负责把经过来源契约校验的条目写入 `trend_contents`，新条目默认是 `PENDING_AI`。独立的 Spring `@Scheduled` 任务按批读取待初审内容，调用现有百炼兼容接口；审核请求只包含平台、标题、摘要、标签、来源链接、发布时间、是否有图片和作者等必要元数据，不携带用户私有衣橱，也不把完整外部网页正文直接送入模型。

状态流转是业务层的轻量状态机，不是 LangChain：

`PENDING_AI → PENDING_HUMAN → APPROVED / REJECTED`

模型输出严格限制为 `decision`、`riskLevel`、`reason` 三个 JSON 字段。无论 AI 建议 PASS、REVIEW 还是 REJECT，服务端都只把内容放入人工队列；请求失败、Key 缺失或 JSON 不合规时进入 `AI_FAILED`，保存稳定错误码，不保存异常原文。管理员可以重试；若需要直接处理 AI 失败内容，必须填写人工说明。人工通过会清除隐藏标记，人工驳回会隐藏内容；已通过内容仍可被管理员单独下架或恢复，但恢复不会改变审核结论。

普通趋势接口的发布条件是 `moderation_status='APPROVED' AND hidden=FALSE`。因此“抓取成功”“AI 通过”与“对普通用户公开”是三个不同事件，任何一个中间状态都不能绕过人工终审。审核状态、模型名、provider call ID、prompt 版本、审核人、审核时间和说明保存在 V6 新增字段中，管理动作写入已有 `admin_audit_logs`。

## 规则推荐 fallback

推荐大模型是首选引擎：`BAILIAN_ENABLED` 默认为 `true`，配置 `DASHSCOPE_API_KEY` 后优先调用 qwen-plus。未配置 Key、模型超时、响应无法解析、字段越界、衣物 ID 重复或引用当前衣橱之外的单品时，系统进入规则推荐。离线测试必须显式设置 `BAILIAN_ENABLED=false`。响应和持久化记录的 `engine` 明确区分 `llm` 与 `development-rule-v1`，不会把规则结果冒充模型结果。

每次生成都会持久化审计元数据：合法 LLM 结果保存真实 provider 元数据（provider call ID、模型名、prompt 版本与三类 token），规则降级只保存稳定的枚举式 `fallback_reason`（如 `llm-disabled`、`missing-api-key`、`request-failed`、`response-invalid`、`result-invalid`、`duplicate-item-ids`、`foreign-item-ids`、`same-category`），不保存异常原文。API 通过嵌套 `generationAudit` 返回这些字段，`engine` 仍保留在顶层。规则降级时 provider 元数据（model name、prompt version、provider call ID 与三类 token）保持为空，但 `fallback_reason` 非空；启用 V3 审计列之前的历史记录，所有审计字段（含 `fallback_reason`）仍保持为空，不伪造模型元数据。

规则引擎只使用当前用户已完善的衣物，且至少需要两个不同类别。条件不成立时返回 422，不生成虚假方案。生产环境可保留 fallback，但应监控可用率、耗时、校验失败原因和降级率，并向用户区分智能生成与基础搭配。

## 每日推荐视觉与衣橱边界（V7 + Wan 图像生成）

每日推荐的解释区只保留四个可追溯维度：天气适配、场合适配、风格方向、衣橱依据。前端不会直接信任推荐历史快照里的衣物对象，而是按衣物 id 与当前用户衣橱重新关联；不存在、跨用户、重复或 `NEEDS_MANUAL_REVIEW` 的衣物不会展示，图片失效时只显示“暂无图片”状态。

个人档案通过 V7 的可空 `style_profiles.gender` 保存 `MALE` 或 `FEMALE`。每日模特原型只在档案已有对应性别时作为生图输入，未填写时显示待补充，不从照片、衣物或参考截图推断。`RecommendationVisualService` 在生成图像前再次按当前用户的衣橱 id 关联推荐快照，并排除已删除、跨用户和 `NEEDS_MANUAL_REVIEW` 单品；`BailianImageGenerationClient` 将指定原型图和最多三个当前衣橱图片参考以 Base64 送入 `wan2.6-image`，同时把全部已选单品的名称、类别、颜色和风格写入受限提示词。未配置 Key、性别或原型资源，及阿里云任务失败/超时时，接口返回明确的 `UNAVAILABLE`/`FAILED` 状态，前端保留已确认单品并显示未生成状态，不把原型或静态图当作成功结果。

图像接口使用异步任务创建与轮询，返回的阿里云结果链接只作为当日会话主视觉使用；生产环境若需要长期保存，应在收到成功链接后立即转存到受控对象存储。演示用的两张过渡静态生图已移出前端资源目录，运行时不会再根据固定单品组合匹配它们。

## 授权趋势源、网页采集与 demoMode

配置 `TREND_JSON_URL` 后，`ConfiguredJsonTrendSourceAdapter` 请求管理员提供的授权 JSON 源。根对象、字段集合、条目数量、重复 ID、标签、热度、ISO-8601 时间和 HTTP(S) URL 都经过整批校验；成功结果在进程内按 `TREND_CACHE_TTL` 缓存。

配置 `TREND_WEB_URLS` 后，`ConfiguredWebTrendSourceAdapter` 读取服务端配置的公开 HTTP(S) 页面。它抽取 OpenGraph/JSON-LD 和可见 HTML 中的标题、摘要、标签、发布时间、图片、文章链接，并标准化为趋势条目；页面列表中的多个 `article` 可拆成多个条目。网页没有统一可信的热度字段，适配器的 `scoreLabel` 为“来源页信号评分”，分数只由新鲜度和内容完整度派生，不能解释为平台实时热度。URL 数量、页面字符数和每页文章数有上限；localhost、内网/回环地址和带用户信息的 URL 会被拒绝，服务端不会提供前端任意 URL 抓取接口。

JSON 适配器按 Spring 顺序优先于网页适配器；当 JSON 源没有返回可用结果时才尝试网页源。网页源允许多个页面部分成功，只有全部页面失败或没有可识别内容时才失败。两类源均未配置、请求失败或不可用时，`TrendService` 回退到 10 条内置开发样本并设置 `demoMode=true`；风潮页从中按 `heatScore` 取 Top 10 进入弧形画廊。有效源经过页面筛选后即使结果为空，仍保持 `demoMode=false`，避免把“没有匹配项”误报为数据源故障。配置网页前仍须确认来源授权、服务条款和 robots 规则；项目不绕过登录、验证码或访问控制，也不会在没有授权时抓取第三方平台。

## 逐次视觉识别同意

视觉识别调用必须同时满足：

1. 用户在本次上传中勾选“使用 AI 自动识别”。
2. 服务端 `BAILIAN_VISION_ENABLED=true`。
3. 已配置可用的 `DASHSCOPE_API_KEY`。

未同意时服务端完全跳过识别服务，即使视觉开关已经开启。用户可直接填写名称、类别和颜色，记录保存为 `MANUAL_CORRECTED`；识别关闭、失败或返回不完整时进入 `NEEDS_MANUAL_REVIEW`，待确认衣物不会参与推荐。

生产化还需要保存同意与模型调用审计、限制配额、明确图片保留期限、支持删除请求并监控识别失败。当前图片删除已采用数据库清理任务队列，MinIO 失败时由后台重试；生产环境仍应补充任务告警、死信处理和对象生命周期策略。

## 数据库迁移与基础设施

结构由 Flyway 管理，运行期 `schema.sql` 初始化已关闭。旧数据库通过 baseline version 0 接管，再执行 V1 的兼容补列与索引语句；V7 以新的可空列迁移个人档案性别，迁移测试验证原数据保留和二次运行幂等。后续结构变化必须新增版本迁移，不能修改已经执行的迁移。

推荐审计元数据由 V3 迁移加入 `recommendations` 的可空列（model_name、prompt_version、provider_call_id、prompt_tokens、completion_tokens、total_tokens、generation_latency_ms、fallback_reason），并带非负 CHECK 约束；迁移兼容 H2/PostgreSQL 与旧 V2 数据，旧行新列为空。

管理员治理由 V4 迁移加入反馈审核状态列和 `admin_audit_logs`；审核状态有 `PENDING`、`REVIEWED`、`RESOLVED` 三种稳定值，审计日志不使用目标账号外键，避免未来账号清理破坏治理证据。V4 只增加结构，不重置演示用户业务数据。

趋势快照由 V5 迁移加入；趋势审核由 V6 迁移加入 `moderation_status`、AI 结论/风险/理由、模型调用元数据和人工终审字段，并建立审核队列索引。V6 对既有趋势行使用 `PENDING_AI` 默认值，升级不会将旧内容自动发布；必须经过同一条审核链路后才会重新出现在普通趋势查询中。

Redis 已从 Compose 和依赖中删除，因为当前业务没有消费者。天气和趋势仅使用有明确调用方的进程内 Caffeine 缓存；需要跨实例缓存时，应先定义一致性、失效和监控要求，再引入外部缓存。

当前不使用 pgvector：数据库只用标准 PostgreSQL 类型，迁移与查询中没有 vector 列或向量检索。监控保持默认关闭：Actuator 仅暴露 `health` 与 `info`，不提供 Prometheus 或 metrics 指标端点；需要在生产环境观测时，再按调度要求显式开放并接入采集端。

## 答辩说明口径

- “身份来自 Spring Security Session，客户端伪造用户请求头不会改变当前用户。”
- “规则引擎是明确标记的降级结果，不冒充大模型输出。”
- “文本推荐默认优先使用大模型；没有 Key 或模型失败时才降级。自动化 E2E 通过 Compose 覆盖显式关闭模型，避免消耗付费额度。”
- “趋势源通过严格契约接入授权数据；不可用时 `demoMode` 主动标记开发样本。”
- “趋势内容使用轻量 Workflow：Spring 定时任务负责 AI 初审，数据库状态机负责隔离和可追踪流转，管理员负责人工终审；没有引入 LangChain，AI 不能直接发布。”
- “视觉识别默认不调用，服务端开关和用户本次明确同意缺一不可。”
- “离线天气演示默认关闭，只有在 `.env` 显式启用且两个真实 provider 都失败时才返回静态快照，`source=configured-demo`，前端明确标注‘配置演示天气/非实时’，绝不冒充实时天气；城市未找到（NOT_FOUND）仍返回 404，不会被静态快照掩盖。”

## 代码证据

- 认证与 CSRF：`security/SecurityConfig.java`、`auth/AuthController.java`、`frontend/src/composables/useFashionApp.js`。
- 数据隔离：wardrobe、recommendation、style 控制器与对应仓储查询。
- 趋势：`ConfiguredJsonTrendSourceAdapter.java`、`ConfiguredWebTrendSourceAdapter.java`、`TrendService.java` 及对应适配器测试。
- 视觉同意：`WardrobeController.java`、`WardrobeService.java`、`WardrobeView.vue`。
- 推荐审计元数据：`V3__recommendation_audit.sql`、`RecommendationAudit.java`、`RecommendationFallbackReason.java`、`BailianRecommendationClient.java`、`RecommendationService.java`。
- 每日模特生图：`BailianImageGenerationClient.java`、`RecommendationVisualService.java`、`RecommendationVisualResponse.java`、`backend/src/main/resources/reference_photo/`、`frontend/src/views/RecommendationView.vue`。
- 管理员运营后台：`V4__admin_governance.sql`、`admin/AdminController.java`、`admin/AdminService.java`、`admin/AdminFeedbackRepository.java`、`admin/AdminAuditRepository.java`、`frontend/src/views/AdminView.vue`。
- 趋势审核 Workflow：`V5__trend_snapshots.sql`、`V6__trend_moderation_workflow.sql`、`TrendModerationService.java`、`BailianTrendModerationClient.java`、`TrendAdminController.java`、`frontend/src/views/AdminView.vue`。
- 迁移与反例测试：`V1__baseline_schema.sql`、`AuthenticationIntegrationTest.java`、`FlywayMigrationTest.java`、`RecommendationControllerTest.java`、`BailianRecommendationClientTest.java`。
- 离线天气演示：`ConfiguredWeatherSnapshot.java`、`WeatherService.java`、`WeatherServiceTest.java`、`frontend/src/composables/useFashionApp.js`。
