# 开发期边界与生产化路径

本文记录毕业设计原型中仍然主动保留的边界。当前实现优先保证身份可信、业务闭环可运行、外部能力失败时不伪造结果；它不是完整的互联网生产账号系统或模型治理平台。

## 边界总览

| 边界 | 当前实现 | 安全失败方式 | 生产化补充 |
| --- | --- | --- | --- |
| 本地账号与会话 | Spring Security、BCrypt、服务端 Session、CSRF | 匿名个人请求返回 401；伪造用户头无效 | OIDC/统一身份、找回密码、登录限流和审计 |
| 规则推荐 fallback | `llm` 失败时显式返回 `development-rule-v1` | 只使用当前用户已完善衣物，不接受模型编造 ID | 模型网关、熔断告警、质量和降级率监控 |
| 趋势数据源 | 可配置授权 JSON 源或服务端配置的公开网页源，失败时返回 `demoMode=true` 的开发样本 | 严格校验并限制网页采集目标；不接受前端任意 URL，不把派生评分冒充平台热度 | 授权源治理、签名、快照存档、运营审核和 robots/条款审查 |
| 视觉识别 | 服务端开关与本次上传用户同意同时成立才允许调用 | 默认不调用；信息不完整时人工确认 | 同意审计、配额、图片保留期限和删除策略 |

## 本地账号与 Session

个人接口不再接受 `X-User-Id` 或图片查询参数作为身份。注册密码以 BCrypt 保存；登录成功后，服务端创建 Session，浏览器仅持有 HttpOnly、SameSite=Lax 的 `JSESSIONID` Cookie。控制器从 `Principal` 取得用户名，仓储查询仍同时约束记录 ID 与用户名，因此用户 A 请求用户 B 的推荐或图片返回 404。

所有写请求受 CSRF 保护。前端先从 `GET /api/v1/auth/csrf` 获取 token，再发送服务端声明的请求头；403 时只刷新一次 token。Session 过期或退出后，前端清空私有状态，进行中的旧请求也不能回写新会话。

这仍是本地账号原型：没有邮箱验证、找回密码、多因素认证、登录限流或设备管理。公网部署至少应启用 HTTPS、设置 `SESSION_COOKIE_SECURE=true`、关闭公开注册或接入 OIDC，并增加认证事件审计。

## 规则推荐 fallback

推荐大模型是首选引擎：`BAILIAN_ENABLED` 默认为 `true`，配置 `DASHSCOPE_API_KEY` 后优先调用 qwen-plus。未配置 Key、模型超时、响应无法解析、字段越界、衣物 ID 重复或引用当前衣橱之外的单品时，系统进入规则推荐。离线测试必须显式设置 `BAILIAN_ENABLED=false`。响应和持久化记录的 `engine` 明确区分 `llm` 与 `development-rule-v1`，不会把规则结果冒充模型结果。

每次生成都会持久化审计元数据：合法 LLM 结果保存真实 provider 元数据（provider call ID、模型名、prompt 版本与三类 token），规则降级只保存稳定的枚举式 `fallback_reason`（如 `llm-disabled`、`missing-api-key`、`request-failed`、`response-invalid`、`result-invalid`、`duplicate-item-ids`、`foreign-item-ids`、`same-category`），不保存异常原文。API 通过嵌套 `generationAudit` 返回这些字段，`engine` 仍保留在顶层。规则降级时 provider 元数据（model name、prompt version、provider call ID 与三类 token）保持为空，但 `fallback_reason` 非空；启用 V3 审计列之前的历史记录，所有审计字段（含 `fallback_reason`）仍保持为空，不伪造模型元数据。

规则引擎只使用当前用户已完善的衣物，且至少需要两个不同类别。条件不成立时返回 422，不生成虚假方案。生产环境可保留 fallback，但应监控可用率、耗时、校验失败原因和降级率，并向用户区分智能生成与基础搭配。

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

结构由 Flyway 管理，运行期 `schema.sql` 初始化已关闭。旧数据库通过 baseline version 0 接管，再执行 V1 的兼容补列与索引语句；迁移测试验证原数据保留和二次运行幂等。后续结构变化必须新增版本迁移，不能修改已经执行的 V1。

推荐审计元数据由 V3 迁移加入 `recommendations` 的可空列（model_name、prompt_version、provider_call_id、prompt_tokens、completion_tokens、total_tokens、generation_latency_ms、fallback_reason），并带非负 CHECK 约束；迁移兼容 H2/PostgreSQL 与旧 V2 数据，旧行新列为空。

Redis 已从 Compose 和依赖中删除，因为当前业务没有消费者。天气和趋势仅使用有明确调用方的进程内 Caffeine 缓存；需要跨实例缓存时，应先定义一致性、失效和监控要求，再引入外部缓存。

当前不使用 pgvector：数据库只用标准 PostgreSQL 类型，迁移与查询中没有 vector 列或向量检索。监控保持默认关闭：Actuator 仅暴露 `health` 与 `info`，不提供 Prometheus 或 metrics 指标端点；需要在生产环境观测时，再按调度要求显式开放并接入采集端。

## 答辩说明口径

- “身份来自 Spring Security Session，客户端伪造用户请求头不会改变当前用户。”
- “规则引擎是明确标记的降级结果，不冒充大模型输出。”
- “文本推荐默认优先使用大模型；没有 Key 或模型失败时才降级。自动化 E2E 通过 Compose 覆盖显式关闭模型，避免消耗付费额度。”
- “趋势源通过严格契约接入授权数据；不可用时 `demoMode` 主动标记开发样本。”
- “视觉识别默认不调用，服务端开关和用户本次明确同意缺一不可。”
- “离线天气演示默认关闭，只有在 `.env` 显式启用且两个真实 provider 都失败时才返回静态快照，`source=configured-demo`，前端明确标注‘配置演示天气/非实时’，绝不冒充实时天气；城市未找到（NOT_FOUND）仍返回 404，不会被静态快照掩盖。”

## 代码证据

- 认证与 CSRF：`security/SecurityConfig.java`、`auth/AuthController.java`、`frontend/src/composables/useFashionApp.js`。
- 数据隔离：wardrobe、recommendation、style 控制器与对应仓储查询。
- 趋势：`ConfiguredJsonTrendSourceAdapter.java`、`ConfiguredWebTrendSourceAdapter.java`、`TrendService.java` 及对应适配器测试。
- 视觉同意：`WardrobeController.java`、`WardrobeService.java`、`WardrobeView.vue`。
- 推荐审计元数据：`V3__recommendation_audit.sql`、`RecommendationAudit.java`、`RecommendationFallbackReason.java`、`BailianRecommendationClient.java`、`RecommendationService.java`。
- 迁移与反例测试：`V1__baseline_schema.sql`、`AuthenticationIntegrationTest.java`、`FlywayMigrationTest.java`、`RecommendationControllerTest.java`、`BailianRecommendationClientTest.java`。
- 离线天气演示：`ConfiguredWeatherSnapshot.java`、`WeatherService.java`、`WeatherServiceTest.java`、`frontend/src/composables/useFashionApp.js`。
