# 当前发现

## 2026-08-23 最终复核补充

- 当前工作区代码已可编译：上传事务边界和天气 `NOT_FOUND` 降级语义由 Claude CLI 修改，天气组合反例已由 `WeatherServiceTest` 覆盖并通过。
- 依赖修复已回到最小范围：仅锁文件 `node_modules/nanoid` 解析为 3.3.18，未在 `package.json` 增加直接依赖；构建与 high/critical 审计均通过。
- Surefire 当前真实总数为 62 项（10 个报告相加），不是历史记录中的 60/61；生成器、进度和当前发现已同步为 62。
- 剩余可验证缺口只有上传失败事务的独立测试，以及 Docker Desktop 恢复后的完整栈 E2E；不能把历史 E2E 结果写成本轮通过。

## 2026-08-23 当前版本审查

- 仓库同步状态：当前分支 `codex/fix-major-gaps` 的 `HEAD=cddc878b6c3e2dfda6172ff8317d1899aee9893b` 与 `origin/codex/fix-major-gaps` 一致；工作区仍有两个未跟踪项：答辩技术文档 Markdown 与 `scripts/evaluation/__pycache__/`，它们不属于已上传提交。
- 可复现回归：后端 `mvn -q test` 通过，Surefire 汇总为 62 项测试、0 失败；前端 `npm run build` 通过（Vite 8.1.4，1785 modules）；离线评估测试 38 项通过；`docker compose config -q` 通过。
- 当前阻断：`frontend` 的 `npm audit --audit-level=high` 报告 `nanoid@3.3.16` 1 个 high severity，路径为 `vite -> postcss -> nanoid`，修复前不能继续声称依赖审计为 0。
- 依赖修复结果：Claude 将 `nanoid` 显式 pin 到 `^3.3.18`，lockfile 解析为 3.3.18；`npm audit --audit-level=high` 已返回 0 vulnerabilities，前端构建通过。该 pin 是为传递依赖安全修复增加的直接依赖声明，后续可评估改用 npm overrides 以减少 manifest 直接依赖。
- 当前环境限制：`docker compose ps` 无法连接 `dockerDesktopLinuxEngine`，Docker Desktop 未运行；本轮不能据此宣称真实 PostgreSQL/MinIO/浏览器 E2E 已重跑。
- 代码反例：`WardrobeService.upload` 先调用对象存储，再分别执行 `wardrobeRepository.create` 与 `updateImageUrl`，方法未声明事务；若第二步失败，catch 会删除对象但数据库记录可能已经提交，形成引用不存在对象的半成品记录。应补事务边界和失败测试。
- 文档反例：`build_graduation_doc.py` 原先把后端 61 项与 Playwright 4 项写成当前“全部通过”，并要求四类 Compose 容器均为 healthy；本轮 Docker 未运行，且 Compose 只为 PostgreSQL/MinIO 声明 healthcheck，已改为区分“本轮未执行”和历史证据。
- 天气反例：若 wttr.in 返回 `NOT_FOUND`、Open-Meteo 随后超时/错误，而配置演示天气已启用且城市匹配，`WeatherService` 现有逻辑只检查第二个 provider 的结果，可能错误返回 `configured-demo`；应保留任一 provider 已确认城市不存在的事实并返回 404，补充该组合测试。
- 文档反例：`build_graduation_doc.py` 原先把个人风格档案描述为“调用百炼 API、失败 stale”，但当前 `PersonalStyleProfileService` 只做本地确定性 development fallback；已改为当前实现，并把外部风格模型列为后续扩展。
- 代码修复执行结果：Claude 已在 `WardrobeService.upload` 加入事务边界，并让补偿删除失败不覆盖原始异常；Claude 已修复天气主 provider `NOT_FOUND` 与备用错误组合不得降级为 configured-demo。新增天气反例测试已通过；上传失败回滚尚未有独立测试，需在 Docker/测试隔离环境补充。
- 处理边界：上述依赖与上传一致性由 Claude 子代理修改；本主代理只同步审查文档、规划记录和最终验证，不改写未跟踪的用户答辩 Markdown。

## 2026-08-04 审计缺口收尾发现

- 推荐审计元数据与 V3 迁移、配置演示天气、Prometheus/pgvector 口径修正均为既有 dirty worktree 变更，已保留未动；surefire 报告（20:13）确认后端 60 项测试全通过，E2E spec 4 项。
- build_graduation_doc.py 原有大量与当前实现不符的措辞：JWT、Redis、管理员后台、pgvector、MyBatis-Plus、Spring AI、Jsoup/抖音、旧表名（sys_user/garment/outfit_plan/favorite_outfit）与“待实测”测试模板；已全部改为当前真实边界（Session+CSRF、Flyway V1-V3、JDBC、60 项测试、4 项 E2E、推荐审计 generationAudit、configured-demo 离线天气）。
- 规则引擎可按五大类别各取一款，最多 5 件；2-4 件数量边界属于 LLM 输入边界，不属于规则引擎输出契约。评估协议把这两类边界分开定义，避免把 5 件规则输出误判为非法。
- 本机无 LibreOffice（soffice）与 pdf2image，Documents 渲染器无法做 DOCX 视觉渲染；已记录确切失败信息并明确“未完成视觉渲染 QA”，只做结构 QA。
- 评估脚本 seed 实际控制 baseline 子集抽样与 LLM 结果抽样；相同 seed 报告逐字节一致，不同 seed 得到不同子集。
- `final_recommendation` 必须与 `validate_llm_result` 共用同一输入边界，否则“重复 ID 的非法结果”会在最终推荐中出现重复 ID；已改为复用校验结果。

## 2026-08-04 离线天气演示发现

- 天气原本只依赖两个真实 provider（wttr.in、Open-Meteo），无网络时两者都失败会直接返回 503，答辩演示无法在离线环境展示天气驱动的推荐路径。
- 新增默认关闭的开关 `app.weather.configured-demo-enabled` / `WEATHER_CONFIGURED_DEMO_ENABLED`（默认 `false`）：仅当两个真实 provider 都失败且请求城市与配置城市（trim 后不区分大小写）一致时，才返回静态快照，`source=configured-demo`，前端明确标注“配置演示天气/非实时”，绝不把静态数据冒充实时天气。
- 城市未找到（NOT_FOUND）不被静态快照掩盖：`WeatherService` 先判断 fallback 的 NOT_FOUND 再判断 configured-demo，所以未找到城市仍返回 404。
- 配置校验采用与现有缓存配置一致的 fail-fast：启用时任一字段缺失/非有限/温度不合理/降水风速为负，应用启动失败；默认关闭时不校验携带的配置值。
- 推荐理由文案对 `configured-demo` 使用“天气”而非“实况”，避免生成文本把演示数据表述为实时天气。

## 2026-08-04 推荐审计元数据发现

- 推荐来源此前只记录 engine，无法从数据库核对真实模型调用与降级原因；本轮新增 Flyway V3 审计列，只有真实成功的百炼响应才写入 provider_call_id 与三类 token，规则降级只写稳定枚举式 fallback_reason，不持久化异常原文。
- 生成耗时使用 `Duration.between(startedAt, Instant.now())` 覆盖天气、LLM 与规则选择的完整计算时间，并 `Math.max(0, ...)` 保证非负。为不把外部天气/LLM 调用留在数据库写事务中，`generate()` 改为非事务，仅用 `TransactionTemplate` 包裹 create+addItems 两个写操作；读取与外部调用全部在写事务之外。残余风险：召回/读取与写事务不做强制隔离，并发写同一用户仍由数据库唯一约束兜底。
- API 通过嵌套 `generationAudit` 返回元数据，engine 仍保留在顶层，旧前端只读 engine/summary/reason 不受影响。
- 前两次 Claude 批处理均因 8 美元预算上限退出：第一次未产生文件修改，第二次已落地配置口径改动（Actuator 收敛、移除 pgvector/Prometheus 口径、固定前端 vite 版本）。本轮改为小批次逐项落地。

