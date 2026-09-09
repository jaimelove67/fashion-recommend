# 执行记录

## 2026-08-23（当前版本最终复核）

- 远端同步复核：`codex/fix-major-gaps` 的 `HEAD` 与 `origin/codex/fix-major-gaps` 同为 `cddc878b6c3e2dfda6172ff8317d1899aee9893b`；未执行提交或推送，保留用户未跟踪答辩 Markdown。
- Claude 代码改动已落盘并复核：`WardrobeService.upload` 增加 `@Transactional`，对象补偿删除失败不会覆盖原始数据库异常；`WeatherService` 在主 provider `NOT_FOUND` 且备用失败时保持 404；`WeatherServiceTest` 新增该组合反例。
- `mvn -q -f backend/pom.xml test`：10 个测试类、62 项通过，0 失败/0 错误/0 跳过；其中 `WeatherServiceTest` 16 项通过。
- 前端依赖安全修复收敛为 lockfile-only：`package.json` 未增加直接 `nanoid`，`package-lock.json` 仅将传递解析从 3.3.16 更新到 3.3.18；`npm install` 后 `npm run build` 通过，`npm audit --audit-level=high` 返回 0 vulnerabilities。
- 离线评估 `python -m unittest discover -s scripts/evaluation -p "test_*.py"`：38 项通过；`docker compose config -q` 与 `git diff --check` 通过；毕业设计 DOCX 已按 62 项测试重新生成并完成结构生成。
- Docker Desktop Linux Engine 当前仍不可连接，未重跑真实 PostgreSQL/MinIO/Playwright E2E；上传事务尚缺 `updateImageUrl` 失败时的独立回滚测试，作为下一步风险保留。

## 2026-08-04（审计缺口收尾）

- 完成工作区只读审计：git status 显示 41 个修改/新增文件；task_plan/findings/progress 已完整读取；surefire 报告确认后端 62 项测试全通过（10 个测试类，0 失败/0 错误/0 跳过），前端 E2E spec 4 项。
- 实现离线评估协议（Task A）：`scripts/evaluation/evaluate_recommendations.py`（纯标准库，默认只读本地 JSON，不联网、不调用百炼/任何模型 API）+ `fixture_wardrobe.json`（8 件衣物、6 条 LLM 结果，meta.kind=fixture 明确标注不为实验结论）+ `test_evaluate_recommendations.py`（25 项 unittest）。
- 评估脚本 seed=20260804 实际控制 baseline 子集抽样与 LLM 结果抽样；`--baseline-trials`、`--sample-size`、`--temperature`、`--report` 均为本地计算参数。
- 反例测试 debug 发现两处真实问题：规则引擎可输出 5 件（五大类别各一件），2-4 边界属于 LLM 输入边界；`final_recommendation` 必须与校验共用同一边界，否则重复 ID 非法结果会产出重复最终推荐。均已修复并通过测试。
- 运行 `python -m unittest discover -s scripts/evaluation -p "test_*.py"`：25 项全部通过；`evaluate_recommendations.py --pretty` 输出真实指标（llm_valid_rate=0.3333、fallback_reasons 含四类原因、rule_engine_validity=1.0 等）。
- 新增 `docs/evaluation-protocol.md`（协议 v1：数据集格式、输入边界、指标定义、seed 语义、运行命令、对抗用例、fixture 与真实实验区分），README 增加最小入口。
- 局部修订 `build_graduation_doc.py`（Task B）：修正 JWT/Redis/pgvector/MyBatis-Plus/Spring AI/管理员/抖音/Jsoup/旧表名/“待实测”；改为 Session+CSRF、Flyway V1-V3、Spring JDBC、62 项后端测试、4 项 E2E、推荐审计 generationAudit、configured-demo 离线天气；重绘架构图与 ER 图（新 6 表 app_users/wardrobe_items/recommendations/recommendation_items/recommendation_feedback/style_profiles）。
- 重新生成 `docx/基于大语言模型的智能穿搭推荐系统_毕业设计成果.docx`（181 段落、17 表格）；结构 QA 通过：ZIP 可打开、关键术语存在、错误术语在声称实现上下文中消失、生成器可重复运行（逐条目哈希一致）。
- ER 图标签宽度检查发现 `recommendation_feedback` 在 font 27 下溢出 300px 盒子，降为 font 22 后全部 label 适配。
- 视觉渲染 QA（要求使用 render_docx.py）：失败于 `ModuleNotFoundError: No module named 'pdf2image'`，且本机无 LibreOffice/soffice、无 Poppler；按要求记录确切失败信息并明确“未完成视觉渲染 QA”，只做结构 QA，不假装通过。
- 更新 task_plan/findings/progress 只追加本阶段事实；未改动 build 脚本之外的无关文件；未触碰 Docker 容器与数据卷。

