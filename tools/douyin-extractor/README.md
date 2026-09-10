# 抖音视频 / 图集提取工具

本地运行的小程序。粘贴一条抖音分享文本或作品链接，保存视频文件或图集中的每张图片，并生成作品信息 `metadata.json`。

## 启动

需要 Python 3.10 或更新版本，以及可访问抖音的网络。首次安装浏览器需要下载约 200–400 MB 文件。

- **macOS**：双击 `启动.command`。如果系统没有可用的 Python，请先从 https://www.python.org/downloads/ 安装。
- **Windows**：安装 Python 时启用 Python Launcher，然后双击 `启动.bat`。
- **命令行**：

```bash
python3 -m venv .venv
# macOS / Linux
source .venv/bin/activate
# Windows 对应命令：.venv\Scripts\activate
python -m pip install -r requirements.txt
python -m playwright install chromium --no-shell
python app.py
```

打开 http://127.0.0.1:8765 。端口被占用时使用 `python app.py --port 8766`。
Linux 如缺少浏览器系统库，参照 Playwright 官方安装说明安装依赖： https://playwright.dev/python/docs/intro 。

## 使用

1. 在抖音作品中选择“分享 → 复制链接”。支持 `v.douyin.com` 短链接、`/video/` 视频、`/note/` 图集，以及带 `modal_id` 的作品链接。
2. 把整段分享文本粘贴到页面，点击“提取并保存”。每次输入一条作品链接。
3. 程序打开自己的 Chromium 浏览器。若出现登录或验证提示，请在该浏览器中手动完成。程序会继续等待作品数据，最长约 3 分钟。提取期间不要切换到其他作品。
4. 成功后点击“打开保存文件夹”；任务卡片中也有文件下载入口。

文件按任务保存到 `downloads/<任务编号>/`，例如：

```text
downloads/6eac0c48d0b13234/
  001.mp4                # 视频，或 001.webp、002.webp 等图集文件
  metadata.json          # 作品 ID、标题、作者、来源、提取时间、文件列表、失败信息
```

图片保留服务器实际返回格式，可能是 JPEG、PNG、WebP、AVIF 或 HEIC，不强制改成 JPG。图集中的实况图只保存静态图，不提取附带动态视频。视频保存页面提供的可用版本，不承诺最高画质或无水印，不做水印移除。

## 实现与边界

- `app.py`：仅绑定本机的 HTTP 页面和任务队列，串行处理，最多 3 个进行中／排队任务。
- `extractor.py`：用 Playwright 打开用户提供的作品页，读取页面正常返回的 JSON 响应或页面初始数据；严格匹配目标作品 ID，防止保存推荐流中的其他作品。
- 支持常见蛇形及驼峰字段、图集列表和嵌套 `image_post_info`。抖音更改数据结构后可能需要调整解析器。
- 下载采用流式写入、备用地址尝试、文件头和长度校验、临时文件原子替换。单文件上限 512 MB，单文件每次地址尝试最长约 5 分钟；链接逐项尝试，取消会在当前网络读操作结束后生效。
- 部分图片失败会显示“部分完成”，保留已成功文件，并在 JSON 中写明失败序号。重复提交会生成新的任务文件夹。
- 登录状态保存在本目录 `.browser-profile/`，不读取日常浏览器的 Cookie，不发送给第三方解析服务；不要分享这个目录。关闭程序后可删除该目录以退出本工具登录。
- 浏览器和下载请求使用本机已配置的 HTTP/HTTPS 代理（如有），程序不修改系统代理设置。
- 页面任务记录仅保留到程序退出；下载文件持久保留。停止程序后可能遗留 `.part`，可以删除。不要同时启动两个实例共用同一个目录。
- 私密、已删除、地区受限或平台拒绝访问的作品可能无法获取。登录或验证需用户完成；程序不自动绕过验证。
- 本工具用于保存你有权处理的内容，不包含搜索、批量账号抓取、直播、视频抽帧或穿搭识别。

## 测试

```bash
python -m unittest discover -s tests -v
```

测试中的数据是明确构造的测试样例，覆盖图集／视频解析、目标作品隔离、失效地址切换、下载完整性和取消。它们不代表真实抖音在线成功率。实际平台兼容性需要用当前可访问的作品链接验证。

2026-09-10 本机实测：用户提供的短链接 `https://v.douyin.com/uEtNiJS9T2g/` 成功解析为作品 `7683406450030431498`，保存 15,621,678 字节的 MP4 和作品信息 JSON；下载长度、文件头及 MP4 顶层结构检查通过。18 项离线测试通过，覆盖解析、下载及本地 HTTP API；本地网页提交、错误提示和完成结果展示已验证。尚未使用真实图集链接做在线下载验证；Windows 启动脚本尚未在 Windows 实机验证。实测视频不包含在版本控制内。

## 后续接入 Java 项目

建议先使用 `metadata.json` 和下载文件对接。当前 HTTP API 是本地页面接口：`POST /api/jobs` 提交 `{ "text": "分享文本" }`，`GET /api/jobs` 查看状态，`POST /api/cancel` 取消 `{ "id": "任务编号" }`。接口要求页面中本次启动生成的 `X-Local-Token`，不是面向公网的生产接口。

代码独立实现，运行依赖为 Playwright（Apache-2.0）；不依赖 TikTokDownloader 或收费解析 API。浏览器由 Playwright 官方安装器获取。参考文档：

- https://playwright.dev/python/docs/network
- https://playwright.dev/python/docs/api/class-browsertype#browser-type-launch-persistent-context