## 2026-08-04 审计发现

- README 与 docker/README 声称的 Prometheus 指标地址实测返回 401：安全配置只放行 Actuator 的 health/info，`/actuator/prometheus` 与 `/actuator/metrics` 均被拒绝，且当前没有 Prometheus 消费者。
- 迁移文件与仓储查询中不存在任何 vector 列或向量查询，pgvector 未使用；Compose 的 pgvector 镜像与 `01-extensions.sql` 的 `CREATE EXTENSION vector` 均无实际消费者。
- `frontend/package.json` 将 `@vitejs/plugin-vue` 与 `vite` 声明为 `latest`，未固定版本；package-lock 当前解析为 `@vitejs/plugin-vue@6.0.7` 与 `vite@8.1.4`。

## 2026-08-01 审查发现

- `RecommendationService` 在规则候选阶段要求至少两个类别，但 LLM 结果只检查数量、重复 ID 和衣橱归属，同类别结果可绕过业务约束。
- `BailianGarmentRecognitionService` 使用无超时的默认 `RestClient`，没有消费项目已有的百炼连接/读取超时配置。
- `WardrobeService.delete` 先删除 MinIO 对象再删除数据库记录，数据库删除失败会留下指向不存在图片的衣物记录。
- 推荐历史一次加载全部记录，并为每条记录分别查询反馈和衣物，存在无分页和 N+1 查询。
- 反馈与风格档案 DTO 缺少与数据库列一致的长度约束，超长输入可能在数据库层变成 500。
- `frontend/package-lock.json` 锁定 PostCSS 8.5.16；npm audit 报告该版本存在 1 个 high 漏洞。

## 2026-07-22 收尾复核

- 已确认 `SecurityConfig` 使用 BCrypt、JDBC 用户表、HttpOnly Session 与 cookie/header CSRF；除趋势、天气、CSRF、登录和注册外，其余接口要求认证。
- 已确认 Flyway V1 同时支持全新建表和旧表补列，配置为 baseline version 0；迁移测试覆盖旧数据保留和第二次执行不重跑。
- README 与开发边界文档仍残留 `X-User-Id`、`demo-user`、Redis 等旧口径，必须在交付前更新。
- 当前受管 PowerShell 的 PATH 中没有 Git；这只阻碍差异命令，不影响源文件读取、构建和容器验证。
- 对抗搜索发现 `docker/postgres/demo/seed.sql` 仍只为 `demo-user` 写业务数据，却没有创建可登录账号；认证上线后这些演示数据无法从 UI 访问，属于答辩路径回归。
- 新版 Playwright 已改为 UI 注册真实账号并共享 Session/CSRF，覆盖未认证 401、刷新续期、退出清理、AI 默认未授权、人工字段必填、反馈持久化和 390x844 布局。
- 当前修复已提交 `fb386ae` 并推送到 `codex/fix-major-gaps`，草稿 PR 为 GitHub #1。
- Docker Desktop 恢复了旧镜像组成的历史栈，其中仍有孤立 Redis 容器；新 Compose 已删除 Redis，重建时需 `--remove-orphans`，但不能删除 PostgreSQL/MinIO 数据卷。
- 真实旧 PostgreSQL 数据卷已由 Flyway 成功建立 baseline 0 并迁移到 V1，日志只显示既有表/列跳过，应用健康检查为 UP。
- 修复后的种子连续执行两次均保持 8 件衣物、3 条推荐、1 条收藏反馈和 1 条人工修正；`demo-user / demo-password-2026` 可经 Session/CSRF 登录并读取 8 件衣物。
- 新 Compose 启动后旧 Redis 孤立容器已移除，PostgreSQL 与 MinIO 数据卷未删除。
- 完整 Playwright E2E 为 4/4 通过；Maven Docker 容器中的 Surefire 报告为 8 个套件、37 项测试、0 失败、0 错误、0 跳过。
- 应用内浏览器桌面与 390x844 视口验收通过：页面非空、无框架错误覆盖层、登录和衣橱交互正常、控制台无 warning/error；移动端 `scrollWidth == viewportWidth == 390`，上传对话框完整位于视口内。

## 2026-07-22 主要缺口收敛

- 当前 26 项后端测试、前端生产构建和 Compose 配置已通过；历史 Playwright 状态为 passed，但当前 Docker daemon 尚未运行。
- 核心业务已经落库并有 LLM 结果白名单校验；本轮重点不是扩展推荐算法，而是修复身份来源、数据库演进和外部能力启用契约。
- `X-User-Id` 可由客户端伪造，属于必须修复的真实安全缺口。
- 趋势没有可用的已授权第三方凭据；正确修复是实现可配置适配器并保留显式开发样本降级，而不是抓取或编造数据。
- 视觉识别默认关闭符合成本与隐私约束；需要补的是每次上传的用户显式授权。
- Redis 在 Compose 中存在但没有任何业务消费者；最少改动是删除该服务及文档声明。
- 当前 `schema.sql` 同时承担建表和增量 ALTER，缺少版本历史；计划迁移到 Flyway，并保留旧数据库基线升级路径。
- 所有个人控制器都直接信任 `X-User-Id`，图片接口甚至接受 `userId` 查询参数；认证必须覆盖图片读取，不能只替换 JSON 请求头。
- 当前 CORS 只允许 `GET`、`POST`，而前端真实使用 `PUT`、`DELETE`；跨端口开发模式下衣物修正与删除会被预检拒绝。
- Bearer Token 不能由普通 `<img>` 自动携带；认证实现需同时设计受保护图片的浏览器加载方式，不能保留可伪造查询参数。
- `recommendation_items.recommendation_id` 与 `wardrobe_item_id`、`recommendation_feedback.user_id` 等高频 JOIN/过滤列缺少完整索引；迁移时应补齐外键侧与用户时间排序索引。
- `RecommendationFeedbackRepository.save` 采用先 UPDATE 后 INSERT，在并发首次反馈时可能冲突；迁移到 PostgreSQL 原生 UPSERT 需要兼顾 H2 测试方言，或通过事务/唯一冲突测试验证现状。
- nginx 与 Vite 已把 `/api` 代理成浏览器同源请求，因此 HttpOnly Session Cookie 比前端持有 JWT 更符合当前架构；它还能让受保护 `<img>` 自动携带身份。
- Spring Security 内置 form login 可保留默认 session fixation 防护；SPA 需要独立 `GET /api/v1/auth/csrf`，写请求发送 `X-XSRF-TOKEN`。
- 现有 Playwright 通过请求头路由覆盖隔离用户；认证后应改为先注册/登录，并复用同一浏览器上下文 cookie。
- 趋势已有 `TrendSourceAdapter` 接口但没有实现，`TrendService` 也没有注入它；可在不改 API 响应契约的情况下增加配置式 JSON 适配器和失败降级。
- `WardrobeService.upload` 当前无条件尝试调用识别服务；需要增加 `allowAiRecognition`，未同意时调用次数必须为零，并从图片 URL 删除用户查询串。
- 前端初始化在认证确认前并发加载全部私人数据；改造后必须先完成 CSRF/会话检查，并在退出时清空全部私人状态，防止旧响应回写。
- Session Cookie 方案允许现有原生 `<img>` 同源加载，无需把令牌暴露给 JavaScript；前端只读取并发送 CSRF token。
- 上传表单只有 `uploadIntent` 视觉状态，没有模型调用同意状态；新增同意 checkbox 后，未同意时衣物名称/类别/颜色仍应保持必填。
- hash 导航仅监听 `popstate` 而非 `hashchange`，属于独立的低风险导航缺陷，可在会话重构时一并补齐且有直接行为依据。
- 后端认证已采用 Spring Security `JdbcUserDetailsManager`，认证表为 `app_users`/`app_authorities`，用户名即现有业务 `user_id`；因此无需迁移所有业务表主键或引入不必要的用户 UUID 映射。
- 安全配置已匿名放行 CSRF、注册/登录、趋势、天气和 health/info，拒绝其他 Actuator，并把未认证/拒绝响应统一为 JSON `ApiResponse`。
- 图片接口已经只从 `Principal` 取身份且 URL 不再包含 `userId`；旧数据库里带查询串的历史 URL 仍会被控制器忽略查询参数，不再形成越权。
- 前端会话请求在写操作统一附加 CSRF，403 时只刷新并重试一次；登录后主动刷新轮换的 token，符合 Spring Security session fixation 行为。
- 前端用 `sessionVersion` 阻止退出/换用户后的旧私人请求回写，并在 guest 状态销毁业务视图；这个反例已在代码结构中处理。
- 新衣物表单默认类别改为空值，未同意 AI 时名称/类别/颜色均由 HTML required 强制；同意后才允许留空交给模型，避免默认“上装”覆盖识别结果。
- Flyway 升级测试模拟现有非空 schema，验证 baseline 0 执行 V1、保留旧数据、补默认列与索引，并验证二次 migrate 不重跑。


