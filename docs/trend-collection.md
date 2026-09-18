# 趋势内容来源与接入说明

趋势页展示的是**公开的穿搭内容与来源**。本文说明当前有哪些来源可以接入、内容如何进入用户可见区，以及抖音/小红书/微博各自**匿名能读什么、不能读什么**。

## 1. 先说清楚边界（2026-09-18 实测）

平台能力必须分开看：**热榜匿名可读**，但**关键词搜索与笔记正文需要登录**。二者不是一回事。

| 平台 | 匿名读热榜 | 匿名搜关键词 / 读正文 |
| --- | --- | --- |
| 微博 | ✅ HTTP 200，50 条，含平台热度值 `num` | ❌ `m.weibo.cn` 搜索接口 HTTP 432；`s.weibo.com` 搜索页 302 跳登录 |
| 抖音 | ✅ HTTP 200，50 条，含平台热度值 `hot_value` | ❌ web 搜索接口返回 `status_code 2483「请先登录」` |
| 小红书 | ❌ 没有公开热榜 | ❌ `explore` 页 302 跳登录；搜索接口 `code -101「无登录信息」` |

实测用的 User-Agent 是自定义的 `FashionResearch/1.0`，与浏览器 UA 结果一致（HTTP 200）——所以本项目**不伪装浏览器**，如实标识研究客户端。

由此确定本项目的数据边界：

- **不内置关键词搜索爬虫、不抓取笔记正文与登录态内容。**
- **不伪造平台热度。** 热度口径只有四种，且在界面上区分展示：

| 评分口径 | 用在哪 | 怎么算 |
| --- | --- | --- |
| 编辑发布 · 按时间 | 出版方 RSS | 文章本身没有热度概念，`heatScore` 固定 0，按发布时间排序 |
| 平台热榜热度（归一化） | 抖音热榜、微博热搜 | 平台自己的榜单热度值做对数归一化，榜首位 = 100 |
| 平台热榜位次 | 同一榜单但平台未给热度值时 | 按榜单名次折算，第 1 位 100，末位接近 0 |
| 平台内互动评分 | 导入的真实内容 | 仅当带真实点赞/收藏/评论/转发计数时，按 `likes + 2×收藏 + 2×评论 + 3×转发` 并随时间衰减，上限 100 |

内容进入用户可见区只有一条路径，没有任何旁路：

```
采集/导入 → trend_contents(PENDING_AI) → AI 初审 → PENDING_HUMAN → 管理员人工终审 → APPROVED → 普通用户可见
```

AI 只能把内容推进到人工队列，不能直接发布或驳回；AI 失败的内容停留在隔离状态。

## 2. 数据流

```
编辑 RSS 源 ───────┐
公开热榜(抖音/微博) ─┼──→ TrendSourceAdapter ──→ trend_contents ──→ AI 初审 ──→ 人工终审 ──→ GET /api/v1/trends
采集/导出文件 ──────┘     (每平台独立适配器与状态)      (V6 状态机)
```

每个平台是**独立的适配器与独立的失败域**：某一个源被墙、超时或返回脏数据，不会清空其他源的内容，也不会把上一次的真实结果覆盖成空白。

同一平台的优先级是**导入文件 > 配置端点 > 公开热榜**。只有在前两者都不存在时才读公开热榜，所以显式导入的内容永远优先，热榜只是"开箱即有真实数据"的兜底。

## 3. 通道一：编辑 RSS（默认开启，零配置）

`TREND_EDITORIAL_ENABLED=true`（默认）时，后端按 `TREND_EDITORIAL_FEEDS` 抓取出版方订阅源。

- 逗号或换行分隔，**订阅源地址只接受 HTTPS**，空值回退到内置源
- 逐源独立抓取与失败隔离，全部失败才把该来源标为 `unavailable`
- 条目按链接指纹去重；同一文章重复出现不会重复计数
- 只保留能通过穿搭关键词分类的条目，非穿搭内容在入库前被丢弃
- 默认抓取范围：Vogue、Hypebeast CN、理想生活实验室