## 2026-08-04（阶段九：答辩交付缺口收敛）

- 完成当前项目只读审计：核对 README 与配置、Compose、pom.xml、frontend 依赖与迁移文件口径。
- 首次 Claude CLI 大批次执行因 8 美元预算上限退出，未产生任何文件修改；本轮改为小批次并逐项落地。
- 开始阶段九第 1 项（配置口径）：收敛 Actuator 暴露、移除未使用的 Prometheus 依赖与 pgvector 口径、固定前端 vite 依赖版本，并同步开发边界文档。
- 第二次 Claude CLI 批处理同样因 8 美元预算上限退出，但已产生配置口径改动（Actuator 收敛、移除 pgvector/Prometheus 口径、固定前端 vite 版本）。
- 阶段九第 1 项完成：`frontend/package.json` 与 `package-lock.json` 根 `dependencies` 均固定 `vite@8.1.4`、`@vitejs/plugin-vue@6.0.7`；Actuator 仅暴露 health/info，仓库不再声称 pgvector 或 Prometheus 指标地址。
- 开始阶段九第 2 项（推荐审计元数据）：新增 Flyway V3 迁移与审计领域对象，让推荐来源可核对、可脱敏。
- 第 2 项落地：百炼客户端解析真实响应 id、model 与 usage 三类 token，定义稳定 prompt 版本常量；服务层用 `TransactionTemplate` 把写操作与外部天气/LLM 调用隔离，并保存稳定枚举式 fallback 原因。
- API 新增嵌套 `generationAudit` 返回审计元数据，engine 仍保留在顶层，旧前端只读 engine 不受影响。
- 第 2 项测试：新增合法 LLM 元数据落库与返回、无 key/请求异常/非法 ID/同类别 fallback 原因、V2->V3 迁移保留旧数据且新列为空、usage 缺失时成功但 token 为空等用例。
- 开始阶段九第 3 项（离线天气）：新增默认关闭的静态天气快照 fallback，让答辩在无网络时也能演示天气驱动的推荐但不冒充实时天气。
- 第 3 项落地：`app.weather.configured-demo-enabled`（默认 `false`）与静态快照字段；仅当两个真实 provider 都失败且请求城市匹配配置城市时返回 `source=configured-demo`，前端标注“配置演示天气/非实时”；NOT_FOUND 仍返回 404；配置校验 fail-fast。
- 第 3 项测试：`WeatherServiceTest` 新增默认关闭 503、开启且城市匹配返回 configured-demo、城市不匹配 503、NOT_FOUND 404、配置非法反例等用例，并保持现有构造器测试兼容。
- 验证：`npm run build`（vite 8.1.4，1785 模块）、`docker compose config -q`、`git diff --check` 通过；`mvn test`、`npm ci`、`npm audit` 因当前沙箱权限 allowlist 未放行，未能执行，待授权后补跑。

### 编译错误修复与收敛（阶段九收尾）

- 上一批改动引入编译错误：`BailianRecommendationClient.buildUserPrompt` 内 `new LlmRecommendationException("无法序列化推荐上下文", exception)` 只传了两个参数，其中第二个是 `Throwable`，不匹配该异常类仅有的 `(reason, message)` 与 `(reason, message, cause)` 两个构造器，无法通过 `mvn compile`。
- 修复：改为传入稳定原因常量 `RecommendationFallbackReason.REQUEST_FAILED`（三参构造器），序列化推荐上下文失败按请求失败回退处理，不再让编译失败。
- token usage 解析收紧：`integralNumber` 只接受非负整数；缺失或非整数（含 `isMissingNode`/`isNull`）继续返回 null，而负数 provider token 直接抛 `LlmRecommendationException(RESPONSE_INVALID)`，走稳定 fallback，避免负值命中 V3 迁移的 `chk_recommendations_*_tokens >= 0` CHECK 造成 500。`total_tokens` 保持真实 provider 值，不按 prompt+completion 擅自修正。
- 修正 `RecommendationAudit` 错误 Javadoc：成功 LLM 的 model/token 字段携带真实 provider 元数据且 `fallbackReason` 为 null；仅规则 fallback 才只填充 `fallbackReason`。
- 新增最小测试：usage 含负 `prompt_tokens` 时 `parseResponse` 抛 `LlmRecommendationException` 且 reason 为 `RESPONSE_INVALID`，负 token 响应不会被当作成功 LLM。
- 依赖版本核对：`frontend/package.json` 与 `package-lock.json` 根 `dependencies` 均固定 `vite@8.1.4`、`@vitejs/plugin-vue@6.0.7`，一致。
- 验证：`npm run build` 通过（vite 8.1.4，1785 模块）、`docker compose config -q` 通过、`git diff --check` 仅 LF->CRLF 提示、无空白错误。`mvn compile`/`mvn test`/`npm ci`/`npm audit` 因权限系统未放行本轮未能执行，待授权后补跑。