## 阶段六初始发现

- Git 仓库完整，main 已配置并跟踪 origin/main；无需重建仓库。
- 根 README 已包含基础启动和能力边界，但尚未提交。
- 演示数据需要可重复生成，不能只依赖某次本地数据库快照，否则答辩机器无法复现。
- 真实百炼证明材料必须与密钥分离；仓库只保留脱敏记录和生成方法。
- E2E 需要真实浏览器完成业务闭环，且应验证推荐已保存，而不是只做页面冒烟。

## 演示数据设计发现

- 现有表没有演示数据唯一键，直接追加会导致重复；种子应在事务内只清理 demo-user 后重建固定数据。
- 推荐历史已经保存衣物快照，可以稳定构造 3 条历史而不依赖实时天气服务。
- 识别修正没有独立事件表；种子可真实执行 NEEDS_MANUAL_REVIEW 插入再更新为 MANUAL_CORRECTED，并保留脚本过程作为可复现证据。
- 答辩数据不应伪装成真实模型结果，预置推荐统一标记为 development-rule-v1；真实模型证明留给后续独立阶段。
- 风格档案列表字段以 JSON 数组文本存储，种子必须按该格式写入。
- 当前衣橱界面把 MANUAL_CORRECTED 与普通手工记录都显示为“可推荐”；为让答辩现场可见人工修正结果，应给该状态一个明确但非警告性的标签。
- 当前没有运行中的 Compose 服务，种子入口需要先检查数据库表是否已由后端初始化并给出明确提示。
- 实际 PostgreSQL 验证证明事务删除和重建可重复执行，第二次运行没有产生重复数据。
- 公开 API 返回的收藏、反馈和 MANUAL_CORRECTED 状态与数据库校验一致，前端无需依赖私有 SQL 结构读取演示数据。
- GitHub DNS 解析和 ICMP 正常，但 github.com 的 TCP 443 连接失败；本机没有配置 Git HTTP 代理，推送阻塞属于当前网络出口问题。
- ssh.github.com:443 和 github.com:22 均可连接，本机已有 GitHub SSH 密钥，可用 SSH 作为 HTTPS 推送的备用路径。
- 本机 Clash Verge 的 HTTP 代理监听 127.0.0.1:7897，但 Git 未配置代理；可对单次 Git 命令传入该代理恢复 HTTPS。

## 开发期边界文档发现

- 四项边界都已在代码中显式可观察：用户头写在请求层，推荐响应带 engine，趋势响应带 demoMode，衣物带 recognitionStatus。
- X-User-Id 只承担原型期数据分区，不是认证；仓储查询仍按 user_id 限制数据，跨用户读取测试返回 404。
- LLM fallback 不是静默伪装：模型结果先校验字段、长度、衣物 ID 和数量，失败后响应 engine 为 development-rule-v1。
- 趋势 demoMode 已被首页、趋势页和通知区展示为开发样本，避免把内置样本当实时平台数据。
- 视觉默认关闭时不会猜测标签；记录进入 NEEDS_MANUAL_REVIEW，且被推荐候选过滤，人工修正后才恢复可用。

## 百炼证明材料发现

- 当前 Docker 后端已配置 DASHSCOPE_API_KEY，文本模型为 qwen-plus；视觉模型开关仍为 false。
- 推荐 prompt 把场合、天气、风格档案和衣橱都声明为数据而非指令，要求只选当前 wardrobe 中 2-4 个互不重复 ID。
- 模型输出必须是只含 summary、reason、itemIds 的 JSON；服务端还会复验字段集合、长度、数量、重复 ID 和跨用户衣物 ID。
- 视觉 prompt 限定 name、category、color、style 四字段，类别只能来自五个枚举；本阶段保留真实文本推荐调用即可，不改变默认关闭的视觉边界。
- 第一次通过业务 API 发起的真实推荐发生了显式 fallback；证明材料必须等待 engine=llm，不能仅凭已配置 Key 判断调用成功。
- 宿主机通过本地代理成功调用百炼 qwen-plus，provider call ID 为 chatcmpl-66fad4c4-9e16-9dbf-b200-e4a4af795e67，usage 为 617 + 226 = 843 tokens。
- 返回结果只含 summary、reason、itemIds；4 个 ID 互不重复且全部解析到 demo-user 当前衣橱。
- 证明材料采用脱敏 JSON 加说明文档，既保留可核验 ID 和结果，又不写入 API Key、Authorization 或完整环境。

## 前端 E2E 发现

- npx、Node 24 和 Playwright 1.59.1 可用，可以使用正式 Playwright Test 文件而不是临时点击脚本。
- 衣橱表单已有可访问标签：单品名称、类别、颜色、风格，提交按钮为“加入衣橱”。
- 推荐表单已有场合、城市、风格提示标签，生成和收藏按钮都有稳定可见文本。
- 前端固定发送 demo-user；E2E 可在浏览器路由层把 X-User-Id 改为独立测试用户，避免污染答辩数据且无需修改产品身份逻辑。
- 推荐至少需要两类衣物；测试可用 API 为独立用户预置下装和鞋履，再由页面新增上装，保证“添加衣物”仍由真实 UI 验证。
- 前端尚无 Playwright 配置、E2E 目录或测试依赖，需要新增最小配置、单个 smoke spec 和 npm 命令。
- Playwright CLI 真实快照确认主导航“衣橱”、页面“添加单品”和“人工确认”均以语义角色暴露，可优先使用 getByRole/getByLabel。
- CLI 会在根目录生成 .playwright-cli 临时快照，提交前应纳入忽略规则；正式测试还需忽略 test-results 和 playwright-report。
- Playwright Test 成功收集到 1 个 Chromium spec，说明配置和测试语法有效。
- 本机 1.59.1 缓存只有 ffmpeg/winldd，CLI 的临时浏览器不被 Playwright Test 识别；浏览器安装需显式使用本地代理。
- 两次 shell 超时只结束外层命令，遗留 4 个 playwright install chromium 子进程并持有 ms-playwright/__dirlock；必须先清理明确匹配的孤儿安装进程。
- 代理下载进程虽已连接但长期不落盘；本机已有 Chrome 150，可将 Playwright 项目固定到官方 chrome channel，去掉答辩前的大体积浏览器下载依赖。
- 首次 E2E 页面 POST 衣橱返回 403，但不带 Origin 的相同 Nginx 请求返回 200；Compose 覆盖的 CORS_ALLOWED_ORIGINS 仅含 8088，缺少实际前端 8090，是浏览器写请求失败的根因候选。
- 带 http://localhost:8090 Origin 的反例稳定返回 403 Invalid CORS request；加入 FRONTEND_PORT 来源并重建后，同一请求返回 200。
- 修复 CORS 后完整 E2E 首次通过，真实完成页面新增衣物、推荐生成、收藏以及历史 saved=true 复核。
- E2E 连续第二次通过，finally 清理逻辑把测试用户衣橱记录恢复为 0；推荐历史使用独立测试用户，不影响 demo-user 答辩数据。