解析器读取 RSS 2.0 的 `<item>`：标题、原文链接、`pubDate`，正文取 `description`，为空时回退 `content:encoded`（部分出版方只把正文放在后者）。图片优先取 `media:content` / `media:thumbnail` / `enclosure`，都缺失时回退到正文里的内联 `<img>`。

字段与链接规则：

| 规则 | 处理 |
| --- | --- |
| 订阅源地址 | 必须 HTTPS |
| 条目原文链接 | 允许 http 或 https。部分出版方仍发布 http 永久链接（如理想生活实验室），这类条目不会被丢弃 |
| 图片 | 必须 HTTPS。http 图片会被丢弃并在界面显示「图片暂不可用，查看原文」，避免 https 页面加载混合内容 |
| 无 host、带用户名密码、或非 web 协议的链接 | 丢弃 |
| 标题、原文链接、可解析的 `pubDate` 任一缺失 | 丢弃，不以抓取时间冒充发布时间 |
| `pubDate` 晚于当前时间 60 秒以上 | 丢弃 |

2026-09-18 在本机实测三个源均返回 HTTP 200 且可解析出真实内容。两个中文源都不提供 `media:*` 或 `enclosure` 标签，图片实际位于 `description` 的内联 `<img>` 中，理想生活实验室的原文链接为 http —— 这些真实结构都已被上面的规则覆盖并有单元测试（`EditorialTrendSourceAdapterTest`）。更换或增加源只需改 `TREND_EDITORIAL_FEEDS`，不需要改代码。

## 4. 通道二：公开热榜（默认开启，零配置）

`TREND_HOT_BOARDS_ENABLED=true`（默认）时，抖音与微博在既没有导入文件、也没有配置端点的情况下，读取各自的公开热榜：

- 抖音：`https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/` → `word_list[].word` + `.hot_value`
- 微博：`https://weibo.com/ajax/side/hotSearch` → `data.realtime[].word`/`.note` + `.num`

热榜给出的是**榜单词 + 平台自己的热度值**，没有正文、没有图片、没有每条的发布时间。因此：

- 条目正文固定为「<榜单名>第 N 位 · 榜单热度值 X」的描述，不编造摘要
- 没有发布时间，`publishedAt` 记为**观测时间**（不是伪造的发布时间）
- 没有图片，`imageUrl` 为 `null`，界面走「无配图」分支
- 评分口径明确标为「平台热榜热度（归一化）」，**不冒充互动量**（`hasCounters()` 为 false）
- 原文链接指向该词的平台搜索结果页，用户可自行打开核对

### 4.1 短词必须命中明确的穿搭词

热榜词是 2–12 字的**话题标签**，不是文章，所以这里用的是比文章分类器更严格的判定（`TrendTopics.boardFashion`）：必须命中一个明确的服饰/时尚词（穿搭、外套、牛仔、针织、球鞋、包包……）。

这不是洁癖，是实测踩出来的。第一版直接复用了面向长文的关键词分类器，真实榜单就给出了这条：

```
微博热搜第 36 位「才意识到一台苹果手机能换一万斤粮食」→ 被判为 [轻户外]
```

原因是大词表里的「机能」命中了"手**机能**换"。同类风险还有「街头」命中"街头采访"、「裙」命中"裙带关系"、「运动」命中"五四运动"、「复古」命中"复古游戏"。短词上这类巧合很常见，代价是用户一看到不相关内容就会不再信任整页。因此**短词只认明确标记，宁可少收一条、不可错收一条**。这些真实反例都已进单元测试（`PublicHotBoardsTest.shortBoardWordsNeedAnExplicitFashionMarker`）。

### 4.2 复核实测

真实热榜按需实测（默认不跑，不联网则跳过）：