## 2026-08-01（阶段八：审查缺口修复）

- 完成当前项目代码审查：后端 37 项测试与前端生产构建通过，Compose 静态配置通过。
- 确认六项待修复问题：LLM 类别约束、视觉超时、跨存储删除一致性、历史分页/N+1、输入长度约束、PostCSS 高危依赖。
- 保留用户已有的 `frontend/src/views/HistoryView.vue` 修改和新增技术文档，不纳入本轮无关改动。
- 开始第 1 项：为 LLM 输出补充类别互异校验和反例测试。
- 第 1 项完成：LLM 同类别输出会整体回退规则引擎；`RecommendationControllerTest` 13/13 通过。
- 开始第 2 项：让视觉识别客户端消费百炼连接/读取超时，并补延迟响应反例。
- 第 2 项完成：本地慢响应服务证明 50ms 读取超时生效，视觉超时测试 1/1 通过。
- 第 3 项采用数据库先删、MinIO 后清理的最小策略，优先保证用户可见数据不引用已删除对象。
- 第 3 项反例通过：MinIO 清理失败时接口仍成功且数据库记录已删除；日志收敛为错误摘要。
- 历史分页首次编译因 `JdbcTemplate.query` lambda 重载歧义失败，已显式指定 `RowCallbackHandler` 后继续。
- 第 4 项完成：历史接口默认 20 条、最大 50 条，返回总数和 hasNext；反馈和衣物均按页批量读取。控制器测试 15/15、前端构建通过。
- 开始第 5 项：把反馈与风格档案 DTO 长度约束对齐数据库和页面输入边界。
- 输入长度反例首次发送了错误的 JSON 根节点；已修正构造方式，并补齐错误 JSON 的标准 400 响应。
- 第 5 项完成：超长风格档案和反馈字段均在持久化前返回统一 400，针对性测试通过。
- 开始第 6 项：仅更新 PostCSS 传递依赖和锁文件，不升级应用框架主版本。
- 第 6 项完成：PostCSS 升级到 8.5.25，`npm audit --audit-level=high` 为 0 vulnerabilities，前端构建通过。
- outbox 首次完整回归发现任务断言误放在无图片删除测试，已移到 MinIO 失败反例并新增调度器成功重试测试。
- 对抗事务提交失败场景后，删除流程调整为事务内只删除记录并持久化清理任务，MinIO 副作用全部交给提交后的定时调度器。
- 阶段八完成：后端 `mvn test` 44/44 通过，前端构建和 `npm audit --audit-level=high` 通过，Compose 静态配置和 `git diff --check` 通过。Docker Desktop 未启动，未重复执行真实容器 E2E。

## 2026-07-22（阶段七收尾恢复）

- 从现有规划文件恢复上下文，核对认证、Flyway、趋势适配器、视觉授权和前端会话实现已经落盘。
- 确认此前完整后端测试为 37 项通过、前端生产构建通过；本轮仍需重跑 E2E、Compose 和真实浏览器验收。
- 启动两条独立并行审计：文档/配置口径修复，以及安全与迁移对抗性只读审查；前端 E2E 更新仍在进行。
- 受管终端执行 `git status --short` 失败：PATH 中找不到 Git。改用容错读取继续，不重复同一失败命令。
- 前端 E2E 并行任务已完成：脚本静态校验通过，共发现 4 个 Chromium 测试；完整运行等待容器启动。
- 搜索旧身份引用时发现演示种子未创建认证账号，已纳入本轮最小修复。
- Python 生成 BCrypt 哈希失败（缺少 `bcrypt` 模块）；下一步改用现有 Spring Security 依赖，避免引入工具依赖。
- 创建分支 `codex/fix-major-gaps`，提交 `fb386ae`；执行 `gh auth setup-git` 后推送成功，并创建草稿 PR #1。
- Docker Desktop 已通过 GUI 启动并确认 Engine running；当前显示的是旧应用栈，含待清理的孤立 Redis 容器。
- 首次完整 Compose 重建中前端 Vite 构建通过（170.50 kB JS / 89.17 kB CSS）；后端在 `dependency:go-offline` 下载 guava 时响应体中断，尚未替换运行中旧容器。
- 后端 Dockerfile 已增加 BuildKit Maven 缓存和有限网络重试，准备重新构建。
- 第二轮后端构建未返回编译错误，但超过 10 分钟工具上限；改用 `docker build --network=host` 复用缓存并缩短网络路径。
- `docker build --network=host` 成功构建新后端；Compose 使用新镜像重建，旧 Redis 孤立容器已移除，健康检查为 UP。
- 真实旧库完成 Flyway baseline 0 -> V1，迁移历史为两条成功记录；数据卷保留。
- 演示种子连续两次执行结果稳定，真实登录与衣橱读取通过。
- 首轮完整 E2E 为 3/4 通过；唯一失败是 Playwright 不保留 multipart `postData()`，业务上传已成功并返回 `MANUAL_CORRECTED`。已移除该脆弱观察点，等待重跑。
- 删除脆弱 multipart 观察点后 E2E 4/4 通过。
- Maven Docker 容器完整后端测试通过：8 个套件、37 项测试、0 失败、0 错误、0 跳过。
- 应用内浏览器完成桌面首页、衣橱、上传弹窗和 390x844 移动布局验收；页面身份、非空内容、控制台、交互和截图证据全部通过。
- 阶段七验收条件全部满足；准备仅暂存本轮修复文件，明确排除并保留独立的 `HistoryView.vue` 工作区改动。