## 代码现状

- 前端已经是 Vue/Vite 单页，当前调用 `/api/v1/trends` 和 `/api/v1/me/style-profile`。
- 后端是 Spring Boot 3.4.2，已有趋势、天气、个人风格三个开发期接口。
- `TrendService` 返回硬编码的三条趋势数据。
- `PersonalStyleProfileService` 使用 `ConcurrentHashMap`，重启后数据丢失。
- `docker-compose.yml` 已经提供 PostgreSQL、Redis、MinIO，但后端尚未配置数据库依赖和表结构。
- 当前后端只有趋势控制器测试，缺少衣橱和推荐链路测试。

## 2026-09-07 修复决策

- 后端未配置时 Secure Cookie 为 true；本地 Compose/.env.example 与源码启动命令显式使用 false，HTTPS 部署必须覆盖本地默认值。
- 分页仓储已用 long 计算 OFFSET，但服务层 hasNext 曾先做 int 加法；现在用 long 完成下一页计算，并要求 page * size <= 1,000,000，阻止无界深分页。
- 固定本地 postgres:16-alpine 已解析的 digest，避免重建时意外切换数据库镜像。完整栈启动已通过，镜像 ID 与原容器一致，原数据卷保留。
- Node engines 与 Vite 8 对齐为 ^20.19.0 || >=22.12.0；保留既有 nanoid 3.3.18 锁文件修复。
- Docker 文档与当前服务、loopback 端口和 health/info 暴露范围同步。
- 对抗复核补充：总记录数超过偏移边界时，hasNext 也必须停止，不能引导前端访问会返回 400 的下一页；用模拟 1,000,051 条记录的服务测试覆盖。
- 最终验证：Maven 66/66、离线评估 38/38、Playwright 4/4、前端构建、零漏洞审计和 Compose 配置/重建/健康检查均通过。Cookie 的生产默认值与本地覆盖经过配置测试，本轮实际部署验证为本地 HTTP。

## 第一性判断

真正必须先解决的是推荐链路的数据契约和可重复验证，而不是继续扩展展示页面。推荐结果必须来自用户衣橱，衣橱为空时不能用默认数据伪造成功。

## 设计决策

- 用 JDBC + PostgreSQL/H2 测试数据库，避免为本次 MVP 引入完整 ORM 抽象。
- 推荐生成时落库，保存动作只更新 `saved` 状态。
- 规则引擎按衣物类别和场合生成稳定结果，并在响应中标明开发期规则来源。

## 测试发现

- 第一次测试失败的根因是测试配置没有声明原有的 `app.cors` 和 `app.bailian` 属性；已补齐测试专用属性，避免把配置覆盖行为当成自动深度合并。
- 新增业务测试首次编译失败的根因是 Java 测试 JSON 字符串引号未转义；已改为文本块输入。
- 对抗测试发现删除衣物会破坏推荐历史，因此推荐项现在保存生成时的单品快照，外键仅用于追踪当前衣物。

## 阶段五发现

- MinIO 已在 Compose 中运行，但原后端没有 SDK、桶初始化、上传或读取接口；需要后端代理私有对象，不能让前端直接访问 Docker 内部地址。
- 原 `wardrobe_items` 的名称、类别、颜色为非空字段；上传识别失败仍要能保存，因此使用明确的 `待补充衣物`、`待识别` 占位值，并增加 `recognition_status`，修正后再进入推荐候选。
- 视觉识别配置为空或关闭时，适配器返回空结果而不是猜测元数据；配置视觉模型后同一上传契约可接入真实多模态接口。
- 真实 PostgreSQL 验证发现推荐历史读取不能使用 `getObject(column, Double.class)` 读取 `numeric`；已改为 `getBigDecimal` 后转换，避免 H2 与 PostgreSQL 行为分叉。
- Compose 首次重建因一次性 `minio-init` 等待超时，改为拆分 `docker compose build` 与 `docker compose up -d --no-deps`；这是启动验证流程问题，不是服务启动失败。
- 前端容器初始仍是旧镜像，浏览器页面与源码不一致；重建前端后确认 `8090` 页面已包含图片上传和人工修正入口。

## 2026-09-08 网页采集需求发现

- 用户提供的 SnapWC 中文首页说明其插件会在当前网页自动检测视频、直播、音频、字幕和图片，网站端则通过粘贴链接返回可下载资源；页面没有声明可供本项目复用的趋势数据 API。
- 因此本项目的最小可行复用点是公开网页元数据采集：标题、摘要、JSON-LD 的 Article 字段、OpenGraph 图片、关键词/标签、发布时间和规范化来源 URL。
- 任意公开网页通常没有可信统一的热度字段；网页适配器必须使用单独的 `scoreLabel=来源页信号评分`，不能把基于新鲜度/内容完整度的派生分数显示为平台热度。
- 网页采集只能处理服务端配置的、用户有权访问的公开 URL；不新增前端任意 URL 抓取入口，避免 SSRF、跨站滥用和把第三方页面当作指令执行。

## 2026-09-08 网页采集验证发现

- 首轮后端编译发现 Java 字符串方法和 Spring 请求工厂 API 使用错误；已改为 `toLowerCase(Locale.ROOT)`，并通过 `SimpleClientHttpRequestFactory` 子类关闭重定向。
- 首轮采集器测试因 Mock HTML 未声明 UTF-8 导致中文乱码；测试响应改为 `text/html; charset=UTF-8` 后通过，属于测试夹具问题，不是生产解析失败。
- 最终后端 75/75、前端生产构建和 `docker compose config -q` 通过；本轮没有设置真实 `TREND_WEB_URLS`，所以不把线上第三方网页成功率误报为已验证。

## 2026-09-08 风潮 Top 10 画廊设计发现

- 当前 `TrendView.vue` 已有趋势详情和三条样本卡片，但没有按热度取 Top 10 的展示层；`TrendService` 的开发降级 feed 只有 3 条。
- 现有 `TrendItem` 已包含画廊所需的 id、标题、标签、heatScore、来源和图片字段，不需要新增前端专用 DTO。
- 最小可演进方案是：后端开发 feed 返回 10 条占位样本；前端用 `sort((a,b) => heatScore)` + `slice(0, 10)` 形成今日画廊；真实抓取适配器后续直接替换来源。
- 交互应至少支持点击卡片、前后切换和键盘方向键；在移动端避免把 10 张卡片横向撑出页面，使用可切换的单卡焦点和隐藏的两侧卡片。

## 2026-09-08 风潮 Top 10 画廊验证发现

