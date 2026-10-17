# 每周穿搭技巧：公开来源与正文采集验证

验证日期：2026-10-16。只读访问公开页面，没有登录、绕过付费墙、调用模型或写入业务数据库。

## 实际来源读取

RSS 发现入口均返回 HTTP 200：

- `https://www.permanentstyle.com/feed`：370,853 bytes。
- `https://putthison.com/feed/`：94,066 bytes。

用生产 `TechniqueSourceCollector.collect()` 单独读取两个已知教程地址，结果如下。这里的读取日期不表示文章刚刚发表。

| 页面 | 实际提取结果 |
| --- | --- |
| [How to wear trainers – 1: Design](https://www.permanentstyle.com/2016/04/how-to-wear-trainers-1-design.html) | Permanent Style；正文 4,547 字符、32 段；当前页面没有被采集器识别到机器可读作者与发表日期，因此两项为 `null`，不由 URL 年月或既有静态资料推测。 |
| [How To Put Together A Successful Outfit](https://putthison.com/how-to-put-together-a-successful-outfit/) | Put This On；Derek Guy；`2018-02-25T06:40:10Z`；正文 18,589 字符、53 段。站点将 `www` 地址规范到无 `www` 地址。 |

Permanent Style 的旧文章主要用 `div` 而非 `p` 分段，已验证兼容。文章正文全部段落（包括短标题和短限定句）会保留，页面评论、侧栏、广告、导航与相关推荐不纳入证据。图片与视频未被独立观看，因此不能把正文提取声称为图像验证。

## 初始广域订阅的独立复核

另一次生产 `collect()` 使用最初两个广域 RSS，**`article-urls` 为空**，因此没有手动常青地址参与。成功返回 6 篇具备足够可读正文的候选，均来自 Permanent Style，页面作者经 RSS 元数据保留为 Simon Crompton：

| 候选标题 | 来源链接 | 发表时间 | 正文字符数 |
| --- | --- | --- | --- |
| Johnston’s new knitwear: Interesting, luxurious | [来源](https://www.permanentstyle.com/2026/10/johnstons-knitwear-luxurious-and-innovative.html) | 2026-10-12T08:00:39Z | 5,384 |
| House cuts, deer bones, tweed suits: Ask me anything | [来源](https://www.permanentstyle.com/2026/10/house-cuts-deer-bones-tweed-suits-ask-me-anything.html) | 2026-10-09T08:00:18Z | 2,320 |
| My perfect weekend boot: Designing the Viberg ‘Leisure’ boot | [来源](https://www.permanentstyle.com/2026/10/my-perfect-weekend-boot-designing-the-viberg-leisure-boot.html) | 2026-10-07T08:00:53Z | 7,443 |
| Trims and fits: How product design works, at Buck Mason | [来源](https://www.permanentstyle.com/2026/10/trims-and-fits-how-product-design-works-at-buck-mason.html) | 2026-10-02T08:47:24Z | 7,543 |
| What Simon’s looking forward to wearing this autumn | [来源](https://www.permanentstyle.com/2026/09/what-simons-looking-forward-to-wearing-this-autumn.html) | 2026-09-30T08:00:41Z | 6,782 |
| The varied tailoring of Rubinacci today | [来源](https://www.permanentstyle.com/2026/09/the-varied-tailoring-of-rubinacci-today.html) | 2026-09-25T08:00:59Z | 7,136 |

此次 Put This On 的最新非 Inside Track 页面未通过正文门槛。候选包含品牌或产品设计讨论、问答征集；**可读正文数量不等于高质量教程数量，也不证明已生成可发布的技巧**。上层仍须验证具体方法有正文证据、来源数量与题材相关性、去除营销与问题征集，并在不充分时跳过发布。这次只读验证未调用归纳模型。

## 最终专题订阅与按周轮换

根据上述真实结果，默认源已改为站点页面 `<link rel="alternate" type="application/rss+xml">` 实际公布的专题订阅：

- `https://www.permanentstyle.com/category/style/feed`：HTTP 200、450,225 bytes；风格与穿法文章。
- `https://putthison.com/tag/guides/feed/`：HTTP 200、399,348 bytes；原创方法教程，部分是历史文章。

同时剔除标题中明确的 AMA 问答征集、产品设计、上新针织、产品评测、展售活动，以及维修保养教程。每个专题候选按上海时区的 ISO 周轮换，随后交错两站候选；显式配置的文章地址仍优先。这避免每周始终只看到同一源头几篇，实际文章的发表日期不会被改成本周。

最终生产实现使用两个专题默认值、空 `article-urls`，当前周的真实只读结果为 6 篇：

| 当前周轮换候选 | 正文字符数 |
| --- | ---: |
| [Homage to Spain (and its style)](https://www.permanentstyle.com/2026/08/homage-to-spain-and-its-style.html) | 21,415 |
| [How To Shop On A Budget](https://putthison.com/how-to-shop-on-a-budget/) | 16,284 |
| [The English nonchalance of David Hockney](https://www.permanentstyle.com/2026/06/the-english-nonchalance-of-david-hockney.html) | 5,838 |
| [How To Think About Color Temperature](https://putthison.com/how-to-think-about-color-temperature/) | 6,244 |
| [The style of Tintin and Hergé](https://www.permanentstyle.com/2026/06/the-style-of-tintin-and-herge.html) | 6,559 |
| [Spring/Summer Sport Coat Guide](https://www.putthison.com/spring-summer-sport-coat-guide/) | 18,691 |

专题订阅不等同于每条都适合当前应用：预算购物、文化风格解读仍需归纳质量门槛筛选；配色温度与运动夹克教程提供了明确可核读的方法文。此轮仍未调用归纳模型，也未把候选数视为发布成功。

## 配置与边界

- `app.techniques.feeds`：未配置时使用上述两个最终专题 RSS；显式空字符串关闭订阅发现。也支持配置公开教程列表页、RSS 或 Atom。最多 6 个入口。
- `app.techniques.article-urls`：可选、由服务器管理员配置的常青教程地址，最多 12 个。可将上表地址作为启动候选；它们不是每周新发布的文章。
- 每轮最多尝试 20 个文章地址，最多返回 6 篇；每次请求最多 1,500,000 bytes；单篇可提取正文在 1,200 到 24,000 字符之间，且至少有 5 段达到 80 字符。
- 完整正文仅作为归纳时的临时证据。采集器不提供全文公开接口，不以 RSS 摘要代替文章正文，不静默截断过长文章。
- 跳过识别到的购物清单、广告、付费墙、继续阅读登录提示，以及响应头或页面元数据中的 `noai`、`noarchive`、`nosnippet`。未取得充分来源时由上层保留旧内容，不为凑周更编造发布。
- 仅请求无凭据的 HTTPS 公网地址，端口只能为默认或 443。发现链接限于配置来源域及其子域；逐个 DNS 结果排除本机、内网、链路本地、CGNAT 与非公网 IPv6。每次跳转重新验证，最多 3 次，响应正文有硬上限。
- 网络安全边界：实现采用现有 Jsoup 传输方式，DNS 校验发生在连接之前，但没有自制 DNS 固定的 TLS/HTTP 客户端。对能恶意控制 DNS 的配置来源，部署仍应通过出站网络策略禁止访问本机和私有网段，防止解析与连接之间的 DNS 重绑定。配置来源应限定为可信出版站点。

## 离线验证

`TechniqueSourceCollectorTest` 共 14 项通过，覆盖 RSS / Atom 发现后独立正文读取、未知元数据、JSON-LD、单源失败隔离、HTML 列表页与重定向、域名和公网 DNS 限制、私有目标拦截、字节与文章数量上限、过长正文拒绝、跟踪参数处理、短限定句和旧版 `div` 正文的完整性，以及上海周日 23:59:59 到周一 00:00 的确定性轮换和跨站交错。

这些检查证明采集组件可以取得可追溯的正文证据；并不证明任意一周的候选都足以形成有质量的新技巧。是否发布必须继续由归纳验证、主题去重和周更频率控制共同决定。