## 2026-08-01

- Docker Desktop 恢复后，从当前工作区代码成功重建 backend/frontend 镜像，并仅替换应用容器；PostgreSQL 与 MinIO 持久卷保持不变。
- 新后端健康检查为 UP，启动日志确认现有 PostgreSQL 从 V1 成功迁移到 V2。
- Flyway 历史包含 baseline、V1 与 V2 三条成功记录；`image_cleanup_tasks` 主键和到期任务索引均已在真实 PostgreSQL 中确认。
- 当前 Docker 栈 Playwright E2E 4/4 通过，覆盖登录会话、衣物新增、推荐保存、反馈持久化、未授权访问、无 AI 同意上传和移动端视口。
- 阶段八最终验证闭环完成；未改动用户独立的 DOCX Markdown，也未暂存或提交任何文件。

## 2026-07-22

- 开始收敛上一轮评估中的主要缺口。
- 采用第一性边界：不伪造实时趋势、不默认启用付费视觉调用、不为技术栈清单保留无消费者 Redis。
- 已读取文件规划、前端测试调试和 PostgreSQL 最佳实践技能；已恢复现有计划、发现与执行记录。
- 已建立阶段七计划，下一步并行审计认证影响面、趋势/迁移契约和前端会话/E2E 改造点。
- 工具错误：首次并行读取技能文件触发 Windows sandbox 1056；改为单进程顺序读取后成功。
- 完成第一轮源码审计：确认个人接口和图片代理均信任客户端用户标识；另发现 CORS 缺少 `PUT/DELETE`。
- 读取 PostgreSQL 约束、外键索引、过滤索引和最小权限规则；迁移设计将补齐外键侧与用户查询索引。
- 认证方案确定为同源 HttpOnly Session Cookie + CSRF，复用 Spring Security form login 和现有代理；避免 JWT/localStorage 及私有图片 blob 改造。
- 后端认证实现已交由独立审计代理在限定文件范围内落地；主任务继续负责迁移、趋势、视觉授权、前端和集成验证。
- 完成前端只读审计：确定认证门禁初始化、会话退出清理、视觉同意控件和新增 E2E 选择器契约。
- 已实现配置式趋势 JSON 适配器、严格字段/URL/时间/数量校验、Caffeine 缓存及 `TrendService` 实时源优先/开发样本降级；等待单元测试和配置补齐。
- 已从 Compose、环境示例和种子错误提示中删除无消费者 Redis；业务数据卷 PostgreSQL/MinIO 保持不变。
- 已创建 Flyway V1 基线，包含原有业务表、认证表契约和四个查询/外键索引，并删除运行期 `schema.sql`。
- 工具环境错误：受管 shell 当前找不到 `git`，常见安装路径也不存在；不重复同一路径，最终阶段再用可用环境检查差异。
- 后端认证主体代码已落地：JDBC 用户、BCrypt、form login、Session fixation、CSRF、JSON 401/403、注册/会话接口和 Principal 身份替换；等待测试迁移。
- Flyway 配置和依赖已接通，测试配置同步关闭 `schema.sql` 初始化；新增旧库升级/幂等迁移测试。
- 上传接口新增 `allowAiRecognition=false`，未同意时完全跳过模型调用；图片 URL 删除用户查询参数。
- 风格档案与反馈首次并发写入增加唯一键冲突后的 UPDATE 重试，保持 PostgreSQL/H2 共用 SQL。
- 前端已实现登录/注册门禁、同源 Session、CSRF 轮换、退出清理、请求版本隔离和 AI 识别同意控件；等待代理构建结果与浏览器验证。
- 首次完整 `mvn test` 已成功下载新增依赖；Flyway 升级和纯趋势测试通过，Spring 上下文因趋势适配器双构造器未标注注入点失败，已按根因修复。


