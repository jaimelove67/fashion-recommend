# 公开图文采集

`collect_public.py` 用 Scrapling 普通 HTTP 请求读取微博公开详情页、小红书公开穿搭频道和 VOGUE 中国时髦学栏目。只请求 HTML，不请求或下载视频、图片文件，不登录、不处理验证页。

只接受有图片、有可核实发布时间、发表于最近七天且与穿搭有关的图文。小红书视频卡片在进入详情前排除；读取失败、日期不明、旧内容和带视频的文章只记录失败原因。互动数和全身照审核状态在无法取得时保留为未知。

在独立虚拟环境安装 `requirements-public.txt`，运行 `python collect_public.py`。默认结果写入 `data/public-image-posts/feed.json`，采集状态写入同目录的 `report.json`。脚本只生成候选数据，不覆盖原有平台导入文件。微博默认种子链接是本次验证的有限样本，不能视为全平台搜索或完整覆盖；需要采集其他博主时应换成其可公开访问的原帖链接。

旧候选脚本与每日自动采集服务分开运行。后端默认连接每日采集服务，支持微博、抖音和小红书；取消端点配置后才读取对应的本地导入文件。候选脚本中的出版物内容不作为日常穿搭帖子导入。页面顶部全身图画廊另外要求经过确认的全身照，本工具不会自动添加该确认。

验证：`python -m unittest discover -s tools/trend-collector -p test_collect_public.py -v`（从项目根目录运行）。

内容排除规则检查标题、作者和完整正文，再生成短摘要；排除时装周、秀场、红毯与含明星、艺人、演员、歌手、演唱会等明确线索的内容。完整正文只用于筛选，不输出到 feed。普通合格图文通过趋势页内容卡片展示，不依赖全身照核验字段。

## 每日自动搜索

`daily_collector.py` 使用项目固定版本的 Scrapling `DynamicFetcher` 搜索微博按时间排序的“穿搭”帖子，以及抖音穿搭搜索页面实际加载的公开搜索响应。使用独立无头浏览器，不要求用户开启远程调试，不读取用户浏览器配置、Cookie 或私密响应。只接受最近24小时、可核实发布时间、有图片且符合穿搭规则的帖子；不自动宣称全身照已审核。

Compose 的 `trend-collector` 服务只在内部网络提供 `/weibo.json`、`/douyin.json`。后端启动后抓取一次，之后北京时间每天12点调用这两个来源。服务按平台原子写入 `data/<platform>.json` 和 `data/<platform>-collection-status.json`。成功但无合格帖子返回空列表；登录墙、验证页、结构无法解析或网络失败返回503并保留上次文件，不修改旧条目的时间。后端预置样本回退在本地环境关闭。

启动：`docker compose --profile app up -d --build trend-collector backend frontend`。本地实测：`tools/trend-collector/.venv/Scripts/python.exe tools/trend-collector/daily_collector.py --real-chrome --output output/scrapling-trends`。Windows的`--real-chrome`由Scrapling启动已安装Chrome的独立实例；Docker默认使用安装的Chromium。

目前匿名公开搜索可能被平台跳转到登录/验证页，Scrapling不能保证每天都有可用结果。采集状态记录实际失败原因。必须先验证平台可用性，不能将服务启动或HTTP200视为采集成功。

采集范围：平台不设硬性名单，优先微博、抖音和小红书的日常穿搭图文。`daily_collector.py` 同时搜索小红书图文，最多读取3条正常图文详情验证发布时间。视频、纯文字、时装周、秀场、红毯、明星、杂志大片均不导入；后端展示层也检查媒体类型，以排除已存旧视频。其他平台可通过通用来源接口接入，画廊对平台无硬性名单，但仍要求已审核的全身照片。
