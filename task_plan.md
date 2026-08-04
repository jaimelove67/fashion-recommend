# 阶段五：衣物图片和识别能力执行计划

# 阶段七：主要缺口收敛计划

# 阶段八：审查缺口修复计划

## 目标

按代码审查优先级修复推荐业务约束、外部视觉调用超时、对象存储删除一致性、历史查询规模边界、输入校验和前端高危依赖，并逐项验证。

## 阶段

- [completed] 1. 增加 LLM 推荐结果的类别互异校验与反例测试
- [completed] 2. 为视觉识别客户端接入连接/读取超时
- [completed] 3. 修复衣物图片删除的数据库与对象存储一致性策略
- [completed] 4. 为推荐历史增加分页并消除列表 N+1 查询
- [completed] 5. 补齐反馈/风格档案输入长度约束与测试
- [completed] 6. 升级 PostCSS 依赖并重新构建/审计
- [completed] 7. 完整回归验证并记录未覆盖环境

## 验收条件

- LLM 返回同类别衣物时回退规则引擎。
- 视觉识别请求受配置的连接和读取超时限制。
- 删除图片失败不会留下不可见的数据库/对象存储不一致，且失败行为有测试。
- 历史列表有明确分页边界，查询不会按记录逐条访问反馈和衣物明细。
- 超长反馈与档案字段返回标准 400，而不是数据库 500。
- `npm audit --audit-level=high` 不再报告当前 PostCSS 高危版本。

## 阶段八完成结果

- LLM 同类别衣物结果会回退规则引擎，并有反例测试。
- 视觉识别消费连接/读取超时配置，并有慢响应测试。
- 图片删除使用 Flyway V2 清理任务表，数据库事务提交后由调度器重试 MinIO 清理。
- 推荐历史支持分页和批量反馈/衣物查询，避免无界响应与列表 N+1。
- 反馈、风格档案和格式错误 JSON 均返回标准 400。
- PostCSS 升级到 8.5.25，npm audit、后端 44 项测试、前端构建和 Compose 配置均通过。

## 目标

在不伪造外部数据、不默认消耗付费模型的前提下，消除当前原型中可以由仓库自身解决的主要缺口：可信用户身份、可演进数据库、真实趋势适配入口、视觉识别用户授权和关键反例测试。

## 第一性边界

- 身份必须由服务端校验后的认证上下文产生，不能再相信客户端自填 `X-User-Id`。
- 没有获得授权的趋势数据源时继续明确返回开发样本；代码应支持配置真实源，但不能抓取或伪造第三方热度。
- 视觉模型调用同时要求服务端启用和用户本次上传明确同意；默认关闭不是缺陷，缺少同意仍调用才是缺陷。
- Redis 当前没有业务消费者，应从部署中删除，不为满足技术栈清单制造缓存逻辑。
- 数据库结构变更必须可追踪、可重复，并在 PostgreSQL 与测试数据库上验证。

## 阶段

- [completed] 1. 审计现有认证、趋势、迁移、视觉授权和测试契约
- [completed] 2. 实现认证接口、服务端身份上下文和前端登录会话
- [completed] 3. 引入 Flyway 版本迁移并删除未使用的 Redis 服务
- [completed] 4. 实现可配置趋势适配器和视觉识别显式授权
- [completed] 5. 扩充认证、隔离、上传授权、趋势降级和浏览器 E2E 测试
- [completed] 6. 更新 README、环境变量和生产边界说明
- [completed] 7. 执行后端、前端、Compose、完整栈与浏览器对抗验证

## 验收条件

- 未认证请求访问个人接口返回 401；登录后用户只能访问自己的衣橱、档案和推荐。
- 前端不再固定发送 `demo-user`，登录状态可恢复且退出后凭据被清理。
- Flyway 在全新数据库和已有表的数据库上都有明确升级路径，`schema.sql` 不再承担运行期迁移。
- Compose 不再启动没有消费者的 Redis。
- 配置真实趋势 JSON 源时返回校验后的实时快照；源不可用时响应明确标识开发样本。
- 未勾选 AI 识别时绝不调用视觉服务；勾选后仍受服务端能力开关约束。
- 自动化测试至少覆盖认证失败、跨用户隔离、趋势失败降级、视觉授权和核心成功闭环。

