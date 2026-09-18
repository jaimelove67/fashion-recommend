# 趋势内容来源与接入说明

趋势页展示的是**公开的穿搭内容及来源**，不是全网热榜。本文说明当前有哪些来源可以接入、内容如何进入用户可见区，以及抖音/小红书/微博为什么需要额外入口。

## 1. 先说清楚边界

抖音、小红书、微博都**没有可公开调用的热榜 API**。本项目实测结论：

| 平台 | 匿名访问结果 |
| --- | --- |
| 微博 | 搜索接口 HTTP 432，热搜接口 HTTP 403 |
| 小红书 | 需要登录会话，匿名读取被拒 |
| 抖音 | 需要请求签名与登录 Cookie |

因此本项目**不内置对这三站的直连爬虫**，也**不伪造平台热度**。趋势页的热度口径只有三种，且在界面上区分展示：

- **编辑发布 · 按时间** —— 出版方 RSS 源。文章本身没有热度概念，`heatScore` 固定为 0，排序按发布时间。
- **平台内互动评分** —— 仅当条目带有真实点赞/收藏/评论/转发计数时，按 `likes + 2×收藏 + 2×评论 + 3×转发` 并随时间衰减计算，上限 100。
- **开发样本热度** —— 未接入任何真实来源时的降级数据，响应中 `demoMode=true`，界面明确标注。

内容进入用户可见区只有一条路径，没有任何旁路：

```
采集/导入 → trend_contents(PENDING_AI) → AI 初审 → PENDING_HUMAN → 管理员人工终审 → APPROVED → 普通用户可见
```

AI 只能把内容推进到人工队列，不能直接发布或驳回；AI 失败的内容停留在隔离状态。

## 2. 数据流

```
编辑 RSS 源 ─────┐
                  ├──→ TrendSourceAdapter ──→ trend_contents ──→ AI 初审 ──→ 人工终审 ──→ GET /api/v1/trends
采集/导出文件 ─────┘   (每平台独立适配器与状态)      (V6 状态机)
```

每个平台是**独立的适配器与独立的失败域**：某一个源被墙、超时或返回脏数据，不会清空其他源的内容，也不会把上一次的真实结果覆盖成空白。

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

## 4. 通道二：三平台内容导入

`tools/trend-collector/collector.py` 把**公开内容导出**规范化成后端契约。它只读取公开字段，不读取也不记录登录 Cookie 或原始响应。

### 4.1 操作步骤

```bash
cd tools/trend-collector
python collector.py status                                        # 先看 data/ 现状
python collector.py import --input <导出.json> --platform douyin
python collector.py import --input a.json b.json --platform weibo  # 一次导入多个文件
python collector.py status                                        # 确认 state 变为 ready
```

导入结果写入 `data/<platform>.json`。`docker-compose.yml` 已把该目录**只读**挂载到后端 `/app/trend-data` 并设为 `TREND_IMPORT_DIRECTORY`，源码运行时需要自己把 `TREND_IMPORT_DIRECTORY` 指向该目录。

### 4.2 导入后让内容真正上线

```
POST /api/v1/admin/trends/refresh                        # 管理员手动刷新来源，写入审计
POST /api/v1/admin/trends/ai-review?limit=10             # AI 初审，只推进到人工队列
PUT  /api/v1/admin/trends/contents/{id}/review           # 人工终审 {"status":"APPROVED","note":"..."}
```

不点刷新也可以等定时任务（`TREND_REFRESH_INTERVAL`，默认 6 小时）。

### 4.3 采集器接受的字段

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

### 4.4 会被拒绝的输入（反例）

- 缺少发布时间或观测时间 → 拒绝，**不把旧导出当成新鲜数据**
- 原文链接域名与所选平台不符 → 拒绝
- 内容与穿搭无关 → 拒绝
- 单文件超过 2,000,000 字节，或 `items` 为空 → 后端拒绝；`status` 标记为 `oversized` / `empty`
- 本次导入没有任何可用条目 → **保留上一次的 feed**，不会把已有内容清空