- 接口真实响应结构为 `data.items`；运行栈已确认返回 10 条开发样本，`demoMode=true`，热度 96、93、91、89、87、84、82、79、76、73，满足降序 Top 10 约束。
- 桌面视觉复核确认舞台为浅灰背景、中心卡片最高、两侧逐级下沉和向内倾斜，隐藏远端卡片只作为可访问的 10 项数据，不破坏参考图的 7 张可见构图。
- 移动端 390px 下舞台保留中心焦点和两侧局部卡片；`document.documentElement.scrollWidth` 与布局宽度一致，未出现横向溢出。
- 浏览器回归 0 个页面错误；唯一警告是测试浏览器此前拒绝过地理位置权限，与画廊渲染无关。
- 对抗条件均在实现中有边界：`slice(0, 10)` 限制数据量，空数据直接显示状态，图片失败走已有 fallback，`prefers-reduced-motion` 关闭过渡。

## 2026-09-08 风潮画廊视觉与自动轮播精修发现

- 新参考图的视觉重点是图片本身；因此保留上方数据说明，但去掉画廊内部的位次、焦点标题、操作提示和数字导航，避免“图片展示区”变成控制面板。
- 透明舞台不会改变页面背景；用卡片实际宽度加间隙计算 `galleryShift`，比按 viewport 固定偏移更能同时适应大屏和移动端。
- 自动轮播必须和页面趋势详情解耦：定时切换只写 `galleryIndex`，用户点击卡片才调用既有 `selectTrend`，避免阅读页面时主详情每 1 秒跳变。
- 轮播生命周期需要覆盖组件卸载、页面不可见、悬停和聚焦；`prefers-reduced-motion` 关闭定时器，保留卡片按钮和键盘区域的访问能力。
- 视觉 QA 初次移动截图发现加大偏移后仍存在卡片重叠（P2）；改用移动卡片宽度加 16px 间隙后，最终截图显示中心卡片与两侧卡片之间有明确留白，且根布局无溢出。

## 2026-09-09 登录/注册切换动画初始发现

- `frontend/src/views/LoginView.vue` 目前用 `mode` 控制登录/注册，Tab 只有静态 `border-bottom`，标题和辅助文案直接替换，确认密码通过 `v-if` 瞬时插入；最小改动点集中在该组件。
- 当前认证契约依赖 `username`、`password`、`confirmPassword` 的 name/autocomplete/pattern/min/max/required，提交仍应复用现有 `app.login`/`app.register`，不引入新的认证状态。
- 当前错误提示已是左侧 3px 珊瑚边线，应保留；输入已有 `:focus-within` 金棕边框/光圈，但可收敛为更有呼吸感的金棕色 focus ring。
- 仓库现有媒体只有三张穿搭 JPG 与 logo，没有短视频；本轮不引用不存在的 MP4，不把静态图冒充视频。保留静态视觉封面并将媒体容器做成未来可接入 video/poster 的响应式边界。
- 当前移动端 `max-width: 680px` 完全隐藏 `.auth-visual`；要满足短首屏方向，应改为默认 180–220px 的媒体封面区，并在 `max-height` 更小时隐藏媒体以优先保障表单。

## 2026-09-09 登录/注册切换动画实现发现

- 标题使用 Vue `Transition mode="out-in"`，出场为 `translateY(-9px)`，入场为 `translateY(10px)`，统一 240ms；标题容器预留高度，避免登录/注册切换时面板内容整体漂移。
- 确认密码位于固定 75px 槽位，内部以 `max-height + opacity + scaleY` 从上向下展开；用户名辅助说明也始终保留高度，避免注册态增加第二次布局跳动。
- Tab 辅助文案通过 `aria-hidden` 保持视觉表达但不改变原有“注册/登录”精确可访问名称；激活缝线为 2px `--accent-strong`，圆点沿底线 240ms 扫过。
- 本地开发服务在未启动后端时会返回“无法建立安全会话 (502)”的既有安全会话错误；该错误仍保留珊瑚左边线，不属于本轮动效回归。
- 1440px 浏览器截图已确认桌面媒体与右侧表单比例、宋体标题和墨黑按钮视觉稳定；注册截图确认确认密码字段和新提交文案正常出现。
- 390×844 截图显示移动端顶部静态封面约 196–220px，表单紧接媒体区且无横向溢出；390×650 时媒体自动隐藏，品牌、Tab、注册字段与提交按钮仍完整可用。
- 连续切换后测得登录/注册两态 `.auth-submit` 的 `getBoundingClientRect().top` 均为 783px，确认槽位均为 75px；切换完成后 `document.activeElement` 为 `username`。
- `prefers-reduced-motion: reduce` 实测激活线立即成型、圆点 `display:none`、标题无 transform，确认密码和焦点仍正常；浏览器唯一错误来自未运行后端返回的两个 502 请求，未出现 Vue/CSS 运行时错误。
- 快速“注册→登录”反例中，确认密码离场 20ms 内固定槽位已为 `aria-hidden=true`，离场 input 已禁用；300ms 后节点移除且用户名重新获得焦点，避免旧必填字段阻塞登录。

## 2026-09-09 移除重复认证标题

- 按界面反馈删除右侧“欢迎回来 / 建立你的账户”及“登录知己 / 注册知己”标题组；Tab 的两条风格说明仍保留，避免登录/注册状态失去语义提示。
- 删除标题组后，认证面板不再引用已不存在的 `login-title/register-title`，改用 `aria-label="账户认证"`；同步移除无使用者的标题过渡 CSS。

## 2026-09-08 自动轮播节奏与图片悬停暂停发现

- “每秒轮播一次”对应实现为 `setInterval(..., 1000)`；CSS 过渡仍为 720ms，留有足够的视觉过渡时间且不引入额外动画依赖。
- 暂停边界应落在 `.gallery-card` 图片卡片，而不是整个舞台：事件委托可让进入任意图片都暂停，并在卡片之间移动时保持暂停。
- 浏览器交互反例验证：将悬停、等待和读取拆成多个 CLI 命令会被 1 秒轮播的命令延迟干扰；合并为单次脚本后，悬停状态稳定，移出后能恢复轮播。

## 2026-09-09 毕业设计文档图表与管理员模块初始发现

- 官方模板为 A4 纵向、9 个分节；第 1 节上边距 1.38 英寸，其余分节四边约 0.98 英寸。当前成果文档与模板分节、页码和主要样式保持一致。
- 模板第 2 章的“机关单位、领导、商城”等内容是示例占位，不属于智能穿搭系统；本次只沿用其标题层级、表格和图题格式。
- 当前成果文档第 2 章已有普通用户流程图和普通用户用例图，已有 7 张 11x2 用例分析表（UC-01 至 UC-07）；管理员角色和对应表格尚未出现。
- 当前代码 README 明确第一期不包含后台管理，因此文档中的管理员内容采用平台级治理/运维的设计扩展边界：账号状态、授权趋势源、脱敏审计、运行状态和图片清理任务，不宣称普通用户后台数据已在现有前端实现。
- 现有流程图为单行五节点，无法表达天气回退、候选判断、模型降级和结果校验；重绘为四泳道跨职能流程，保留核心推荐闭环并显式标注异常路径。
- 现有用例图所有关联线从同一汇聚点发散，图内文字较小；普通用户图与管理员图分别按账户、衣橱/推荐、运营治理分组，减少交叉线并保留 UML 角色—用例边界。
- Word 只读 PDF 导出成功，模板 38 页、当前成果 39 页；规范渲染脚本因运行时缺少 `soffice.exe` 不能直接执行，预览使用 Word 临时 PDF + bundled Poppler PNG，源文件未被保存。

