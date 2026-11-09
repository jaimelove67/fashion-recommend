# 本地前端更新发布记录（2026-11-06）

- 发布状态：已激活，时间为北京时间 2026-11-06 08:47:16。
- 发布编号：`frontend-local-20261106-01a1ae4a`。
- 网站：`https://www.jaime.ltd/`。
- 当前源码目录：`/opt/zhiji/releases/frontend-local-20261106-01a1ae4a`；`/opt/zhiji/current` 已原子切换到此目录。
- 基础提交：`7f90d2f1395db0fb41b03e2430d4f702f797fdf1`。本次部署包含未提交工作区和新增文件，未创建 Git 提交或推送；实际制品以发布清单和镜像 ID 为准。

## 发布内容

本地已删除“穿搭内容 / 分析示例”切换、原有筛选和下方分析区，但此前线上仍运行含这些控件的版本。本次将当前前端完整构建发布，包含点击穿搭卡片打开介绍弹窗、优先展示具有已核验全身图片的卡片等当前工作区更新。

同步前端、扩展及文档源码快照，共 190 个文件，其中 90 个文件需要相对服务器更新；构建产物共 57 个文件。对比当前线上静态资源后，仅需新增 JS、CSS 并替换首页入口，增量包为 449362 字节。扩展下载保持 0.1.3，哈希与本地包一致。

只重建前端容器。后端、采集器、PostgreSQL、MinIO 的容器 ID 和镜像 ID 均与发布前一致；未执行数据库迁移或修改 TLS 配置。新旧 Compose 配置在规范化发布目录后完全一致。新前端镜像保留旧版哈希静态资源，供已打开的旧页面继续读取。

## 制品

- 新镜像：`fashion-recommendation-cloud-frontend:frontend-local-20261106-01a1ae4a`。
- 新镜像 ID：`sha256:9bdfbb58c865c8cfa583c24440fbfaf1f3563d35694e34d41f190bbd94a71e78`。
- 回滚镜像：`fashion-recommendation-cloud-frontend:before-frontend-local-20261106-01a1ae4a`。
- 旧镜像 ID：`sha256:2f9c103f3c2eef6da321a75c885defd061cae6382bc0e63e6b64571bffee926c`。
- 旧源码目录：`/opt/zhiji/releases/history-7f90d2f-20261105-58ce44`，保留未覆盖。
- 操作目录：`/opt/zhiji/shared/frontend-releases/frontend-local-20261106-01a1ae4a`，包含脚本、源码与构建清单、增量包、状态和部署结果；本机副本位于 `.codex_tmp/frontend-publish-20261106/`。

| 文件 | SHA-256 |
| --- | --- |
| `index.html` | `178ca2e5cebe32660e337f99700ca95250dc6a60b22cca46931afa3b41a12dca` |
| `assets/index-B7P5Hid6.js` | `b507d6561c7efed6a1421befd9ed7a68f127671be183f7f89f304bec760cf598` |
| `assets/index-LxGw-ltU.css` | `469e775afd3aa943ca3053d737bc72dfcc4099b50f988dbd0e67c8aaf2e3c461` |
| `downloads/zhiji-try-on-extension.zip` | `7489346bccf2ea38635aec269d6ce4c06135589ab3a3bac6221e63edb69160c7` |

## 验证及范围

- 前端单元测试 127 项通过，生产构建通过。Windows 沙箱内最初出现 `spawn EPERM`，在沙箱外重跑通过。构建仍有现有的大体积 JS 分块提示。
- 对生产构建执行 30 项适用的浏览器回归，通过桌面与手机端的按钮移除、历史收录内容介绍弹窗、空态、来源说明、试穿安装连接及真实扩展加载交互。测试使用模拟账户、购物站点和试穿协议；未调用真实摄像头或模型。首次扩展用例缺少浏览器路径和预览地址，补充环境变量后全部通过。
- 没有执行完整 E2E 套件。旧深度分析和单品收藏用例依赖本次已移除的界面，未计入上述 30 项通过结果；首次混合运行中的相关旧界面断言失败后停止该批次，重新选取适用用例验证。
- 发布镜像内全部 57 个新构建文件逐一通过 SHA-256 校验；实际入口 JS 不包含 `trend-content-modes` 或 `showAnalysisExamples`，并包含新介绍弹窗。
- 服务器侧验证 www 与根域名 HTTPS 首页、入口 JS/CSS 和扩展下载哈希；Nginx 配置通过；CSRF 响应正常；后端健康 `UP`；发布前后趋势接口均为相同的 5 条已收录内容；其他服务身份保持一致。
- 服务器通过公网域名再次读取首页，哈希与本地构建相同。公网 Chromium 无需忽略证书错误即可打开新站点并加载 `index-B7P5Hid6.js`。本机 Python/.NET 的独立请求出现证书链校验异常；服务器 HTTPS 校验通过，线上叶证书有效期为 2026-10-31 至 2027-01-29，本次没有调整证书或关闭 TLS 验证。
- 公网 Chromium 在 1280px、390px 下读取真实线上资源和趋势接口，配合拦截的模拟登录态与空个人数据，确认切换按钮及下方分析区已移除、键盘打开穿搭介绍并按 Esc 关闭、页面无横向溢出，且无页面脚本异常。没有使用真实账户。截图跳过外部字体就绪等待；最初截图因字体请求等待超时，随后调整检查脚本完成；对移动中边缘卡片的强制点击未能激活弹窗，改用聚焦当前卡片并按 Enter 完成复核。

## 回滚

先核对当前仍运行本次发布版本，再执行：

```bash
python3 /opt/zhiji/shared/frontend-releases/frontend-local-20261106-01a1ae4a/deploy.py rollback
```

脚本通过发布锁与当前镜像、其他服务身份检查，恢复旧镜像并仅重建前端，将 current 指针切回旧源码目录，随后核对原首页哈希。切换异常会自动尝试同样的恢复；本次未切回旧版演练回滚。