## 对抗性用例

- 伪造旧 `X-User-Id` 但没有有效令牌，应返回 401 而不是读取目标用户数据。
- 用户 A 使用自己的令牌读取用户 B 的推荐或图片，应返回 404。
- 趋势源返回额外字段、空列表或无效 URL 时，不得标记为实时数据。
- 服务端已启用视觉模型但上传未提供用户同意时，识别客户端调用次数必须为 0。
- 已存在旧表且没有 Flyway 历史表时，升级不得删除原有数据。

## 遇到的错误

| 错误 | 尝试次数 | 处理 |
| --- | --- | --- |
| 并行读取三个技能文件触发 Windows sandbox CreateProcessWithLogonW 1056 | 1 | 改为单个 PowerShell 进程顺序读取，成功 |
| 受管 shell 中 `git` 从 PATH 消失且常见安装路径不存在 | 1 | 暂用直接文件检查继续；最终验证时在外部已安装环境重试 Git 检查 |
| 受管 shell 中 `mvn` 不在 PATH，无法执行代理新增的趋势/Flyway 测试 | 1 | 完成全部编辑后使用允许的外部 Maven 环境统一验证，不重复当前 PATH |
| 查询 Node 子进程命令行被系统拒绝访问 | 1 | 通过 dist 时间戳确认前端构建发生，不再读取受限进程详情 |
| 首次 `mvn test` 的 Spring 上下文无法实例化趋势适配器 | 1 | 定位为双构造器缺少 `@Autowired`；Flyway/纯单元测试已通过，标注生产构造器后重跑 |
| 并行恢复读取因受管终端找不到 `git` 导致整组命令中断 | 1 | 改为逐项容错读取；业务验证继续，最终差异检查单独定位 Git |
| Python 环境缺少 `bcrypt`，无法生成演示密码哈希 | 1 | 不安装额外依赖，改用项目已缓存的 Spring Security BCrypt 实现 |
| Git HTTPS 推送三次被连接重置 | 3 | `gh api` 可达；运行 `gh auth setup-git` 重配凭据后推送成功 |
| Docker Windows 服务无权限启动，后台 Desktop 50 秒未就绪 | 2 | 通过 Docker Desktop GUI 启动并确认 Engine running；保留数据卷重建应用栈 |
| 后端镜像首次重建时 Maven Central 响应体中断 | 1 | 前端构建已通过；为 Maven 阶段增加 BuildKit 持久缓存与 3 次传输重试后再构建 |
| 后端缓存重建超过 10 分钟工具上限 | 1 | 不重复 Compose 构建；复用已填充缓存并改用 BuildKit host 网络绕过 Docker NAT |
| Playwright 对浏览器 multipart 请求调用 `postData()` 返回 null | 1 | 删除依赖内部缓冲的断言；保留默认未勾选、必填字段、MANUAL_CORRECTED 响应，并由后端 mock 验证模型零调用 |
| 批量历史查询的单参数 lambda 匹配两个 `JdbcTemplate.query` 重载 | 1 | 显式转换为 `RowCallbackHandler`，保持批量装配方案不变 |
| 输入约束测试链式 `putArray` 构造出数组根节点 | 1 | 改为显式 `ObjectNode`；同时发现并补齐格式错误 JSON 的统一 ApiResponse 处理 |
| outbox 多文件补丁缺少更新块分隔 | 1 | 拆分为服务、新类、迁移配置三个补丁，未产生部分写入 |

## 阶段七完成结果

- 认证、Principal 数据隔离、CSRF、Flyway、授权趋势源、视觉逐次同意和 Redis 移除已落地。
- 真实旧 PostgreSQL 数据卷完成 baseline 0 -> V1，数据保留；演示种子连续执行两次稳定且账号可登录。
- Maven Docker 测试 37/37、Playwright E2E 4/4、Compose 配置、前后端镜像构建和健康检查通过。
- 应用内浏览器完成桌面与 390x844 视口 QA，控制台无 warning/error，页面和弹窗无横向溢出或重叠。

