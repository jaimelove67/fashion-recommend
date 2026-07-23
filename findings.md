# 当前发现

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