## 2026-09-09 毕业设计文档管理员模块与图表完成发现

- 输出文档补充了管理员平台治理边界：管理员登录与权限校验、用户账号状态治理、授权趋势源维护、运行监控、脱敏审计、图片清理任务和异常数据处理；不直接读取或修改普通用户私有衣橱。
- 普通用户用例图重绘为账户与偏好、衣橱与推荐、结果与趋势三个分组；管理员用例图单独表达管理端边界，二者均保留角色—系统边界—用例关系。
- 核心业务流程重绘为“普通用户、前端应用、业务服务、外部服务与数据”四泳道，显式表达天气备用源、候选不足、LLM 降级、结果校验、重试和审计保存。
- 用例分析表的编号步骤通过真实换行标记实现；最终 11 张相关表共检测到 28 组带 `（1）` 序号的竖排路径，新增管理员表题与表格连续排列。
- Word 临时导出验证最终文档为 43 页，图 2.1—图 2.3 均显示且图题紧随其后；目录字段已更新，DOCX 压缩包完整性检查通过。由于运行时没有 `soffice.exe`，规范渲染脚本仍不可用，最终视觉 QA 使用已安装 Word 的临时 PDF 和 bundled Poppler 完成。

# 2026-09-09 系统架构图参考图对齐

- 用户提供的参考图采用横向企业级分层架构：顶部为用户层和身份认证；主体左侧为第三方系统或数据源及数据总线/接口服务；主体中央依次为应用服务、基础服务与功能组件、数据访问层和服务器资源；底部为基础工具与安全保障体系、运维管理体系、数据标准体系。
- 参考图中的“云管家、微嘉园、线上民情通、浙政钉”等政务业务文字属于示例内容，不能直接迁移到智能穿搭项目；本轮只复用其层级、相对位置、连接关系、浅色圆角盒和横向密集模块排布。
- 当前成果图为四个横向技术栈盒，仅表达表现层、业务服务层、智能推荐层、数据与基础设施层，缺少用户入口、身份认证、外部数据源、数据总线/接口服务、基础组件、数据访问层和底部保障体系，因此需整体替换，不适合局部加字修补。
- 项目化映射边界：用户层使用普通用户/系统管理员的前台、管理和审计入口；外部数据源使用 wttr.in、Open-Meteo、百炼 Qwen API 与授权网页趋势源；应用服务覆盖用户业务和管理员治理；基础设施使用 PostgreSQL、Spring Boot 应用服务、MinIO 图片对象存储、Caffeine 进程内缓存，并明确安全、运维、数据标准三类横向保障。
- 参考图与当前旧图均已通过本轮本地图片视觉检查；后续验收重点为新图在 2.4.1 页面中的可读性、图题连续性和不影响 2.4.2 后续内容的分页稳定性。

## 2026-09-09 系统架构图参考图对齐执行发现

- 高清架构图已按参考图收紧为约 1.58 的横向画布比例，顶部用户层/身份认证、左侧数据源和两条竖向集成服务、中央应用服务与组件、底部数据访问/资源/工具/保障层均保持清晰分区。
- 第一次 DOCX 替换误命中目录中的同名 2.4.1 条目；该中间输出未作为成果交付，已改用正文 Heading 3 条件从上一版成果副本重新生成，正文标题、目录条目和图像段落均恢复正确。
- Word 导出后的第 2.4 章仍为 3 页：第 16 页承接管理员表格与技术方案，第 17 页完整呈现参考图结构的图 2.4，第 18 页继续 2.4.2—2.4.5；图题紧随架构图且 2.4.2 未被挤到异常空白页。
- 目录、架构图页前后页及 43 页缩略图接触表已完成视觉检查；当前未发现新增裁切、重叠、图题漂移或后续章节分页异常。
- 目录第 3—4 页已再次放大检查，2.4.1 保持页码 17，2.4.2 同页承接，且目录条目没有被第一次误定位产生的说明文字污染。

# 2026-09-09 目录右侧页码对齐

- 用户参考截图要求不同目录层级的页码都落在同一条页面右边界；当前截图中的一级条目页码明显比二、三级条目更靠左。
- 最新成果 DOCX 的 TOC 1 样式同时存在 `w:pos=8777` 和 `w:pos=9360` 两个右对齐点引导制表位；TOC 2、TOC 3 只有 `w:pos=9360`，目录正文段落没有额外直接制表位。该差异解释了一级目录页码整体偏左的现象。
- 修复目标为删除 TOC 1 的旧 `8777` 制表位，并让 TOC 1、TOC 2、TOC 3 均只保留右对齐、点引导、`9360 twips`（6.5 英寸）的统一制表位；各级左缩进保持不变。

## 2026-09-09 目录右侧页码对齐完成发现

- Word 导出后的目录第 3—4 页已放大检查，一级、二级、三级目录页码均贴合同一右侧边界，长标题没有造成页码换行或点引导线断裂。
- 43 页全篇接触表复核显示架构图页、管理员用例表、后续章节和参考文献分页与上一版一致；没有因目录样式修正引入新的裁切、重叠或空白页。
- 最终结构检查确认 TOC 1/2/3 均只有 `right + dot + 9360` 制表位，目录字段有 71 个 PAGEREF，页脚有 5 个 PAGE，DOCX 压缩包完整。
- 只读定位显示当前项目 `docx` 目录保留了架构图按参考图的 `_v2` 和本次目录对齐成果；本轮没有删除或覆盖这两份输出。

# 2026-09-14 推荐页 AI 穿搭助手设计发现

- 当前推荐页已经形成暖纸张背景、衬线标题、橄榄绿/砖红点缀的编辑型视觉；本轮保留这套视觉，不回退到参考图的普通统计卡片布局。
- 推荐页的核心任务是帮助用户快速决定“今天穿什么”；现有页面同时承载今日搭配、生成条件、生成结果、衣橱统计和趋势提示，生成路径被拉成长页面。
- 用户已明确“按条件生成搭配”定位为 AI 助手，因此交互应从“填写三个字段”改成“上下文摘要 + 自然语言输入 + 快捷意图 + 结构化结果卡”。
- 后端推荐接口目前只接受 `occasion`、`city`、`styleHint`；前端助手先复用现有接口，不虚构多轮聊天或新增未实现的持久化能力。
- `dailyLooks` 当前会按固定模式生成五个方向，即使衣橱为空也会显示；本轮需要明确区分真实衣橱推荐和“灵感方向”，避免视觉图和文案造成虚假可穿承诺。
- 当前天气对象是实时/当前快照，切换非当天日期时不能继续把当前体感和风速作为目标日期天气显示；需要统一标签或只展示当天可验证的模板天气信息。
- 桌面端助手适合使用右侧抽屉，移动端适合底部抽屉；助手关闭后应保持页面滚动位置，生成成功后在面板内展示结果并保留保存/评分闭环。

## 2026-09-14 推荐页 AI 穿搭助手实现与验证

- 将生成入口收敛为“问问知己”，页面首屏仍由今日搭配承担决策任务；助手使用同一套暖纸张、墨黑、橄榄绿和砖红视觉，不引入独立的蓝色聊天皮肤。
- 结果卡在助手内部先呈现摘要、衣物和理由，再由用户决定收藏或继续追问；成功生成后仍写入现有当前推荐、收藏、评分和历史状态，没有新增未实现的聊天持久化层。
- 真实推荐和固定灵感模板存在数据边界：无历史推荐时显示“灵感方向”，有真实推荐时显示“来自衣橱”，并让真实推荐默认成为卡堆顶层，修复了“3 套衣橱搭配”却默认选中第 5 个灵感方向的语义冲突。
- 浏览器验收中的 1440px 页面 `scrollWidth` 与视口一致，助手抽屉为 460px 宽并锁定页面滚动；390px 下改为底部抽屉，输入、快捷按钮和关闭操作均留在视口内。
- 对抗用例验证：不含城市的自然语言输入停留在助手内提示补充，不发送不完整请求；Escape 和遮罩点击均关闭抽屉；图片失败继续走已有 fallback；减少动效偏好下抽屉和发送按钮不再播放动画。
- 端到端测试需使用仓库定义的 `docker-compose.e2e.yml` 关闭模型调用；普通开发配置下模型请求失败会得到 `request-failed`，属于运行配置差异，不应被误判为助手 UI 失败。