```bash
cd backend
mvn -Dtest=PublicHotBoardsLiveTest -DliveTrendBoards=true test
cat target/trend-live-report.txt
```

结果会写入 `target/trend-live-report.txt`，包含每条的得分、位次、原始热度值与评分口径，可作为答辩现场"确实读到了真实数据"的证据。

## 5. 通道三：三平台内容导入

`tools/trend-collector/collector.py` 把**公开内容导出**规范化成后端契约。它只读取公开字段，不读取也不记录登录 Cookie 或原始响应。

### 5.1 操作步骤

```bash
cd tools/trend-collector
python collector.py status                                        # 先看 data/ 现状
python collector.py import --input <导出.json> --platform douyin
python collector.py import --input a.json b.json --platform weibo  # 一次导入多个文件
python collector.py status                                        # 确认 state 变为 ready
```

导入结果写入 `data/<platform>.json`。`docker-compose.yml` 已把该目录**只读**挂载到后端 `/app/trend-data` 并设为 `TREND_IMPORT_DIRECTORY`，源码运行时需要自己把 `TREND_IMPORT_DIRECTORY` 指向该目录。

### 5.2 导入后让内容真正上线

```
POST /api/v1/admin/trends/refresh                        # 管理员手动刷新来源，写入审计
POST /api/v1/admin/trends/ai-review?limit=10             # AI 初审，只推进到人工队列
PUT  /api/v1/admin/trends/contents/{id}/review           # 人工终审 {"status":"APPROVED","note":"..."}
```

不点刷新也可以等定时任务（`TREND_REFRESH_INTERVAL`，默认 6 小时）。

### 5.3 采集器接受的字段

取平台内容导出，字段名参照 `collector.py` 的 `normalize()`：

| 通用字段 | 说明 |
| --- | --- |
| `note_id`（小红书/微博）、`aweme_id`（抖音） | 内容 ID，必填 |
| `title` / `content` / `desc` | 标题与正文，用于穿搭相关性判断 |
| `time` 或 `create_time` | 发布时间，必填 |
| `last_modify_ts` | 观测时间；缺失时用本次导入时间 |
| `note_url` / `aweme_url` | 原文链接，域名必须与所选平台一致 |
| `image_list` / `cover_url` | 图片或封面 |
| `liked_count` / `collected_count` / `comment_count` / `share_count` | 互动计数，用于平台内互动评分 |
| `nickname` | 作者 |

### 5.4 会被拒绝的输入（反例）

- 缺少发布时间或观测时间 → 拒绝，**不把旧导出当成新鲜数据**
- 原文链接域名与所选平台不符 → 拒绝
- 内容与穿搭无关 → 拒绝
- 单文件超过 2,000,000 字节，或 `items` 为空 → 后端拒绝；`status` 标记为 `oversized` / `empty`
- 本次导入没有任何可用条目 → **保留上一次的 feed**，不会把已有内容清空

### 5.5 可选：小红书登录态采集

小红书没有任何匿名入口，只能走登录态。需要 `pip install -r requirements.txt` 并执行 `xhs login` 生成 `~/.xiaohongshu-cli/cookies.json`，然后 `python collector.py xhs --keyword 通勤穿搭 --limit 10`。会话缺失时会明确报 `XHS_SESSION_REQUIRED`，不会静默失败。

这条路径属于**登录态采集**，可能违反平台条款并导致账号受限，默认不使用。如采用，应在论文与答辩中作为「可选扩展」说明，并与前两条通道区分开。

### 5.6 可选：本地 HTTP 服务

```bash
python collector.py serve --port 8767
```

把 `TREND_DOUYIN_URL` / `TREND_XIAOHONGSHU_URL` / `TREND_WEIBO_URL` 指向 `http://127.0.0.1:8767/<platform>.json`。适合不希望后端直接读文件的部署方式。