## 2026-07-14

- 收到四项答辩增强任务，确认按指定顺序逐项提交并推送。
- 已恢复现有规划文件和 Git 状态；当前 main 跟踪 origin/main。
- 发现上一个任务的根 README 和 .gitignore 尚未提交，先作为本轮基线独立发布，避免混入演示数据提交。
- 第一次规划补丁未应用：Markdown 反引号与执行器模板字符串冲突；改用无冲突锚点后继续。
- 基线提交 f96bd0d 已推送到 origin/main。
- 演示数据采用仅重置 demo-user 的幂等 SQL，不依赖一次性的本地数据库快照或实时天气请求。
- 已增加 8 件衣物、3 条推荐、收藏反馈、风格档案及识别失败后人工修正的种子实现，并在衣橱界面区分“人工确认”状态。
- Docker 完整栈启动成功；种子连续运行两次后均保持 8 件衣物、3 条推荐、1 条收藏反馈和 1 条 MANUAL_CORRECTED。
- API 复核结果为 wardrobe=8、recommendations=3、saved=1、feedback=1、manualCorrected=1，个人档案显示名为林知夏。
- 前端生产构建、PowerShell 语法检查、Compose 配置检查和 git diff 检查均通过。
- 第 1 项提交 8c243d6 已创建；首次推送因 GitHub HTTPS 连接被重置而失败，暂停后续阶段并先检查远端连通性。
- git ls-remote 随后也因 github.com:443 无法连接而失败，确认问题位于外部网络链路而非提交内容或 Git 认证。
- 网络诊断结果：github.com 可解析且可 ping，但 TCP 443 不通；继续检查 GitHub SSH 备用端口。
- SSH 443、SSH 22 和本机 GitHub 凭据检查通过，改用 SSH URL 完成第 1 项推送。
- ssh.github.com:443 因该别名尚无 known_hosts 记录而被安全校验拒绝；不关闭校验，改用已知的 github.com:22。
- github.com:22 主机校验通过，但 GitHub 返回 publickey 拒绝；不擅自向用户账号添加 SSH Key，转查现有身份配置和本地代理。
- 检测到 Clash Verge 代理 127.0.0.1:7897，准备以单命令配置方式验证 HTTPS，不修改全局 Git 配置。
- 本地 gh CLI 通过代理成功读取 jaimelove67/fashion-recommend，确认账号权限、仓库和 main 默认分支均正常。
- 已新增独立开发期边界文档，集中说明四项边界的设计目的、可观察行为、安全失败方式和生产化替换路径。
- 已修正 README 中个人风格档案仅在内存保存的过时描述；当前档案与衣橱、推荐、反馈均持久化到 PostgreSQL。
- 开发期边界文档术语检查和 Markdown 差异检查通过，README 已链接该文档。
- 第 2 项提交 1a0b2c3 已推送到 origin/main。
- 第 3 项开始：确认百炼 Key 已配置、qwen-plus 可用，视觉识别仍按设计保持默认关闭。
- 第一次真实推荐请求于 16:29:59 发起，业务结果 engine=development-rule-v1，因此未作为模型证明；按约束先查明 LLM 降级原因。
- 第一次证据文档补丁因多文件更新块上下文标记错误而未应用；拆分为证据文件与索引更新后继续。
- 16:32:24 从宿主机按相同请求契约成功调用百炼 qwen-plus，取得 call ID、usage 和合法结构化结果。
- 已新增 Mermaid 推荐流程图、prompt 四层约束说明和脱敏 JSON 调用记录，并在 README 中增加入口。
- 证据 JSON 解析、token 合计、衣橱 ID 校验和真实 Key 泄漏检查通过；后端 24 项测试全部通过。
- 第 3 项提交 3696172 已推送到 origin/main。
- 第 4 项开始：确认 Playwright 1.59.1 可用，并梳理衣橱、推荐和收藏流程的稳定可访问标签。
- Playwright CLI 已打开 Docker 前端并完成首页、衣橱真实快照，语义控件与源码预期一致。
- 第一次 E2E 多文件补丁因 README 更新块残留多余 hunk 标记而未应用；依赖安装成功，改为拆分补丁继续。
- 已加入 @playwright/test 1.59.1、Chromium 配置和完整业务 smoke spec，并补充运行命令及失败产物位置。
- 首次 npx playwright install chromium 在 184 秒后无输出超时；不重复同一路径，先关闭 CLI 会话并检查浏览器缓存与代理。
- CLI 会话已关闭；test --list 成功收集 1 个 spec，浏览器缓存确认缺少 Chromium，改用代理下载。
- 第二次 Chromium 安装显式使用代理后仍无输出超时，判断阻塞更可能来自首次超时遗留的缓存锁；先验证进程和锁路径再处理。
- 已确认 4 个孤儿安装进程及锁目录的绝对路径，准备只清理这些临时状态后重试。
- 第三次直接 CLI 下载已建立代理连接但长期未完成，主动终止；确认系统 Chrome 150 可供 Playwright channel 直接使用。
- 首次 E2E 使用系统 Chrome 成功运行到页面新增衣物，但 POST wardrobe 返回非 2xx；trace、截图和视频已保留，先诊断真实响应再修改。
- 相同请求不带 Origin 时返回 200，初步定位为 Docker CORS 配置缺少前端 8090；先构造带 Origin 的反例确认。
- 带 Origin 的反例确认返回 403 Invalid CORS request；修复 Compose 时首次补丁因变量插值冲突未应用，改用普通字符串后已加入 FRONTEND_PORT 来源。
- 后端重建后带 8090 Origin 的 POST 返回 200；首次完整 E2E 通过，1 个测试耗时 13.3 秒。
- E2E 第二次运行继续通过，测试结束后 e2e-smoke 用户衣橱残留数为 0。
- 最终验证：前端构建通过、后端 24 项测试通过、Compose 配置通过、临时产物忽略规则命中、待提交文件未包含真实 API Key。