## 2026-09-14 管理员功能第一批初始发现

- 当前后端已有 `app_users(username, password_hash, enabled)` 和 `app_authorities(username, authority)`；普通注册只授予 `ROLE_USER`，暂未提供管理员 API、管理员控制器或管理页。
- 现有安全边界以登录 Session/CSRF 和认证 Principal 为基础，个人业务接口已按 Principal 隔离；管理员路径必须新增服务端 `ROLE_ADMIN` 保护，不能只依赖前端导航隐藏。
- 第一批最小闭环确定为管理员概览、分页账号查询、启用/停用账号；统计只返回聚合计数，账号列表不返回密码哈希或衣橱/推荐明细。
- `app_users.enabled` 可直接支持账号停用并影响 Spring Security 后续认证，无需新建状态表；停用当前管理员的自锁风险需要在服务层拒绝。
- 管理员页面是否显示取决于 `/api/v1/auth/me` 的 authority 响应；若当前响应不含 authority，需要先扩展认证响应而不改变已有 username 字段。

## 2026-09-14 管理员功能权限审计补充

- `SecurityConfig` 当前只对公开接口放行，其余请求统一 `.authenticated()`，尚无 `/api/v1/admin/**` 的 `ROLE_ADMIN` 匹配；新增管理路径必须在通用 authenticated 规则之前声明管理员角色。
- `AuthUserResponse` 当前只有 `username`；登录成功处理器和 `/auth/me` 都只构造用户名，前端无法从会话响应可靠判断管理员身份。扩展为附加 authorities 字段可保持旧的 `data.username` 兼容。
- `AuthService.register` 只创建 `ROLE_USER`，管理员账号应通过受控数据库种子/测试夹具授予 `ROLE_ADMIN`，不能开放普通注册自选角色。
- `app_users` 没有创建时间字段；本轮账号列表不伪造注册时间，返回用户名、启用状态和角色即可；后续若需审计时间再单独迁移。
- 现有个人数据仓储都以 `user_id` 过滤；管理员概览可通过聚合 `COUNT(*)` 读取数量，但账号列表不应复用或扩展到衣物/推荐明细查询。

## 2026-09-14 管理员功能测试反例

- 首次 `AdminControllerTest` 的 H2 执行账号模糊查询失败，错误为 `Error in LIKE ESCAPE: "\\\\"`；根因是 Java SQL 字符串生成了两个反斜杠作为 ESCAPE 字符，而 SQL 只接受单个字符。
- 该失败证明需要同时关注 H2/PostgreSQL 方言下的 LIKE 转义；修复目标是让 SQL 产生 `ESCAPE '\\'`（SQL 层单个反斜杠），并保留 `%`、`_`、反斜杠的输入转义。

## 2026-09-14 管理员功能全量回归发现

- 新增管理员测试独立运行 5/5 通过；全量 `mvn test` 汇总 81 项时，`AuthenticationIntegrationTest` 的 CSRF 契约失败，`/api/v1/auth/csrf` 返回 `headerName=X-CSRF-TOKEN` 而既有前端/测试契约要求 `X-XSRF-TOKEN`。
- 该现象不能直接归因于管理员代码；需要对比单类运行与全量运行的 Spring TestContext、`SecurityConfig` 和 `SessionCookieConfigurationTest`，确认是否有测试配置缓存/覆盖后再做最小修复。
- 单独运行 `AuthenticationIntegrationTest` 已通过，说明 `SecurityConfig` 的 `CookieCsrfTokenRepository` 在干净上下文中确实返回 `X-XSRF-TOKEN`；全量失败与测试顺序/共享上下文相关，当前未发现第二条安全过滤链或显式测试覆盖。
- 最小顺序复现第一次被 PowerShell 命令解析器拦截，原因是 `-Dtest` 值中的逗号未加引号；这次失败不涉及 Java/Maven，后续改用带引号的系统属性。
- 带引号的组合运行已完成：管理员测试 5/5 通过，认证测试 2 项中 1 项失败；失败响应仍为 Session CSRF 的 `X-CSRF-TOKEN`。`SecurityConfig` 源码与编译产物都仍配置为 Cookie CSRF + `X-XSRF-TOKEN`，且未找到第二条过滤器链或测试配置覆盖。
- 进一步隔离确认：不含 `.with(csrf())` 的管理员测试可与认证测试组合通过，含写请求 CSRF 的管理员测试才触发污染。Spring Security Test 的 `csrf()` 会通过 `WebTestUtils` 反射修改共享 `CsrfFilter.tokenRepository`，替换为包裹 `HttpSessionCsrfTokenRepository` 的测试仓储；这属于测试上下文共享副作用，不是生产配置变化。

### 管理员浏览器对抗补充

- 运行容器重建后，唯一临时管理员登录成功；管理导航出现，概览返回聚合计数，账号列表支持分页和用户名搜索，当前登录账号的状态按钮被禁用。
- 普通 `demo-user` 从 `#admin` 登录后自动回到 `#home`，导航不显示“管理”；该验证发现并修复了登录流程遗漏的路由纠正。
- 临时管理员登录会按现有应用启动逻辑自动生成 1 条空风格档案；清理时必须显式删除该档案，再删除唯一 authority 和用户记录。
## 2026-09-14 管理员账号写入

- 用户请求更新数据库后，先确认 `demo-admin` 不存在，再在当前 PostgreSQL 只插入启用的 `demo-admin` 与 `ROLE_ADMIN`。
- 未执行会重置 `demo-user` 的完整 seed；写后校验结果为 `demo-admin:true:1`，表示账号已启用且拥有一个管理员角色。

## 2026-09-14 管理员运营后台第二批初步决策

- 当前系统已有 `recommendation_feedback`、推荐审计字段和图片清理任务，适合先实现“反馈审核 + 推荐运行概览 + 操作审计”；仓库没有独立公共服装目录表，不新增没有数据来源支撑的服装 CRUD。
- 管理后台保留统一登录和品牌外壳，但管理区应使用独立侧栏/工作台信息架构；普通用户首页不需要复制到后台。
- 数据库更新必须采用新的 Flyway 迁移并兼容 H2/PostgreSQL；完整 demo seed 会重置 `demo-user`，本轮只增加结构和可追踪的管理员操作数据，不直接重跑完整 seed。
- 管理概览的“成功率”不能把所有推荐都称为模型成功：`engine = 'llm'` 视为实际 LLM 结果，`fallback_reason IS NOT NULL` 视为规则降级；零推荐时前端应显示“暂无数据”而不是 0% 的误导性成功率。
- 反馈审核状态使用稳定枚举 `PENDING`、`REVIEWED`、`RESOLVED`；用户编辑反馈后重新进入 `PENDING`，否则后台会把旧处理结果误认为当前反馈状态。
- 管理审计日志不使用目标表外键，保证未来账号清理不会删除治理证据；操作者仍由已认证的 `Principal` 提供，不能由请求体传入。

