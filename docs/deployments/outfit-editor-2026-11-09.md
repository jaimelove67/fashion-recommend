# 单品编辑与生图额度发布记录

2026-11-09 已将当前工作区更新提交并推送到 `main`，应用代码版本为 `696cbc91abde32440c335360506203838306468f`。本批次同时同步此前未提交的管理工作台、搭配记录时间线、穿搭鉴赏、浏览器扩展和识图模型更新。

## 线上版本

- 网站为 `https://www.jaime.ltd` 和 `https://jaime.ltd`。
- 发布目录为 `/opt/zhiji/releases/outfit-editor-20261109-696cbc9`，`/opt/zhiji/current` 已切换到该目录。
- 操作目录为 `/opt/zhiji/shared/app-releases/outfit-editor-20261109-696cbc9`，包含源码包、前后端制品、哈希清单、部署脚本和 `state.json`。
- 前后端镜像分别为 `fashion-recommendation-cloud-backend:outfit-editor-20261109-696cbc9` 和 `fashion-recommendation-cloud-frontend:outfit-editor-20261109-696cbc9`，均带有上述代码版本标签。对应 `latest` 已同步。
- 沿用原服务运行时镜像，后端覆盖经过完整测试的 JAR，前端覆盖本地生产构建资源。部署目录中的 `release-images.yml` 固定本次镜像版本。
- Flyway 从 V15 升级到 V17，新增推荐效果图存储及用户生图额度表。迁移前已有业务表的记录数量在发布检查时一致。
- PostgreSQL、MinIO 和采集器容器 ID 与发布前一致。环境配置继续引用 `/opt/zhiji/shared/.env.cloud`。

## 本次交互

推荐页通过侧边单品图片进入更换，支持衣橱选择和本次上传。点击生成按钮后才提交生图。额度按账户执行，每天最多一张、每周最多四张，使用北京时间，每周一重置。生成失败会释放额度。单品替换与上传素材按本次编辑保留，刷新后恢复原推荐方案。

## 备份

服务器备份位于 `/opt/zhiji/shared/backups/outfit-editor-20261109-696cbc9`，包含 PostgreSQL 自定义格式转储、MinIO 数据归档、原环境配置及校验和。目录权限为 700，备份文件权限为 600。数据库转储目录已通过 `pg_restore --list` 检查，本次未进行完整恢复演练。

自动审批因用户数据与配置密钥风险拦截了备份下载。用户明确选择仅保留服务器备份并继续部署，本次未将生产备份下载到本机。

## 验证

- 完整后端 `mvn -q test package` 通过，真实模型检查按配置跳过。
- 前端生产构建通过，132 项单元测试通过。构建保留单个 JS 分块大于 500KB 的提示。
- 扩展浏览器回归共 34 项，20 项通过、14 项失败。通过项覆盖管理导航、桌面及手机时间线、推荐编辑、手动生图和部分当前趋势展示。失败项分布在 `trend-analysis.spec.js`、`trend-daily-selection.spec.js` 和 `trend-filter-favorites.spec.js`，仍依赖当前页面已移除的 `.trend-breakdown`、`.brief-daily-slot`、深入分析及款式示意图收藏区域，需另行适配当前穿搭介绍界面。
- 首次切换后，公网资源检查发现新镜像静态目录权限导致 JS、CSS 返回首页内容。已在镜像构建中显式授予公开静态资源读取权限，并重建前端。修复后服务器内及本机公网校验均通过，入口 HTML、JS、CSS 和扩展 ZIP 的 SHA-256 与本地制品一致。
- 两个公网 HTTPS 域名返回 200，CSRF 返回有效会话数据，匿名用户访问账户和生图额度接口返回 401。后端健康为 UP，证书续期定时器为 active。
- 线上验收覆盖部署、公开资源、健康及匿名鉴权边界。真实账户更换单品、上传与计费生图尚未执行。

## 回退依据

旧源码目录为 `/opt/zhiji/releases/history-timeline-20261106-01a1ae4a`。本次保留 `fashion-recommendation-cloud-backend:before-outfit-editor-20261109-696cbc9` 和 `fashion-recommendation-cloud-frontend:before-outfit-editor-20261109-696cbc9` 镜像标签。

回退前应核对当前版本及后续依赖。应用回退需恢复两项服务的旧镜像及旧源码目录指针，并重新检查 Nginx、HTTPS 和后端健康。数据库当前为 V17，应用镜像回退会保留新增表。完整数据回退需基于上述备份另行安排停写与恢复。