## 2026-07-13

- 已确认项目同时包含 `frontend` 和 `backend`。
- 已确认前端构建通过，后端现有测试通过。
- 已确认当前后端缺少衣橱、推荐、保存记录的核心接口。
- 已建立本次任务计划，下一步实现数据库配置和核心领域模型。
- 已确定最小接口契约：衣橱管理、推荐生成、推荐历史、推荐保存。
- 开始增加 JDBC/PostgreSQL 配置、H2 测试数据库和三张核心表。
- 已实现衣橱新增、查询、删除；推荐生成、查询、历史列表和保存接口。
- 已通过基础 `mvn test`，并增加业务闭环与反例测试。
- 已修复推荐历史快照问题：删除衣物不会改变已保存推荐。
- 最终验证：4 个测试通过，`mvn package -DskipTests` 通过，`docker compose config -q` 通过，`npm run build` 通过。
- 阶段五开始：确认原有 `image_url` 只能保存外链，MinIO 未接入，开始设计私有对象代理和识别失败状态。
- 阶段五完成：加入 MinIO Java SDK、私有桶上传/读取/删除、对象键与识别状态字段、百炼视觉识别适配器和手动修正接口。
- 前端完成图片选择、multipart 上传、识别失败提示、人工修正表单和图片展示；保留无图片时的原有手工添加路径。
- 对抗验证：JPG/PNG/WEBP 接受，SVG 在存储前返回 400；识别失败保存为 `NEEDS_MANUAL_REVIEW`，修正为 `MANUAL_CORRECTED` 后可参与推荐；跨用户图片读取返回 404。
- 真实栈验证：Docker 后端、PostgreSQL、MinIO 上传返回 200，图片代理返回 `image/jpeg`，推荐生成和历史读取通过；浏览器 `8090` 页面无框架错误、控制台无 warn/error，点击“完善”出现修正表单。
- 过程错误：Compose 组合重建等待一次性 `minio-init` 超时；已拆分构建/启动完成验证。真实 PostgreSQL 的 numeric 读取异常已修复并通过重新构建验证。
# 2026-09-07 审查问题修复

- 已复核并修复 Secure Cookie 默认值、分页偏移边界和 long 算术、PostgreSQL digest、Node engines 和 Docker 文档。
- 在原有 README、lockfile 和规划记录上叠加修改，未改写已有天气、衣物上传和 DOCX 改动。
- 新增分页边界反例，完整验证进行中。
- 第一轮 Maven 65/65、Python 38/38、npm ci/build/audit 和 Compose config 均通过；前后端镜像构建通过。复核后补充 hasNext 不越过偏移边界的服务反例，待最终回归与后端镜像更新。
- 最终 Maven 66/66（12 个测试类，0 失败/错误/跳过）通过，补充的 hasNext 边界反例通过，后端镜像已重新构建。
- docker compose --profile app up -d --no-build --wait --wait-timeout 60 通过；health 返回 UP。PostgreSQL 与 MinIO 仍使用原镜像及原命名数据卷。
- 当前完整栈 npm run test:e2e 4/4 通过（10.5 秒），覆盖推荐保存反馈、认证会话、上传与移动端。模型开关经选择性配置核对均为 false，未输出密钥。
- 最终差异复核通过，原有天气、上传、DOCX 改动及文件删除状态保留，未暂存/提交/推送。服务留在 http://localhost:8090。

