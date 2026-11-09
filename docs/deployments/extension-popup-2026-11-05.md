# 跨站试穿扩展 0.1.3 发布记录

## 结果与范围

- 发布时间：2026-11-05 14:01:49（北京时间）。
- 发布编号：`extension-popup-20261105-01a1aa33`。
- 正式下载：`https://www.jaime.ltd/downloads/zhiji-try-on-extension.zip`，根域名同一路径也提供相同文件。
- 扩展版本从 `0.1.2` 更新为 `0.1.3`。修复账号未连接时点击“在本站启用试穿浮窗”会静默关闭菜单的问题，现在保留菜单并提示返回项目趋势页开启连接；补充项目地址绑定与账号连接的区别。
- 原因：后台 `SHOW_CURRENT` 在未连接时返回 `{ enabled: false }`，菜单只检查外层消息是否成功，因此仍执行关闭。现在通过错误响应进入菜单已有的可见错误路径。
- 生产静态资源只替换 `downloads/zhiji-try-on-extension.zip`；基于正在运行的前端镜像构建增量镜像，没有上传本地其他前端改动。
- 只重建前端容器。后端、采集器、PostgreSQL 和 MinIO 的容器 ID 保持不变；没有执行数据库迁移、修改凭据或修改 HTTPS 配置。
- `/opt/zhiji/current` 仍指向 `/opt/zhiji/releases/technique-dbc703d-20261105-bb9ae6`。该目录内的 `frontend/public/downloads/zhiji-try-on-extension.zip` 也已原子替换，供后续构建使用。

## 制品与备份

- 发布目录：`/opt/zhiji/shared/frontend-releases/extension-popup-20261105-01a1aa33`。
- 新扩展 ZIP SHA-256：`7489346bccf2ea38635aec269d6ce4c06135589ab3a3bac6221e63edb69160c7`。
- 旧扩展 ZIP SHA-256：`a16cd3ca75ce3280198466d58a0047688e94d3a2d586425ac45271df4280f33c`。
- 新前端镜像：`fashion-recommendation-cloud-frontend:extension-popup-20261105-01a1aa33`。
- 新镜像 ID：`sha256:3aeea182c4492d45a83e1ef7474136c5d8c7218abac421a58673c69c6dffaa8a`。
- 旧镜像保留为 `fashion-recommendation-cloud-frontend:before-extension-popup-20261105-01a1aa33`，ID 为 `sha256:5cd8b6e89e028388d13d5c520ec050155167af878afbf30bee9086766ac4474b`。
- 新镜像已更新现有 Compose 使用的前端镜像别名。发布目录保留旧 ZIP、旧首页、发布前后静态文件哈希清单、部署脚本和 `deployment.json`；本机副本位于忽略目录 `.codex_tmp/cloud-deploy/extension-popup-20261105-01a1aa33/`。
- 基础本地 Git 版本为 `dbc703d75d97686ce62e2fc3edacd6d8ecedef59`，扩展补丁来自本次已验证的工作树，未创建或推送 Git 提交；实际发布制品以 ZIP 哈希和镜像 ID 为准。

## 验证

- 发布前：前端单元测试 127 项通过；跨站扩展、安装连接引导、项目内试穿浏览器回归最终 32 项通过；前端生产构建通过。浏览器测试使用隔离配置、模拟购物网站及试穿服务，未调用真实模型或摄像头。首次完整检查有一例首帧等待超时，复测通过。
- 新增菜单回归先在旧实现复现未连接时静默关闭，再验证修复后保留错误提示；已连接时通过实际扩展菜单文档点击，在购物测试页显示真实浮窗。原生工具栏气泡与权限确认框仍由用户操作，测试站点权限预先授予。
- 服务器新旧静态文件集合一致，逐文件 SHA-256 对比确认只有扩展 ZIP 改变，首页哈希仍为 `8947a4b14df44de3a548bd74aa7a08543063e746e17852c126a924654edc94d6`。
- 新镜像配置预检、运行容器 `nginx -t`、两个域名的 HTTPS 首页和扩展下载、CSRF 接口及后端健康 `UP` 均通过。
- 从本机公网分别下载 www 与根域名的无版本参数 ZIP，均为 HTTP 200、版本 `0.1.3`、13 个文件，完整 ZIP 哈希与本地测试包一致；未关闭 TLS 证书验证。公网首页哈希保持原值。
- 公网 CSRF 接口返回有效会话响应，校验时未输出令牌。未使用真实账号登录、上传照片或调用模型服务。
- 扩展仍无默认全站访问权限或默认内容脚本，保留用户逐站授权方式。

## 浏览器更新

线上 ZIP 更新不会自动替换已加载的解压扩展。用户需重新下载并解压覆盖原扩展目录，在浏览器扩展管理页重新加载，确认版本为 `0.1.3`；刷新项目及目标网页，再到知己趋势页点击“开启跨站试穿”，保留项目标签页。

## 回滚

确认当前仍运行本发布版本且没有后续发布依赖后，在服务器执行：

```bash
python3 /opt/zhiji/shared/frontend-releases/extension-popup-20261105-01a1aa33/deploy.py rollback
```

脚本使用前端发布锁，检查当前发布目录、镜像及其他服务容器，恢复旧前端镜像别名、旧源码 ZIP，并仅重建前端容器。切换异常时也会自动尝试同样的恢复。回滚脚本已做语法检查和前置条件审查，本次未切回旧版本做线上演练。
