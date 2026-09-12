# 抖音视频／图集解析 API

这是一套可在本机或内部服务器运行的 Python API，供 Java 后端调用。不是抖音官方开放平台接口，也不使用第三方付费解析服务。

`分享链接 → 提取作品 ID → 生成请求参数 → 抖音作品详情接口 → 视频／图集地址`

API 请求不启动浏览器、不抓取网页 DOM。浏览器仅用于会话初始化或失效后的人工更新。请求参数工具来自 [Evil0ctal/Douyin_TikTok_Download_API](https://github.com/Evil0ctal/Douyin_TikTok_Download_API) 的固定提交，来源、修改与 Apache-2.0 许可见 [NOTICE](vendor/dtk_signing/NOTICE.md)。没有部署该项目整套 PostgreSQL、Redis 和身份池。

## 启动

在此目录执行，使用 Python 3.12。Windows 将 `python3` 替换为 `python`，激活命令替换为 `.venv\Scripts\activate`。

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-api.txt
python -m playwright install chromium
python setup_session.py
python api.py
```

也可以使用 `启动API.command`（macOS）或 `启动API.bat`（Windows）。首次会打开独立浏览器；如出现登录或验证，请手动完成，回到终端按回车保存会话。平时启动 API 不会重复打开浏览器。会话失效时再手动运行 `setup_session.py`。

- 文档：<http://127.0.0.1:8766/docs>，点击 **Authorize** 输入本地 `.api-key` 文件中的密钥。
- 密钥默认自动生成并保存到 `.api-key`，重启不变，也可用 `DOUYIN_API_KEY` 设置至少 24 字符的值。
- 会话：`.api-session.json`；下载目录：`.api-data/`；浏览器配置：`.api-profile/`。
- 以上路径已加入 `.gitignore`，不得提交会话、密钥和媒体文件。
- 默认只监听本机。Java 和 Python 同机运行时可直接调用；容器或其他机器访问需自行配置地址、网络及 HTTPS。不要将服务密钥嵌入 Vue 前端。

## 接口

除 `/healthz`、接口文档外，所有接口均需要 `Authorization: Bearer <API_KEY>`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /healthz` | 进程健康检查，不代表抖音可用 |
| `GET /v1/status` | 会话配置状态，不代表在线会话有效 |
| `POST /v1/parse` | 解析单条分享文本，返回媒体直链 |
| `POST /v1/downloads` | 提交后台下载任务，返回 HTTP 202 和任务 ID |
| `GET /v1/downloads/{id}` | 查询任务状态、文件和失败信息 |
| `GET /v1/downloads/{id}/files/{name}` | 获取已下载文件，也需要 Bearer 鉴权 |

### 解析

```bash
curl -sS http://127.0.0.1:8766/v1/parse \
  -H "Authorization: Bearer $(cat .api-key)" \
  -H 'Content-Type: application/json' \
  -d '{"text":"https://v.douyin.com/uEtNiJS9T2g/"}'
```

以下是结构示意，示例媒体地址不可下载：

```json
{
  "data": {
    "id": "7683406450030431498",
    "title": "作品标题",
    "author": "作者昵称",
    "type": "视频",
    "source_url": "https://www.douyin.com/video/7683406450030431498",
    "provider": "douyin_web_api",
    "parsed_at": 1789189304,
    "assets": [{"kind": "video", "urls": ["https://example.invalid/video.mp4"]}]
  },
  "notice": "媒体直链可能过期或需要请求头；需要稳定文件时请创建下载任务。"
}
```

图集的 `type` 为 `图集`，`assets` 按图片顺序排列，每项 `kind` 为 `image`。`urls` 是同一个媒体文件的候选地址，不是多张图片。平台 ID 始终使用字符串，避免 JavaScript 大整数精度问题。不保证任意作品无水印或最高画质。

### 下载和取文件

向 `/v1/downloads` 发送同样的 JSON，得到 `data.id`。每隔 1–2 秒查询 `/v1/downloads/{id}`：

- 进行中：`queued`、`parsing`、`downloading`。
- 结束：`done`（全部成功）、`partial`（部分成功）、`error`。
- 成功文件在 `data.files`，包含文件名、字节数、类型及相对路径 `download_url`。
- 将 `download_url` 拼在 API 服务地址后，用同一 Bearer 密钥请求文件。不要让 Java 服务任意代理用户提交的 URL。

任务状态保存在 `.api-data/<id>/job.json`，不保存 Cookie 和临时媒体直链。服务重启后可继续获取已完成的文件，未完成任务会标记为 `INTERRUPTED`，需重新提交。当前不提供取消 API、断点续传或自动清理；文件保留到管理员停止服务后手动归档。

### Java 17 示例

使用 JDK 自带 `HttpClient`，无需修改 Spring Boot 依赖。`DOUYIN_API_KEY` 是 Python API 的密钥，Java 服务通过环境变量取得；下面的示例也可以将 URI 改成 `/v1/downloads`。

```java
var client = java.net.http.HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(5)).build();
var payload = new com.fasterxml.jackson.databind.ObjectMapper()
    .writeValueAsString(java.util.Map.of("text", shareText));
var request = java.net.http.HttpRequest.newBuilder()
    .uri(java.net.URI.create("http://127.0.0.1:8766/v1/parse"))
    .timeout(java.time.Duration.ofSeconds(80))
    .header("Authorization", "Bearer " + System.getenv("DOUYIN_API_KEY"))
    .header("Content-Type", "application/json")
    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build();
var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
// 先检查 response.statusCode()：200 才是解析成功；202 表示下载任务已接收。
// 429 表示忙或队列已满，稍后重试；其他错误根据 error.code 显示明确提示。
```

## 配置与限制

| 环境变量 | 默认值 / 用途 |
| --- | --- |
| `DOUYIN_API_KEY` | 未设置时读取或生成 `.api-key` |
| `DOUYIN_SESSION_FILE` | `.api-session.json`；用 `setup_session.py --output` 指定保存位置 |
| `DOUYIN_OUTPUT_DIR` | `.api-data` |
| `DOUYIN_PROXY` | 默认直连；有需要时设置如 `http://127.0.0.1:7897`，配置会话和解析时应一致 |

端口使用 `python api.py --port 8766` 调整。只运行一个 API 进程，不能使用多个 Uvicorn workers 共享同一状态目录。每次只处理一个上游解析／下载任务；下载队列含运行任务最多 3 个，解析遇到繁忙返回 429。累计任务最多 1000 个，到达后需停止服务、归档旧任务目录并重启。

短链接最多跳转 6 次、每次超时 8 秒，详情请求超时 20 秒；单文件上限 512 MB，单候选下载限时约 5 分钟（网络读操作可能额外等待最多 20 秒）。整个图集任务可能更久。没有承诺批量成功率；平台会话、访问限制和接口参数变化仍会造成失败。

错误响应示例：

```json
{"error":{"code":"SESSION_REQUIRED","message":"请先运行 setup_session.py 配置或更新抖音会话。"}}
```

常见错误：`INVALID_URL`（400）、`UNAUTHORIZED`（401）、`BUSY` / `QUEUE_FULL`（429）、`SESSION_REQUIRED`（503）、`UPSTREAM_REJECTED` / `UPSTREAM_CONNECTION` / `MEDIA_UNAVAILABLE`（502）。收到上游拒绝后不要连续重试，应检查作品可见性、网络和会话。不会自动处理验证码或访问受限作品。

## 验证记录

2026-09-12，在 macOS、Python 3.12 上：

- 40 项离线测试通过，包含原桌面工具测试、API 鉴权、错误状态、跳转限制、下载任务、队列上限、重启恢复和图集顺序／部分失败。
- 用户提供的 `https://v.douyin.com/uEtNiJS9T2g/` 经 `/v1/parse` 返回 HTTP 200，单次约 0.63 秒；解析过程不启动浏览器。这是单样本耗时，不是性能承诺。
- `/v1/downloads` 实际下载作品 `7683406450030431498`，状态 `done`，MP4 为 15,621,678 字节。
- 文件下载接口返回 HTTP 200，内容 SHA-256 与落盘文件一致，MP4 的 `ftyp`、`moov`、`free`、`mdat` 顶层结构及完整长度检查通过。
- 图集当前为离线样例验证，尚无用户提供的真实图集测试；未验证 Windows 实机启动和 Java/Vue 端到端集成。
- Java/Vue 业务代码未改动。本次未重复运行此前通过的后端 26 项测试及前端构建。

运行离线检查：

```bash
python -m pip install -r requirements-api.txt
python -m unittest discover -s tests -v
```