# 2026-09-08 网页趋势采集

- 读取用户提供的 SnapWC 中文首页：确认其公开能力是网页媒体资源检测/下载解析，不是可直接接入的时尚趋势数据 API。
- 读取现有趋势链路：前端只消费 `/api/v1/trends`；后端已有严格 JSON 适配器、Caffeine 缓存和开发样本降级，但没有 HTML/JSON-LD 网页采集。
- 采集实现按最小范围设计为服务端配置 URL 的网页适配器；下一步先落地适配器与契约测试，再同步界面、配置和文档。
- 已新增 `ConfiguredWebTrendSourceAdapter`、Jsoup 依赖、网页趋势配置和 7 项适配器测试；扩展 `summary`/`scoreLabel` 契约并让首页、风潮页、个人档案页区分“来源页信号评分”。
- 网页源支持 OpenGraph/JSON-LD/可见 HTML，列表页可拆分多个 `article`；配置 URL 限制 HTTP(S)、拒绝 localhost/内网/回环、禁用重定向并按 TTL 缓存。
- 最终验证：后端 75/75 通过，前端 `npm run build` 通过，`docker compose config -q` 通过；未配置具体真实趋势页面，线上页面兼容性仍需用户提供授权 URL 后再联调。

# 2026-09-08 风潮 Top 10 三维弧形画廊

- 收到需求：完整 3D 弧形画廊放在风潮界面，当前先展示按热度排序的 10 套开发样本，真实每日抓取后续替换。
- 第一性决策：后端开发样本扩充到 10 条，前端从趋势数据按 `heatScore` 排序取前 10，不额外制造一套与 API 脱节的假数据层。
- 已完成：TrendService 开发降级 feed 扩充为 10 条，并更新趋势服务与控制器契约测试。
- 已完成：TrendView 新增按 heatScore 排序取 Top 10 的 3D 弧形画廊，支持卡片、左右按钮、数字 tab、方向键和 Home/End 浏览，详情保持联动。
- 已完成：移动端使用单卡焦点与两侧局部卡片降级，390px 回归确认无根布局溢出；CSS 支持 `prefers-reduced-motion`。
- 最终验证：前端生产构建通过；Maven 全量 75/75 通过；运行栈 `/api/v1/trends` 返回 10 条 `demoMode=true`、热度 96→73 降序样本；桌面和 390px 浏览器截图及交互验证通过。
- 交付边界：真实抓取和每日调度仍未开发，接入后只需替换现有 TrendItem 来源；当前开发图片为项目已有 3 张图片循环复用，并由界面明确标注为开发样本。

# 2026-09-08 风潮画廊视觉与自动轮播精修

- 根据新参考图收敛界面：标题改为“风潮穿搭精选”，移除 `01 / 10`、箭头按钮、舞台下方焦点文字、提示语和 01–10 导航按钮。
- 舞台取消独立浅色背景，改为透明；卡片间距按实际卡片宽度动态计算，桌面和移动端都保留清晰留白。
- 增加 1 秒自动轮播；自动移动只改变画廊焦点，不让页面其他趋势详情随时间跳动。鼠标悬停、键盘聚焦、页面隐藏和组件卸载会暂停或清理定时器；reduced-motion 下不自动播放。
- 移动端第一次加大间距后发现卡片重叠，已改为“卡片宽度 + 间隙”的计算方式；最终 390px 回归无横向溢出，卡片间距约 22–39px。
- 最终验证：前端构建通过，Docker 前端镜像重建并健康启动；1440px 桌面与 390px 移动截图、自动轮播和页面控件删减检查通过，浏览器 0 个错误。

# 2026-09-09 登录/注册切换动画