## 6. 配置项

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| TREND_EDITORIAL_ENABLED | true | 是否启用编辑 RSS 源 |
| TREND_EDITORIAL_FEEDS | Vogue、Hypebeast CN、理想生活实验室 | 逗号/换行分隔的 HTTPS 订阅源 |
| TREND_HOT_BOARDS_ENABLED | true | 抖音/微博的公开热榜兜底；导入文件或端点存在时不生效 |
| TREND_IMPORT_DIRECTORY | 空（Compose 为 `/app/trend-data`） | 读取 `<platform>.json` 的目录 |
| TREND_DOUYIN_URL / TREND_XIAOHONGSHU_URL / TREND_WEIBO_URL | 空 | 各平台 JSON 端点，独立失败隔离 |
| TREND_REFRESH_INTERVAL | 21600000 | 定时刷新间隔（毫秒） |
| TREND_WEB_URLS | 空 | 管理员配置的授权公开网页，抽取标题/摘要/标签/时间/图片 |
| TREND_AI_REVIEW_ENABLED | true | 趋势 AI 初审；关闭时内容停留在隔离区 |

## 7. 来源状态怎么看

`GET /api/v1/trends` 返回 `sources`，趋势页与推荐页的「来源与统计说明」直接展示：

| state | 界面文案 | 含义 |
| --- | --- | --- |
| `ready` | 已连接 | 最近一次抓取成功，并显示收录条数与最后成功时间 |
| `unavailable` | 采集失败 | 抓取失败，保留上次结果 |
| `unconfigured` | 未接通 | 社交平台未提供入口 |
| `pending` | 等待采集 | 尚未跑过首次刷新 |

`ready` 的来源若本次没有穿搭内容，会显示「来源可用，本次没有穿搭相关内容」——**榜单读到了但没有相关词，与来源不通是两件事**，界面分开表述。微博热搜经常一整天没有穿搭词，这时它仍然是"已连接"。

状态列表只反映**当前真实存在的适配器**：已删除或改名的来源不会继续留在列表里。所以「小红书未接通」是明确显示的状态，不是静默缺失。

## 8. 当前状态与未完成的部分

已完成的验证（2026-09-18）：

- 后端 `mvn test` 全量 **122 项通过**（0 失败 / 0 错误，1 项为按需实测类默认跳过），其中 `PublicHotBoardsTest` 9 项、`EditorialTrendSourceAdapterTest` 9 项。
- 公开热榜按需实测通过并落盘证据（`target/trend-live-report.txt`）：抖音当日命中 2 条真实穿搭热榜词，微博当日 0 条（榜单里确实没有穿搭词）。
- 前端 `npm run build` 通过；`docker compose --profile app config` 通过，`TREND_EDITORIAL_FEEDS`、`TREND_HOT_BOARDS_ENABLED` 与 `/app/trend-data` 挂载均已渲染。
- 三个默认订阅源实测 HTTP 200 且可解析；采集器 `python -m unittest test_collector` 6 项通过。

已知限制：

- **小红书仍无任何真实内容**，因为它是三家唯一没有匿名入口的平台，只能走登录态采集（默认不启用）。
- 热榜只提供"榜单词"，**没有正文与图片**，因此趋势页里来自热榜的条目是"话题 + 热度 + 搜索入口"，不是图文卡片。这是匿名边界的直接结果，不做伪造。
- 热榜的"榜单热度值"是平台自身口径，不同平台之间**不可直接比较**；跨平台列表按平台内名次轮转，不做跨平台热度归一。
- 微博热搜常年缺少穿搭词，来源状态会频繁显示「来源可用，本次没有穿搭相关内容」，这是真实情况而非故障。
- 趋势链路的全栈端到端验证需要 Docker 运行时；最近一次环境不可用，只完成了单元测试、构建与 Compose 静态校验，没有跑真实容器页面。
- `TrendFeed.demoMode` 现在恒为 `false`（开发样本回退已按「无来源时显示空状态」的原则移除），前端仍保留该字段的展示分支，属于待清理的死代码。