---

## 目标

在阶段一至四的衣橱与推荐链路之上，建立可验收的图片闭环：上传衣物图片 -> MinIO 保存 -> 衣物元数据落库 -> 可选视觉识别 -> 识别失败后手动修正。

## 范围边界

- 保留现有趋势、天气、个人风格接口。
- 新增衣橱数据和推荐记录的持久化。
- 推荐算法先使用可测试的开发期规则引擎，暂不接入真实大模型。
- 使用现有 `X-User-Id` 作为开发期用户边界，暂不实现完整登录。
- 使用现有开发期 `X-User-Id` 作为用户边界，不扩展完整登录。
- 视觉识别采用可替换适配器；未配置视觉模型时必须明确返回待人工确认，不伪造识别结果。
- 图片通过后端代理地址读取，MinIO 桶保持私有，避免把内部对象地址直接暴露给浏览器。

## 阶段

- [completed] 1. 确认现状并设计最小接口契约
- [completed] 2. 增加数据库配置、表结构和后端领域模型
- [completed] 3. 实现衣橱 CRUD 与规则推荐闭环
- [completed] 4. 增加控制器测试和对抗性测试
- [completed] 5. 运行构建验证并整理交付说明
- [completed] 6. 增加 MinIO 图片存储、识别适配器和衣物元数据状态
- [completed] 7. 接入前端上传、识别失败修正和图片展示
- [completed] 8. 运行后端/前端/Compose 验证并记录对抗性结果

## 验收条件

- 可以为同一用户创建、查询、删除衣物。
- 没有衣物时，请求推荐会明确失败，而不是返回伪造结果。
- 有上装、下装、鞋履时，请求推荐会返回组合、理由和推荐记录 ID。
- 推荐可以标记保存，并能通过查询接口看到保存状态。
- `mvn test` 通过，已有趋势接口行为不回归。
- 支持图片类型和大小校验，图片对象写入 MinIO 私有桶。
- 上传后返回图片代理地址、识别状态及类别/颜色/风格元数据。
- 识别失败时衣物仍可保存为待人工确认，`PUT` 修正后可进入正常推荐链路。
- 未配置视觉服务时不伪造识别成功；MinIO 不可用时不写入半成品衣物记录。

## 阶段五完成结果

- 新增 `POST /api/v1/me/wardrobe/upload` multipart 上传、`PUT /api/v1/me/wardrobe/{id}` 手动修正和图片代理读取接口。
- MinIO 使用私有桶，数据库保存 `image_url`、对象键、识别状态和识别提示；推荐过滤 `NEEDS_MANUAL_REVIEW` 记录。
- 视觉识别适配器已接入百炼多模态协议，`BAILIAN_VISION_ENABLED=false` 时明确进入人工确认，不消耗外部服务。
- 后端 24 项测试通过，前端构建通过，Compose 配置通过；真实 Docker + PostgreSQL + MinIO 上传、图片读取、跨用户 404、修正和推荐历史读取通过。
- 浏览器页面加载、上传入口、待人工确认提示和“完善 -> 保存修正”状态通过，控制台无错误。

## 阶段五剩余边界

- 当前默认关闭视觉模型；启用自动类别/颜色/风格提取时设置 `BAILIAN_VISION_ENABLED=true` 并提供可用的 `DASHSCOPE_API_KEY`，模型默认为 `qwen-vl-plus`。

## 完成结果

- 4 个后端测试全部通过。
- 后端打包通过。
- Docker Compose 配置校验通过。
- 前端构建通过，未修改前端代码。

---

# 阶段六：答辩交付增强计划

## 目标

按用户指定顺序补齐答辩演示数据、开发期边界说明、真实百炼调用证明和前端 E2E 验证。每个阶段独立验证、提交并推送到远端，避免不同任务混入同一提交。

## 执行顺序

- [completed] 1. 准备 6-8 件衣物、2-3 条推荐历史、收藏反馈及识别失败后人工修正的可重复演示数据
- [completed] 2. 集中说明 X-User-Id、规则 fallback、趋势 demoMode 和视觉识别默认关闭的开发期边界
- [completed] 3. 增加真实百炼推荐/视觉识别调用记录、流程图和 prompt 约束说明
- [completed] 4. 增加并验证前端 E2E 脚本：打开页面、添加衣物、生成推荐、保存推荐