- 已读取并遵循 frontend-skill 与 planning-with-files-zh；建立阶段十七，明确本轮只改认证页表现与可用性，不改后端认证契约。
- 完成 LoginView、全局字体/颜色、可用媒体资产和前端脚本的初步审计：当前左侧是静态 JPG，移动端隐藏；Tab、标题和确认密码均为无动画瞬时切换。
- 已确定最小方案：在 LoginView 内新增切换 key/焦点调度、稳定表单区高度、缝线 pseudo-element 与响应式媒体封面，不添加动画库或伪造视频文件。
- 具体实现决策：标题内容使用 240ms `Transition mode="out-in"`，确认密码使用固定高度槽位承载层叠展开，注册态保留用户名说明位以避免表单高度二次跳动；Tab 说明文案保持文字 Tab 语义。
- 响应式决策：移动端展示现有静态穿搭封面（180–220px），`max-height: 700px` 时隐藏媒体优先表单；未来接入视频可复用该媒体边界，不在当前素材缺失时伪造 MP4。
- 已完成桌面浏览器视觉检查：登录/注册两态均能渲染，Tab 可访问名称未被辅助文案污染，注册确认密码与创建账户按钮出现；未启动后端导致的 502 安全会话提示保持原有珊瑚边线表现。
- 已完成移动端与 reduced-motion 检查：390×844 展示约 200px 静态封面，390×650 自动隐藏媒体；两种视口 `scrollWidth` 未超出布局宽度，矮屏仍保留完整认证表单。
- 已完成时序/布局对抗验证：标题交叉过渡总时长 240ms，确认槽位 75px，登录/注册提交按钮 top 坐标一致；切换结束焦点为用户名，reduced-motion 下动效关闭但状态和焦点保留。
- 已补齐快速切换的无障碍与表单有效性保护：固定槽位在登录态立即 `aria-hidden`，Transition 离场钩子禁用确认密码 input；快速“注册→登录”反例通过。
- 最终 `npm run build` 通过，`git diff --check` 无空白错误；本轮只改动 `frontend/src/views/LoginView.vue` 与规划记录，没有改后端、没有新增视频素材，也没有覆盖仓库其他未提交业务改动。

# 2026-09-09 移除重复认证标题

- 按用户反馈删除登录/注册两态的“欢迎回来、登录知己”及对应注册表述，保留 Tab 状态说明与表单切换动效。
- 清理失效标题无障碍引用和未使用的标题过渡 CSS；`npm run build` 重新通过，源码检索不再命中这些旧文案。

# 2026-09-08 自动轮播节奏与图片悬停暂停

- 将风潮画廊自动轮播间隔调整为 1000ms。
- 改为图片卡片级悬停暂停：鼠标进入图片时清理定时器，离开图片后恢复轮播；鼠标在图片卡片之间移动不会误触发恢复。
- 保留键盘聚焦、页面隐藏、组件卸载和 `prefers-reduced-motion` 的保护逻辑。
- 单次浏览器脚本验证：无悬停时 1.3 秒内焦点变化；悬浮图片 1.3 秒内焦点保持；移出后 1.3 秒内恢复变化。前端构建通过，Docker 前端容器健康，控制台 0 个错误。

# 2026-08-23 当前版本审查

- 先检查远端：`codex/fix-major-gaps` 与 `origin/codex/fix-major-gaps` 同步到 `cddc878`；未跟踪答辩 Markdown 与评估脚本 `__pycache__` 保留未动。
- 回归证据：`mvn -q test` 60/60、`npm run build`、离线评估 38/38、`docker compose config -q` 通过。
- 反例证据：`npm audit --audit-level=high` 失败，`nanoid@3.3.16` 报 1 个 high severity；Docker Desktop 未运行，真实栈 E2E 未重跑。
- 待 Claude 修改：升级/锁定 nanoid 依赖并补上传事务失败测试；主代理随后同步 README、开发边界、findings、task_plan 和本记录。
- 文档同步：重新生成毕业设计成果 DOCX；生成器将本轮未执行的 Playwright 标记为“本轮未执行”，并把 frontend/backend 的 Compose 检查改为可验证的页面/API 与 Actuator 检查，不再声称不存在的 healthcheck。
- Claude 中间态检查：代理 worktree 首次补丁重复插入 `upload` 方法、丢失 `allowAiRecognition` 并复活 `?userId=` 图片地址，未合并；已要求基于远端当前文件重做，任何不通过契约检查的补丁都不进入主工作区。
- 追加代码审查反例：主天气 provider NOT_FOUND + 备用 provider ERROR/timeout + configured-demo 匹配时不能返回静态天气，需保留 NOT_FOUND 语义并返回 404。
- 文档同步：修正毕业设计生成器中个人风格档案的错误模型调用描述，改为当前已实现的本地确定性 development fallback。
- Claude 最终依赖任务完成：`nanoid` 3.3.16 -> 3.3.18，`npm audit --audit-level=high` 0 vulnerabilities，`npm run build` 通过；后端两个代码任务均失败或产出 malformed 中间态，未合并。
