# 执行记录

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
