# Agent-Reach 作为趋势页采集方案的源码核对

核对日期：2026-10-14（Asia/Shanghai）。目标：结合当前项目实现，判断 Agent-Reach 是否能承担穿搭趋势页的小红书、抖音、微博数据采集。本文记录外部源码事实、本地现状及接入建议。

方法与限制：只读 GitHub 项目 README、源码和其直接上游的一手资料；没有安装或运行第三方代码，没有登录平台或操作账户。读取的是当日 `main`，不是固定版本的实测结果。未验证真实搜索成功率、账号长期存活、跨日自动运行或媒体链接有效期。公开 API 元数据读取失败，未核定当日 head commit；不把 star 数或 README 稳定性宣称作为可靠性证据。本地 `docs` 原先没有 research 目录，因此新增本文件。

## 结论

Agent-Reach 适合辅助开发者选择、安装和排查互联网工具；它自身不构成一个可直接替换现有趋势采集器的三平台数据 API。对当前需求最有价值的是它推荐的小红书上游 MCP，可作为采集后端候选，但需要项目自己的字段映射、详情补充、时间过滤、调度和失败处理。以下判断是由已读源码推导，不代表已完成真实平台验收。

## 1. 实际产品定位与接口

- README 定义为能力层，职责为选型、安装、体检和路由，实际读取由 Agent 直接调用上游工具。不能据此期待一个统一的跨平台搜索、分页和标准化数据接口。[README](https://github.com/Panniantong/Agent-Reach#设计理念)
- `cli.py` 注册安装、配置、doctor、skill、format、转录、watch、更新检查等命令，没有统一的 `search` / `read` 子命令；`format xhs` 只是处理已有 JSON。`watch` 用途是健康与更新检查，不是内容订阅采集任务。[CLI 源码](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/cli.py#L64)
- Channel 基类仅有 URL 识别、后端排序与可用性检查；真正的读取仍交给上游。个别渠道可能有额外方法，不能推广成统一 SDK。[Channel 基类](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/channels/base.py)

## 2. 三个平台的覆盖

| 平台 | Agent-Reach 自身 | 直接上游的相关能力 | 对本项目的含义 |
| --- | --- | --- | --- |
| 小红书 | 已注册渠道，优先 OpenCLI，其次 xiaohongshu-mcp，再次存量 xhs-cli | 搜索、笔记详情、评论和用户主页；MCP 搜索支持图文及一天内筛选 | 有可试用的采集后端，但不是零配置 |
| 微博 | 没有注册渠道 | OpenCLI 适配器目录列出 hot/search/feed/post/comments 等，依赖浏览器 | 可以另做适配验证；Agent-Reach 不提供已完成的微博通路 |
| 抖音 | 没有注册渠道 | OpenCLI 目录列 profile/videos/user-videos/hashtag/location/stats 等；该目录未列全站关键词 search | 不能把“有抖音适配器”理解为已满足关键词、24 小时和图文搜索需求 |

来源：[Agent-Reach 渠道注册表](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/channels/__init__.py)、[小红书渠道源码](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/channels/xiaohongshu.py)、[OpenCLI 完整适配器目录](https://github.com/jackwener/OpenCLI/blob/main/docs/adapters/index.md)。目录存在仅证明声明了命令，不证明当前账号和网络可获得有效内容。

## 3. 小红书 MCP 与趋势页数据匹配

- 上游 `FilterOption` 接受 `note_type="图文"`、`publish_time="一天内"`，以及 `sort_by="最新"`、最多点赞、最多评论或最多收藏。它通过浏览器筛选面板与页面 `__INITIAL_STATE__` 获取内容，而非小红书官方开放 API。[搜索实现](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/xiaohongshu/search.go)
- `search_feeds` 的参数为 keyword 和 filters，需要已登录。`get_feed_detail` 需要 feed_id 与 xsec_token；应从搜索结果保留完整来源上下文，避免只保存裸 ID。[MCP 工具声明](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/mcp_server.go#L249)
- 上游搜索卡片返回 `noteCard.displayTitle`、`noteCard.type`、作者、互动统计和 cover，没有发布时间；详情 `FeedDetail` 才有 time、desc、imageList、interactInfo。互动量为字符串，需项目归一化。用户粉丝统计属于单独用户主页响应，不能假定搜索结果自带。[上游类型定义](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/xiaohongshu/types.go)
- 搜索代码 `waitFeedsChanged` 最长等待 15 秒，超时只打印警告，随后仍可返回筛选之前的数据。因此“传入一天内”不等于严格保证 24 小时。若项目要求严格窗口，应读取详情并检查实际 time、图文类型及图片，再纳入结果；时间戳单位也需以样本验收。[筛选等待实现](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/xiaohongshu/search.go#L191)
- 上游已有 HTTP 服务：`/api/v1/login/status`、GET/POST `/api/v1/feeds/search`、POST `/api/v1/feeds/detail` 和 `/api/v1/user/profile`，并有 `/mcp`。项目采集器可以评估直接接 HTTP，不必额外通过 Agent-Reach 和 mcporter。[路由源码](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/routes.go)

## 4. 两处文档与实际接入的差别

**Doctor 不能作为小红书采集成功验收。** README 宣称按顺序真实探测完整可用后端、选择首个可用路线；但小红书渠道的 `_check_opencli` 即使桥接 ready 也只返回 warn，明确不执行平台命令；`_check_mcp` 即使服务可达且在 mcporter 注册仍只返回 warn，未验证登录。`_check_xhs_cli` 也只检查显式保存 Cookie，不实时调用平台。因此这些路径不会据此设置有效 active_backend。服务可达甚至将任意 HTTP 错误响应视为存活。应另验登录状态、非空搜索及详情字段。[README](https://github.com/Panniantong/Agent-Reach#设计理念)、[小红书 doctor 源码](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/channels/xiaohongshu.py#L157)

**内置 `format xhs` 不能直接标准化当前 MCP 原始响应。** Agent-Reach `_clean_note` 主要匹配 `note_card`、`xsec_token`、`interact_info`、`liked_count`、`image_list`、`url_default`；当前 MCP 原始结构却使用 `noteCard`、`xsecToken`、`interactInfo`、`likedCount`、`imageList`、`urlDefault`，搜索顶层是 feeds，清洗器只识别 items/notes 包装。静态对照可见直接传入会遗漏关键字段；详情中的标题/desc/time等部分同名字段可能保留，不能笼统称完全不可用。项目需要显式映射或保留原始结构。本次未执行第三方清洗函数。[清洗实现](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/channels/xiaohongshu.py#L46)、[上游 JSON 类型](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/xiaohongshu/types.go)、[上游响应包装](https://github.com/xpzouying/xiaohongshu-mcp/blob/main/service.go#L77)

## 5. 部署与运维边界

- Agent-Reach 当前 pyproject 声明 Python >=3.10、版本 1.5.0、Beta，依赖 requests/feedparser/PyYAML/rich/yt-dlp 等；可选浏览器依赖 Playwright。核心安装还有 Node.js、gh、mcporter 等工具，按所需渠道选择，不代表趋势功能必须全装。[pyproject](https://github.com/Panniantong/Agent-Reach/blob/main/pyproject.toml)
- 桌面 OpenCLI 需要运行中的 Chrome、扩展和已有登录会话；服务器小红书 MCP 需要单独二进制与无头浏览器。Agent-Reach 安装文档提示首次浏览器下载约 150MB、调用超时建议 120 秒、Cookie 手动导入；这些是部署预算参考，尚未测量本项目机器资源消耗。[安装指南](https://github.com/Panniantong/Agent-Reach/blob/main/docs/install.md#L174)
- 社交 reference 明示小红书 xsec_token 限制、高频搜索与深评论会触发验证码，建议操作间隔 2–3 秒；xhs-cli 作为停更存量备选。README 对 Cookie 平台提示账号被限制及凭据暴露风险。更换工具不能自动消除平台会话失效与风控。[社交渠道使用说明](https://github.com/Panniantong/Agent-Reach/blob/main/agent_reach/skill/references/social.md#L62)、[README Cookie 建议](https://github.com/Panniantong/Agent-Reach#cookie-安全建议)
- Agent-Reach 代码为 MIT；这解决该项目代码使用许可，不代表被采集平台内容或图片自动获得转载许可。[LICENSE](https://github.com/Panniantong/Agent-Reach/blob/main/LICENSE)

## 6. 反向检查与尚未验证事项

最可能推翻“不宜整体替换”的反例，是它的上游工具恰好已经完整覆盖三个平台且能自动稳定采集；现有一手资料支持小红书候选与微博命令存在，却没有证明抖音所需搜索能力或无人值守稳定性。反过来，小红书图文与时间筛选确实存在，不能把它概括为“只能读网页、没有趋势可用数据”。

后续验证应直接针对真实采集结果：已有会话能否得到相关搜索结果；详情是否给齐标题、来源、实际发布时间、图文类型、图片、互动数；严格 24 小时窗口能否独立验证；失效时能否保留已有有效结果并报告原因。本文没有完成这些实测，也不据 README 宣称给出成功率或长期可靠性保证。

## 7. 当前穿搭项目的实际需求与现状

本地依据是本次工作区代码，未用旧任务记录替代现有实现。根 AGENTS.md 引用的 docs/agents/ 与 CONTEXT.md 在当前工作区不存在；README 引用的 docs/trend-collection.md 也不存在，因此以实际源码和现存采集说明为准。

- 当前不是从零开发趋势数据链路。[daily_collector.py](../../tools/trend-collector/daily_collector.py) 已用 Scrapling DynamicFetcher 搜索微博、抖音、小红书；只收录最近 24 小时的合格穿搭图文，每平台最多 50 条，失败时保留旧导出并返回 503。[PUBLIC_COLLECTION.md](../../tools/trend-collector/PUBLIC_COLLECTION.md) 说明其使用独立浏览器，不复用用户 Cookie 或浏览器资料。
- [SocialTrendConfiguration.java](../../backend/src/main/java/com/fashion/recommendation/trend/SocialTrendConfiguration.java) 已按平台配置独立 JSON 端点，未配置端点时可读本地导入。Compose 默认连接现有 trend-collector 的三个平台端点；所以可以只更换某一平台的采集后端，而不改页面和整套服务。
- [ConfiguredJsonTrendSourceAdapter.java](../../backend/src/main/java/com/fashion/recommendation/trend/ConfiguredJsonTrendSourceAdapter.java) 要求顶层仅含 items，条目需要 id/platform/title/topicTags/heatScore/publishedAt/sourceUrl/imageUrl，可选 summary/evidence/fetchedAt，必须显式映射原始响应；一个格式错误可拒绝该平台整批。社交源合法空结果允许返回 items=[]，不能把有效空结果当连接失败。
- [TrendService.java](../../backend/src/main/java/com/fashion/recommendation/trend/TrendService.java) 已负责启动与北京时间每日 12 点刷新、数据入库、窗口过滤、平台内互动评分、博主分层及失败保留。缺失互动数应保留 null，粉丝数未知归为未分类；无需让 LLM 生成看似真实的热度或补齐事实字段。
- [trendGallery.js](../../frontend/src/utils/trendGallery.js) 对画廊要求 evidence.fullBodyImageUrl 且必须属于该帖图集；仅有封面无法满足画廊条件。现有采集器不自动确认全身照；因此接入上游后可以先有内容卡片而没有画廊，不能把这误判为采集完全失败。Agent-Reach 未提供全身穿搭识别或原帖商品提取能力。
- [trendImage.js](../../frontend/src/utils/trendImage.js) 的特定代理仅覆盖符合限定格式的新浪图片。小红书图片能取得 URL 不代表本项目浏览器就能正常加载，图片直链的实际可用性还需联调，当前未验证其失效与访问限制。

本次只读取已有状态文件，未重新采集。tools/trend-collector/data 的 2026-10-14 状态记录如下，不能据此保证当前线上状态仍相同：

| 平台 | 记录尝试时间（北京时间） | 结果 | 接受条数 |
| --- | --- | --- | --- |
| 微博 | 08:28 | unavailable，login-or-verification-required | 0 |
| 抖音 | 08:26 | unavailable，TimeoutError / collector-failed | 0 |
| 小红书 | 08:28 | unavailable，TimeoutError / collector-failed | 0 |

证据文件：[微博状态](../../tools/trend-collector/data/weibo-collection-status.json)、[抖音状态](../../tools/trend-collector/data/douyin-collection-status.json)、[小红书状态](../../tools/trend-collector/data/xiaohongshu-collection-status.json)。这些记录支持“目前采集存在访问或超时障碍”的判断；它们不能证明登录会必然解决所有失败，也不能证明任何替代工具一定更好。

## 8. 推荐的最小接入路线

建议保留当前 Vue 趋势页、Java JSON 契约、趋势库、调度及评分；先验证一个小红书来源。Agent-Reach 可作为开发环境中的工具配置和排障辅助。如果应用仅需要小红书，直接集成它所推荐的 xiaohongshu-mcp HTTP 接口更贴合现有 Python 采集服务，完整 Agent-Reach 与 mcporter 链路不是必需依赖。

建议链路：小红书搜索（图文、一天内、穿搭关键词）→ 详情补充 → 本地严格验证实际发布时间、图文类型和原帖图集 → 映射现有 items JSON → 独立小红书端点 → 现有 Java 趋势库 → 趋势页。

实现时保留完整详情访问上下文，但只向业务前端输出必要公开内容；平台登录凭据不得进入业务 JSON、前端或日志。需要已有会话或手动导入凭据的配置属于下一阶段，应单独获得用户授权，本次仅完成分析。

推荐先做单源原型验收，再连续运行一周观察相关内容数量、详情成功率、页面图片加载、耗时及会话失效。先证明原帖、真实发布时间和图片可追溯；互动与粉丝数缺失时保留未知。全身照确认作为单独条件验证，不因接口返回图片就自动添加标记。重试、缓存、旧结果保留和平台隔离继续使用现有机制；更慢的详情补充应有总体耗时预算，必要时预采集并供后端读取，不把长浏览器任务放进用户页面请求。

最终判断：毕业设计的穿搭素材发现或小红书单源试验具有适用性；直接替换现有三平台采集服务、承诺每天稳定取得 24 小时热点与完整全身照的依据不足。本次没有修改业务代码、安装第三方依赖、运行平台采集或部署服务，只新增此分析文档。
