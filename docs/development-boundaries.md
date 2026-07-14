# 开发期边界与生产化路径

本文用于明确毕业设计原型中的四项主动设计边界。它们不是被隐藏的缺陷，也不等同于生产能力：当前实现优先保证业务闭环可运行、失败状态可观察、外部服务不可用时不伪造结果，并为后续替换保留清晰接口。

## 边界总览

| 边界 | 当前目的 | 可观察行为 | 安全失败方式 | 生产化替换 |
| --- | --- | --- | --- | --- |
| X-User-Id | 在没有登录模块时验证多用户数据分区 | 前端固定发送 demo-user，后端所有个人接口读取该头 | 仓储查询仍带 user_id；跨用户记录返回 404 | Spring Security + OIDC/JWT，从服务端认证上下文取得用户 ID |
| 规则推荐 fallback | 百炼未配置、超时或响应不合规时保持核心流程可演示 | 推荐响应的 engine 明确为 llm 或 development-rule-v1 | 只从当前用户衣橱中选 2-4 件，不接受模型编造 ID | 模型网关、熔断告警、质量监控及面向用户的降级提示 |
| 趋势 demoMode | 在没有稳定合规数据源时验证趋势页面的数据契约 | API 返回 demoMode=true，首页和趋势页显示“开发样本” | 不抓取或伪造实时平台热度 | 实现 TrendSourceAdapter，接入授权数据源并保存抓取时间和来源 |
| 视觉识别默认关闭 | 控制外部调用成本和隐私风险，保证离线开发稳定 | BAILIAN_VISION_ENABLED=false；上传后返回明确识别状态 | 信息不完整时进入 NEEDS_MANUAL_REVIEW，不能参与推荐 | 经用户授权启用视觉模型，增加审计、限流、删除策略和人工复核 |

## X-User-Id：开发期用户上下文

当前系统没有实现登录页、密码、Token 签发和会话续期。X-User-Id 的职责仅是把衣橱、风格档案和推荐记录分到不同 user_id 下，以验证“用户 A 不能通过业务查询拿到用户 B 的记录”这一数据契约。

这项设计有三条明确限制：

1. 请求头可以由客户端修改，因此它不是身份认证，也不能直接部署到公网。
2. 后端默认值 demo-user 只为减少本地演示配置，不代表匿名用户拥有生产权限。
3. 图片标签无法自动携带自定义请求头，所以开发期图片代理也接受 userId 查询参数；它同样依赖生产认证层替换。

已有保护并非认证替代品，但能验证数据访问边界：仓储查询同时使用记录 ID 和 user_id，跨用户读取推荐或图片会返回 404。

生产化时，应由 Spring Security 校验 OIDC/JWT，并从服务端认证上下文取得不可由客户端伪造的 subject，再映射到内部用户 ID。控制器不再接受 X-User-Id 或 userId 查询参数。

## 规则推荐 fallback：显式降级而非伪装

系统优先尝试百炼文本模型，但以下情况会进入规则推荐：

- DASHSCOPE_API_KEY 为空。
- 模型连接超时、请求失败或返回内容无法解析。
- JSON 字段、文本长度、衣物数量不符合约束。
- 模型返回重复 ID，或引用了当前用户衣橱之外的衣物。

降级结果不会伪装成模型结果。每条推荐都保存 engine：

- llm：通过全部结构和业务约束校验的模型结果。
- development-rule-v1：开发期规则引擎结果。

规则引擎仍遵守核心业务约束：只使用当前用户已完善的衣物，优先组合不同类别，并结合服务端天气快照生成理由。衣橱无法形成至少两件不同类别的组合时，接口返回 422，而不是生成虚假方案。

生产化时应继续保留 fallback，但增加模型可用率、降级率、响应耗时和结果校验失败原因的监控；前端可向用户显示“智能生成”或“基础搭配”来源。

## 趋势 demoMode：明确的样本数据契约

当前 TrendService 返回三条内置样本，目的是验证趋势筛选、标签、来源链接、更新时间和热度展示的数据契约。响应中的 demoMode 固定为 true，前端在首页、趋势页和通知区均显示开发样本提示。

这避免了两个不成立的假设：

- 不把硬编码内容描述为实时平台数据。
- 不在没有授权、稳定性和合规评估时直接抓取第三方平台。

生产化时保留 TrendFeed 和 TrendSourceAdapter 契约，通过授权 API、运营录入或合规数据服务提供快照；只有真实来源成功返回并记录 fetchedAt 后才能把 demoMode 设为 false。

## 视觉识别默认关闭：人工确认优先

BAILIAN_VISION_ENABLED 默认是 false。关闭状态下系统不会根据文件名、颜色均值或固定模板猜测类别和颜色，也不会把未识别结果标记为成功。

上传后的状态机为：

- RECOGNIZED：视觉模型返回完整且可接受的名称、类别和颜色。
- NEEDS_MANUAL_REVIEW：视觉服务关闭、失败或结果不完整，需要用户补充信息。
- MANUAL_CORRECTED：用户完成名称、类别、颜色和风格修正。

NEEDS_MANUAL_REVIEW 记录仍保存图片，避免用户重复上传，但推荐服务会主动过滤这些记录。只有识别成功或人工确认后的衣物才能进入推荐候选。

生产化启用视觉识别时，除设置 DASHSCOPE_API_KEY 和 BAILIAN_VISION_ENABLED=true 外，还需要补充用户授权、调用审计、图片保留期限、配额限制和失败告警。

## 答辩说明口径

- “X-User-Id 是原型期用户上下文，用于验证数据隔离；生产环境由认证系统从 Token 中提供用户身份。”
- “规则引擎是显式可见的可用性降级，返回结果会标记 engine，不会把规则结果冒充大模型结果。”
- “趋势接口通过 demoMode 主动声明样本状态，目的是先验证数据契约，再接入合规实时来源。”
- “视觉识别默认关闭时系统选择人工确认，不猜测标签；待确认衣物不会进入推荐。”

## 代码证据

- 用户上下文：frontend/src/composables/useFashionApp.js，以及 wardrobe、recommendation、style 控制器。
- 推荐降级与校验：backend/src/main/java/com/fashion/recommendation/recommendation/RecommendationService.java 和 BailianRecommendationClient.java。
- 趋势样本标识：backend/src/main/java/com/fashion/recommendation/trend/TrendService.java、TrendFeed.java，以及 frontend/src/views/TrendView.vue。
- 视觉开关与状态：backend/src/main/resources/application.yml、BailianGarmentRecognitionService.java 和 WardrobeService.java。
- 对抗性测试：backend/src/test/java/com/fashion/recommendation/RecommendationControllerTest.java 和 BailianRecommendationClientTest.java。