## 提交纪律

- 开始下一阶段前，上一阶段必须完成验收并已推送。
- 每个提交只包含当前阶段直接需要的文件及必要的规划记录。
- 真实 API Key 不写入仓库；调用记录必须脱敏，但保留时间、模型、请求约束、结果和可验证标识。

## 对抗性验收

- 演示数据脚本重复运行不得无限追加重复数据。
- 无百炼 Key 时仍能明确回退，不把规则结果伪装成模型结果。
- 证明材料不得包含密钥、完整认证头或可复用隐私数据。
- E2E 必须在空数据或可控数据状态下稳定运行，并验证保存后的状态，而不只验证按钮可点击。

## 阶段六完成结果

- 演示数据脚本连续执行两次后均保持 8 件衣物、3 条推荐、1 条收藏反馈和 1 条人工修正记录。
- 四项开发期边界已有独立说明文档和 README 入口。
- 已保存真实 qwen-plus 调用 ID、usage、脱敏结果、流程图和 prompt 约束说明。
- Playwright E2E 连续两次通过，覆盖页面新增衣物、生成推荐、收藏及历史接口 saved=true 复核。
- E2E 发现并修复 Docker 前端 8090 Origin 未被后端 CORS 允许的问题。
- 前端构建、后端 24 项测试、Compose 校验和密钥泄漏检查全部通过。

---

# 阶段九：答辩交付缺口收敛计划

## 目标

在保持当前功能闭环不变的前提下，收敛答辩交付中仅由仓库自身可以消除的口径与文档缺口，避免在答辩材料中声称未实现的能力。本轮以最小改动推进，不触碰业务 Java 代码、不读取 .env、不覆盖用户未提交改动。

## 阶段

- [completed] 1. 配置口径：收敛 Actuator 暴露、移除未使用的 Prometheus 依赖、移除未使用的 pgvector 口径、固定前端 vite 依赖版本
- [in_progress] 2. 推荐审计元数据：为推荐/识别调用补齐可核对、可脱敏的审计元数据
- [completed] 3. 离线天气：评估无网络环境下天气降级与演示路径
- [pending] 4. 研究评估材料：产出对齐论文/开题等研究要求的评估材料
- [pending] 5. 毕业设计文档同步：同步 README 与开发边界文档与当前实现一致
- [pending] 6. 完整验证：执行前后端构建、审计、Compose 校验与差异检查

## 验收条件

- 安全策略保持默认不对外开放，Actuator 仅暴露 health/info。
- 仓库不再声称使用 pgvector 或提供可访问的 Prometheus 指标地址。
- 前端构建依赖版本固定为 lockfile 当前解析结果，不再依赖 latest。
- 文档口径与当前实现一致，不出现未实现能力的声明。

## 阶段九配置口径完成结果

- 安全策略保持默认不对外开放，Actuator 仅暴露 health/info，仓库不再声称 pgvector 或 Prometheus 指标地址。
- 前端 `package.json` 与 `package-lock.json` 根 `dependencies` 均固定 `vite@8.1.4`、`@vitejs/plugin-vue@6.0.7`，不再依赖 `latest`，且不改变已解析包。

## 阶段九推荐审计元数据计划

用一次可核验、可脱敏的审计元数据收敛推荐来源的“可核对”口径。只有真实成功的百炼响应才写入 providerCallId 与三类 token；规则降级不伪装模型调用；旧历史记录保持为空而不是回填猜测值。