### 4.5 可选：小红书登录态采集

需要 `pip install -r requirements.txt` 并执行 `xhs login` 生成 `~/.xiaohongshu-cli/cookies.json`，然后 `python collector.py xhs --keyword 通勤穿搭 --limit 10`。会话缺失时会明确报 `XHS_SESSION_REQUIRED`，不会静默失败。

这条路径属于**登录态采集**，可能违反平台条款并导致账号受限，默认不使用。如采用，应在论文与答辩中作为「可选扩展」说明，并与编辑 RSS 通道区分开。

### 4.6 可选：本地 HTTP 服务

```bash
python collector.py serve --port 8767
```

把 `TREND_DOUYIN_URL` / `TREND_XIAOHONGSHU_URL` / `TREND_WEIBO_URL` 指向 `http://127.0.0.1:8767/<platform>.json`。适合不希望后端直接读文件的部署方式。

## 5. 配置项

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| TREND_EDITORIAL_ENABLED | true | 是否启用编辑 RSS 源 |
| TREND_EDITORIAL_FEEDS | Vogue、Hypebeast CN、理想生活实验室 | 逗号/换行分隔的 HTTPS 订阅源 |
| TREND_IMPORT_DIRECTORY | 空（Compose 为 `/app/trend-data`） | 读取 `<platform>.json` 的目录 |
| TREND_DOUYIN_URL / TREND_XIAOHONGSHU_URL / TREND_WEIBO_URL | 空 | 各平台 JSON 端点，独立失败隔离 |
| TREND_REFRESH_INTERVAL | 21600000 | 定时刷新间隔（毫秒） |
| TREND_WEB_URLS | 空 | 管理员配置的授权公开网页，抽取标题/摘要/标签/时间/图片 |
| TREND_AI_REVIEW_ENABLED | true | 趋势 AI 初审；关闭时内容停留在隔离区 |

## 6. 来源状态怎么看

`GET /api/v1/trends` 返回 `sources`，趋势页与推荐页的「来源与统计说明」直接展示：

| state | 界面文案 | 含义 |
| --- | --- | --- |
| `ready` | 已连接 | 最近一次抓取成功，并显示收录条数与最后成功时间 |
| `unavailable` | 采集失败 | 抓取失败，保留上次结果 |
| `unconfigured` | 未接通 | 社交平台未提供入口 |
| `pending` | 等待采集 | 尚未跑过首次刷新 |

状态列表只反映**当前真实存在的适配器**：已删除或改名的来源不会继续留在列表里。所以「抖音/小红书/微博 未接通」是明确显示的状态，不是静默缺失。

## 7. 当前状态与未完成的部分

已完成的验证（2026-09-18）：

- 后端 `mvn test` 全量通过，其中 `EditorialTrendSourceAdapterTest` 覆盖真实源结构（CDATA 内联图片、http 永久链接、仅 `content:encoded` 的正文、非穿搭/缺时间/未来时间/非 web 链接的反例）。
- 前端 `npm run build` 通过；`docker compose --profile app config` 通过，`TREND_EDITORIAL_FEEDS` 与 `/app/trend-data` 挂载均已渲染。
- 三个默认订阅源实测 HTTP 200 且可解析。
- 采集器 `python -m unittest test_collector` 6 项通过。

仍未完成：

- `tools/trend-collector/data/` 目前为空，尚未导入任何真实社交平台导出，因此抖音、小红书、微博在界面上显示**未接通**。
- 公开 RSSHub 实例在本机不可达，未引入；如需微博/小红书/抖音的 RSS 化，需要自建 RSSHub，且仍需登录态。
- 趋势链路的全栈端到端验证需要 Docker 运行时；最近一次环境不可用，只完成了单元测试、构建与 Compose 静态校验，没有跑真实容器页面。
- `TrendFeed.demoMode` 现在恒为 `false`（开发样本回退已按「无来源时显示空状态」的原则移除），前端仍保留该字段的展示分支，属于待清理的死代码。
