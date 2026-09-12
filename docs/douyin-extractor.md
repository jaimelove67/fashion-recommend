# 抖音素材提取：当前范围与接入方式

## 当前交付

2026-09-12 新增独立 [HTTP 解析 API](../tools/douyin-extractor/API.md)：复用 DTK 的三个签名工具文件，直接请求抖音作品详情接口，提供 Bearer 鉴权、异步下载、持久化任务状态及文件下载。浏览器仅用于首次配置／更新本地会话，不参与日常 API 请求。未部署 DTK 的完整服务，也未接入付费解析平台。

`tools/douyin-extractor/` 是可单独运行的 Python 工具，包含本地网页、串行任务队列、Playwright 浏览器采集、媒体下载及测试。输入单条作品分享链接，输出视频或图集文件及 `metadata.json`。

本次没有修改原有 Java/Vue 业务逻辑，也没有将工具接入“风潮”页面。它不包含自动搜索、账号批量采集、视频抽帧或穿搭识别。

## 验证

在工具目录中运行：

```bash
python -m pip install -r requirements-api.txt
python -m unittest discover -s tests -v
```

测试使用构造样例，验证目标作品隔离、图集解析、媒体文件头、下载长度、备用地址和取消。在线采集不应作为 CI 的必过条件，因为平台登录状态、验证码、网络和作品可用性不受项目控制。

本分支新增本地 HTTP API 测试，共 18 项离线测试；GitHub Actions 配置在 Linux、macOS 和 Windows 上运行这些测试，不访问抖音、不安装浏览器。

上述 18 项为 2026-09-10 的浏览器版测试记录。API 版测试需先安装 `requirements-api.txt`；当前测试数量和在线验证结果见 [API 验证记录](../tools/douyin-extractor/API.md#验证记录)。CI 安装 Python 依赖，不下载 Chromium，也不访问抖音。

2026-09-10 分支验证结果：

| 检查 | 结果 |
| --- | --- |
| 提取工具 `python -m unittest discover -s tests -v` | 18 项通过 |
| 原项目前端 `npm ci`、`npm run build` | 安装及生产构建通过 |
| 原项目后端 `mvn clean test`，Temurin JDK 17 | 26 项通过，0 失败、0 错误、0 跳过 |
| `git diff --cached --check` | 通过 |

本机预装的 JDK 25 不被原项目的 Byte Buddy 测试依赖支持，验证时改用 JDK 17，未修改 Java 依赖。前端 `npm audit` 报告原有 `nanoid`、`postcss` 两项高危依赖，本分支未修改前端依赖或 lockfile。完整 Docker/E2E 流程未执行，因为本机没有 Docker 运行环境。

开发期在 macOS 实测用户提供的短视频链接，成功保存 15,621,678 字节的 MP4，作品 ID 为 `7683406450030431498`，视频容器时长约 69.6 秒。文件头、响应长度和 MP4 顶层结构检查通过。视频文件、浏览器登录状态和环境目录不纳入版本控制。

尚未完成真实图集在线验证、Windows 实机启动验证和与现有穿搭系统的端到端联调。

## 推荐集成路径（待实现）

1. 在 Spring Boot 中新增导入任务接口，将任务 ID 和用户 ID 保存到 PostgreSQL。
2. Java 调用已提供的 Python API 提交任务；服务使用 Bearer 密钥并保存任务状态。Java 侧仍需实现用户权限、任务与用户的关联、超时轮询，以及生产部署配置。原桌面网页的临时 `X-Local-Token` 不用于新 API。
3. 下载成功后，把文件上传到 MinIO，数据库保存对象键、平台作品 ID、原始来源及处理结果。
4. Vue 增加分享链接输入、任务进度、失败提示和素材预览。
5. 视频增加抽帧和去重，图片调用视觉模型，把识别结果作为参考穿搭传给现有推荐逻辑。

当前浏览器需要图形界面，在登录或验证时允许用户手动操作。服务器部署必须另外设计浏览器会话维护及人工介入方式；仅将 `headless` 设为 `true` 无法保证无人值守采集。

素材属于外部参考，不应自动加入用户实际拥有的衣橱。保留来源、失败状态及人工修正入口，并按用户授权范围处理媒体。