- [completed] 1. 新增 Flyway V3：为 recommendations 增加可空审计列 model_name、prompt_version、provider_call_id、prompt_tokens、completion_tokens、total_tokens、generation_latency_ms、fallback_reason，兼容 H2/PostgreSQL 与旧 V2 数据。
- [completed] 2. 百炼客户端解析真实响应 id、model 与 usage 三类 token，定义稳定 prompt 版本常量，缺失值不伪造。
- [completed] 3. 服务层保存稳定枚举式 fallback 原因，不持久化异常原文；生成耗时覆盖天气+模型+规则选择全流程且非负。
- [completed] 4. API 通过嵌套 generationAudit 返回元数据，旧前端只读 engine 保持兼容。
- [in_progress] 5. 测试：合法 LLM 元数据落库与返回、无 key/异常/非法 ID/同类别 fallback 原因、V2->V3 迁移保留旧数据且新列为空、usage 缺失时成功但 token 为空。
- [pending] 6. 同步 README、llm-integration-evidence、development-boundaries、findings、progress 与收集核对结果。

## 阶段九执行记录

- 前两次 Claude CLI 批处理均因 8 美元预算上限退出；第一次未产生任何文件修改，第二次已完成配置口径改动（Actuator 收敛、移除 pgvector/Prometheus 口径、固定前端 vite 版本）。本轮改为小批次逐项落地并逐项验证。

## 阶段九离线天气演示完成结果

- 新增默认关闭的开关 `app.weather.configured-demo-enabled` / `WEATHER_CONFIGURED_DEMO_ENABLED`（默认 `false`），以及静态快照字段 city、temperatureC、apparentTemperatureC、precipitationMm、weatherCode、windSpeedKmh。
- 仅当两个真实 provider（wttr.in、Open-Meteo）都失败且请求城市与配置城市（trim 后不区分大小写）一致时，才返回静态快照，`source=configured-demo`；前端把该 source 明确标注为“配置演示天气/非实时”，不冒充实时天气。城市未找到（NOT_FOUND）不被静态快照掩盖，仍返回 404。
- 配置校验采用与现有缓存配置一致的 fail-fast 方式：启用时任一字段缺失/非有限/温度不合理/降水风速为负，应用启动失败。
- 测试：`WeatherServiceTest` 覆盖默认关闭双失败仍 503、开启且城市匹配返回 configured-demo、城市不匹配仍 503、NOT_FOUND 仍 404、以及配置非法的反例。
- 文档：application.yml、docker-compose.yml、.env.example、README、development-boundaries、findings、progress 已同步，明确答辩可在 `.env` 显式启用但不能冒充实时天气。

---

# 阶段十：审计缺口收尾执行

## 目标

在最外层审计任务中收尾两项可复现交付：① 默认离线、不调用付费模型的研究评估协议与脚本；② 局部修订毕业设计 DOCX 生成器并重新生成，使其措辞与当前源码/迁移/测试/配置一致。本阶段只追加记录，不改写既有历史。

## 已完成

- [completed] 1. 只读审计当前工作区：git status/diff、task_plan/findings/progress、后端 API/DTO/迁移/测试、前端契约、README 与 docs、build_graduation_doc.py。
- [completed] 2. 实现离线评估协议与脚本（scripts/evaluation/）：纯标准库 Python，默认只读本地 JSON 并计算，不联网、不调用百炼或任何模型 API，不内置实验结论。
- [completed] 3. 运行评估反例测试（25 项 unittest 全部通过）与 fixture 评估（输出真实计算指标）。
- [completed] 4. 局部修订 build_graduation_doc.py 并重新生成 docx/基于大语言模型的智能穿搭推荐系统_毕业设计成果.docx。
- [completed] 5. 结构 QA（ZIP/段落/表格/关键术语）通过；视觉渲染 QA 因环境缺失 pdf2image 与 LibreOffice 未完成，已记录确切失败信息。
- [completed] 6. 记录本阶段真实状态到 task_plan/findings/progress，并做最终差异检查。

## 验收条件

- 评估脚本默认离线，seed 实际控制抽样；输出只含真实计算结果或明确未执行状态。
- 反例覆盖缺字段、重复 ID、衣橱外 ID、同类别、空数据；规则引擎契约与 LLM 输入边界分开定义。
- DOCX 不再出现 JWT、Redis、管理员后台、pgvector、旧表名与“待实测”；改为 Session+CSRF、Flyway V1-V3、后端 60 项测试、4 项 E2E、推荐审计字段与离线演示天气边界。
- 记录只追加不重写；所有失败命令与原因写入记录。
