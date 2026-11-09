# 七日时间线前端发布记录

2026-11-06 发布 `history-timeline-20261106-01a1ae4a`，将搭配记录中的近七天收藏统计改为时间线。每天显示搭配总数、已收藏和未收藏数量；统计仍按已加载记录的生成日期归类，使用当前收藏状态。

- 当前发布目录：`/opt/zhiji/releases/history-timeline-20261106-01a1ae4a`；发布前目录：`/opt/zhiji/releases/admin-overview-20261106-01a1ae4a`。
- 前端镜像：`fashion-recommendation-cloud-frontend:history-timeline-20261106-01a1ae4a`，ID `sha256:064be60dc897d7c756744fcf92371f44ccc4acab7eabfcf6b65f05335116e6b0`。
- 发布操作目录：`/opt/zhiji/shared/frontend-releases/history-timeline-20261106-01a1ae4a`。回滚镜像标签为 `fashion-recommendation-cloud-frontend:before-history-timeline-20261106-01a1ae4a`。
- 在原线上源码基础上更新 `frontend/src/views/HistoryView.vue` 和时间线浏览器测试；增量静态资源为入口 HTML、新 JS 和 CSS。没有提交或推送 Git。
- 本地生产构建通过；使用模拟数据的浏览器测试在 1280px 和 320px 宽度下通过，覆盖空记录以及已收藏和未收藏的数量。两种宽度的时间线截图已人工检查。
- 新镜像的全部构建文件通过哈希校验。发布后两个 HTTPS 域名、入口资源、扩展下载、CSRF、趋势接口及后端健康检查通过；后端、数据库、对象存储和采集器容器保持原样。公网隔离浏览器确认首页加载新 JS；未在真实账户中重新执行历史页面检查。

## 回滚

确认当前仍为本版本且没有后续发布依赖后运行：

```bash
python3 /opt/zhiji/shared/frontend-releases/history-timeline-20261106-01a1ae4a/deploy.py rollback
```

脚本校验当前镜像和其他服务状态，仅恢复前端镜像与源码目录指针；本次未实际演练回滚。