## 2026-09-14 管理员运营后台第二批最终验证

- H2 全量测试 84/84 通过；Flyway 测试覆盖空库迁移、基线升级和 V4 表/索引存在性，未修改既有 V1–V3 迁移。
- 运行中的 PostgreSQL schema history 已到 v4；`admin_audit_logs` 和反馈治理字段存在，`demo-admin`/`demo-user` 角色分别为 `ROLE_ADMIN`/`ROLE_USER`，业务数据计数保持为 132/13/31/17。
- 管理概览实际显示 132 个账号、31 条推荐、17 条待处理反馈、9 条 LLM 结果和 18 条规则降级结果；这些数字直接来自数据库聚合，而不是前端硬编码。
- 浏览器管理员流程通过：登录自动进入 `#admin`，运营总览、账号与权限、反馈审核、操作日志均可切换；反馈页只返回用户、评分、处理状态和推荐上下文元数据，不返回推荐正文、衣物图片或私有衣橱。
- 浏览器普通用户反例通过：`demo-user` 登录后为 `#home`，导航无管理入口；在保持普通用户会话时用 `goto #admin`，应用仍回到 `#home`。
- 390px 视口反例通过：管理工作台侧栏折叠为页面菜单，四个分区仍可访问，概览内容没有发现根布局横向溢出。
- 浏览器首次加载的 401 仅来自未认证会话检查；另发现并修复了 LoginView 账号 pattern 中未转义连字符导致的 Chromium Unicode Sets 控制台错误，重新构建后该错误消失。

## 2026-09-14 管理员运营后台实现边界

- 后台没有公共服装目录 CRUD，因为当前 schema 没有公共服装数据来源；使用真实业务聚合和衣物识别/图片清理队列指标表达运营状态。
- 推荐运行卡将 `engine = 'llm'` 与 `fallback_reason IS NOT NULL` 分开统计，不把规则降级结果包装成模型成功；基础设施监控和趋势写入仍属于后续扩展。

## 2026-09-15 趋势实现开始
- 当前 TrendService 只取首个成功源，失败回退开发样本；无日周快照和多源合并。
- Douyin 分支 db6d734 只支持单链接图集/视频下载，无内容发现、封面或抽帧。18 项基础离线测试本轮复跑通过；真实图集尚未验证。
- 推荐页主图来自衣橱；现有趋势源使用 freshness/completeness 评分，不能标为平台热度。
- 官方抖音主题榜单公开列表未含穿搭；小红书/微博权限须实测。当前工具无现成社交连接器，CLI xhs 未安装。

## 2026-09-15 管理员容器旧页面根因与验证

- 运行容器内的前端静态目录已经是新版本，但已有浏览器实际加载旧入口引用的 `index-CFraNbe7.js`；新镜像中的当前包为带新 hash 的前端资产。根因之一是 Nginx 没有为 SPA 入口页设置重新验证策略。
- 管理员首次登录路径已有 `#home -> #admin`，但页面重新加载/初始 hash 同步时，`syncViewFromLocation()` 仍允许已认证管理员停留在 `#home`；这解释了“导航栏多一个管理入口但主页面仍是普通用户首页”。
- 修复后，`syncViewFromLocation()` 同时保护两种方向：管理员从 `#home` 进入 `#admin`，普通用户从 `#admin` 回到 `#home`；后端 `ROLE_ADMIN` API 保护不变。
- 对抗用例：管理员登录后强制访问 `/#home`。修复前 E2E 失败并停在 `#home`；修复后 E2E 1/1 通过，当前 Docker 可视化检查地址为 `#admin` 且标题为“管理工作台”。
- Docker 初次干净构建还暴露了趋势模块测试与当前 `TrendService(List<TrendSourceAdapter>, TrendRepository)` 契约不一致；当前工作树测试已对齐，未通过删除测试或跳过测试编译掩盖问题。

## 2026-09-15 趋势审核 Workflow 设计决策

- Workflow 是业务状态流转，不等同于 LangChain；本项目继续使用 Spring Boot `@Scheduled`、服务层状态机、PostgreSQL/H2 和现有百炼客户端即可实现。
- 新趋势内容必须经过 `PENDING_AI → PENDING_HUMAN → APPROVED/REJECTED`；AI 失败进入 `AI_FAILED`，不能直接展示。人工通过后仍可被管理员下架，恢复时保留原审核证据。
- AI 输出只允许结构化的审核建议（是否相关、风险等级、理由、建议标签），管理员拥有最终决定权，且可推翻 AI 结论。
- 普通趋势查询的发布条件应为“人工通过 + 未隐藏”；抓取、AI 初审和管理员终审都不应把用户私有衣橱或完整外部页面正文带入审核记录。

## 2026-09-15 趋势审核 Workflow 实现与验证

- 最小实现由 `@Scheduled` + 数据库状态机 + `RestClient` 组成，不需要引入 LangChain、LangGraph、Camunda 或 Temporal；状态和审计记录本身就是可恢复的流程上下文。
- V6 迁移后旧趋势内容默认进入 `PENDING_AI`，因此升级不会把历史抓取内容直接暴露给普通用户。当前运行库 Flyway 版本为 6，自动初审后 3 条内容均为 `PENDING_HUMAN`。
- AI 客户端使用严格 JSON 契约，只接受 `PASS/REVIEW/REJECT` 与 `LOW/MEDIUM/HIGH`，并截断审核理由长度；禁用、缺少密钥、请求失败和响应非法都会进入稳定的 `AI_FAILED` 原因，不持久化原始异常。
- 验证到的关键反例：AI 初审成功不能直接让公共查询返回内容；AI 失败不能自动发布；普通用户访问管理员审核接口被拒绝；抓取刷新后人工通过状态仍保留；人工通过后设置 `hidden` 才能下架/恢复。
- Docker 的 E2E 覆盖以临时关闭 AI 的配置验证降级路径，7/7 通过；恢复正常 Compose 后 backend healthy、`/actuator/health` 为 UP，生产配置仍可由 `TREND_AI_REVIEW_*` 环境变量调整。

## 2026-09-15 运营总览 Lieflat Charts 实现

- 数据边界：当前后端只有一次性运营聚合，没有按日快照；因此不能画“增长趋势”或伪造时间序列。本轮只画两个可由现有字段诚实支撑的结论：推荐生成结果构成、账号启用构成。
- 模板审计：推荐结果比较至少审计 F1 Rung Bars、F5 Tick Rows、F7 Stacked Rungs，选择 F1，因为当前只有 3 个引擎结果类别且“一档=一条记录”可数；账号构成至少审计 L14 Hundred Field、F4 Tick Donut、G4 Dot Waffle，选择 F4，因为两段百分比在运营后台中更紧凑，且不需要编造个体记录。
- 实现没有把 lieflat-charts 的独立 HTML 当成新页面，而是把其 SVG 几何、灰阶、卡片四件套和 reveal 语法迁移到 Vue 的现有管理工作台；这样继续使用后端真实数据、现有路由和本地图标依赖，不增加 Chart.js/ECharts/CDN。
- 反例验证：总账号数为 0 时不绘制假的 100% 刻度；推荐分类未覆盖总量时增加“未标记”而不是把缺口算成 LLM；刷新页面后图表仍从 `/api/v1/admin/overview` 重算，不依赖固定演示数字。
- 视觉修复：首次实现发现异步数据加载后 IntersectionObserver 目标未及时建立，导致 SVG 保持 opacity 0；补充数据监听和可视区兜底 reveal 后，Docker 浏览器实测两个 SVG 均为 `is-visible`，刻度和标签正常显示。浅灰“未标记”系列提升为中灰以满足可读性。
