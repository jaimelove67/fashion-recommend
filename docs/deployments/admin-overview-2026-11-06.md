# 管理工作台前端发布记录

2026-11-06 10:33:55（北京时间），发布 `admin-overview-20261106-01a1ae4a`。管理员登录后默认进入管理工作台；“返回用户端”继续可用。运营总览改为连续简报布局，调整数据、提醒、图表和近期操作的层级；推荐记录采用真实数量的横向条形图，提醒按钮去除默认粗黑边。没有更改后端和数据。

- 基础提交：`7f90d2f1395db0fb41b03e2430d4f702f797fdf1`，包含本地未提交变更。没有创建或推送 Git 提交。
- 当前源码：`/opt/zhiji/releases/admin-overview-20261106-01a1ae4a`；旧版本 `/opt/zhiji/releases/frontend-local-20261106-01a1ae4a` 保留。
- 前端镜像：`fashion-recommendation-cloud-frontend:admin-overview-20261106-01a1ae4a`，ID `sha256:dec2f74c5896804e6c94daea72af232c498264882bb37d0df3ee7b37b220bc6e`。
- 回滚镜像：`fashion-recommendation-cloud-frontend:before-admin-overview-20261106-01a1ae4a`，ID `sha256:9bdfbb58c865c8cfa583c24440fbfaf1f3563d35694e34d41f190bbd94a71e78`。
- 服务器操作目录：`/opt/zhiji/shared/frontend-releases/admin-overview-20261106-01a1ae4a`，其中包含部署脚本、清单、增量包和权限受限的 `iso-live-test-20261106.md` 测试记录。
- 更新源码为 `AdminView.vue`、`useFashionApp.js` 与两份管理员浏览器测试；新制品为首页入口、`assets/index-Cbv6voD7.js` 和 `assets/index-oiCqnMCW.css`。扩展 ZIP 保持之前版本。

## 检查

前端生产构建与 127 项单元测试通过；11 项管理员导航、图表、技巧管理和图片复核浏览器测试通过，使用模拟数据。1280、768、390、320 px 的提醒栏、焦点和跳转检查通过。服务器新镜像全部 57 个构建文件哈希验证通过；两个域名的 HTTPS 首页、JS、CSS、扩展 ZIP、CSRF、趋势接口和后端健康检查通过。

公网隔离浏览器使用真实管理员登录验证：从 `#recommend` 登录后自动进入 `#admin`，导航只显示管理后台，提醒项无左右粗边；“返回用户端”回到推荐页并恢复用户导航。测试会话已退出。其他服务容器 ID 和镜像 ID 未变；发布前后 `iso` 的衣橱、搭配记录、反馈、照片计数均为 4、2、1、1。未再执行真实 AI 模型或摄像头试穿。

## 回滚

核对当前版本及后续发布依赖后执行：

```bash
python3 /opt/zhiji/shared/frontend-releases/admin-overview-20261106-01a1ae4a/deploy.py rollback
```

该脚本检查镜像、其他服务和 current 目录，只重建前端并恢复旧指针；本次没有实际演练回滚。
