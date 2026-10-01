# 工作台与消息运行规则

## 导航与组织切换

宽度不超过 760px 时，顶部导航按钮打开左侧抽屉；菜单与桌面共用权限过滤，抽屉内可切换组织。按钮提供展开状态，支持 Enter/Space，Escape 关闭后恢复焦点，点击路由后关闭。375px、650px 和桌面均需验证。

组织切换成功后按组织 ID 重建路由页面，包括已处于 dashboard 的场景。工作台刷新和全局搜索忽略失效请求，清除原组织的搜索结果；旧组织的慢响应不能覆盖新页面。切换中的组织选择器不可重复提交。

## 审批统计

角色工作台的 MY_APPROVALS 与 `/api/v2/workflows/tasks?view=MINE` 使用同一业务口径：当前组织、当前用户为受派人、v2 任务状态 OPEN。无 workflow:view 权限不显示卡片；发起人、其他受派人和其他组织的任务不计入。批准、拒绝、退回、撤回及终止后不再计入。

旧任务仍由 `/api/v2/compat/workflow/tasks` 提供，不混入指向 v2 待办的首页数字。运维的逾期指标为跨组织的 v2 OPEN 任务及旧 OPEN 任务，截止时间严格早于数据库当前时间；这是运维全局指标，不能当作个人首页计数。旧转交会更换受派人并保持 OPEN。

OUTBOX_FAILURES 风险只向 workflow:admin 显示；银行未匹配风险只向 finance:view 显示。

## Outbox 的状态、事务与恢复

事件状态为 PENDING、RETRY、DEAD、DELIVERED；FAILED 是投递尝试日志的 outcome，不是事件状态。

- flowora_outbox_pending：PENDING 与 RETRY 总数。
- flowora_outbox_retry：RETRY 总数。
- flowora_outbox_failed：DEAD 总数，保留原指标名供现有告警使用。诊断 outboxFailed 使用相同口径。
- FloworaOutboxFailed 在 DEAD 持续存在 5 分钟后告警；恢复投递后消退。

定时入口和手工批处理均由显式 TransactionTemplate 保持 `FOR UPDATE SKIP LOCKED` 行锁直到批次提交。两工作进程不会同时处理同一锁定事件。本站内通知、尝试记录与事件状态使用同一数据库事务；事务中断或提交失败会回滚，下一次可重新领取，不依赖进程内锁或遗留 locked_at 租约。

站内通知以 outbox_event_id 唯一键去重。死信重放重置本轮最多 5 次的重试预算，但不清空旧尝试；attempt_number 连续递增，恢复后的成功记录可审计。入队、到期判断、重试和投递时间均用数据库时钟，避免 JVM/JDBC 与数据库会话时区不一致使调度偏移。

当前只投递站内通知。上述数据库去重不承诺未来邮件或外部渠道的 exactly-once，接入外部渠道时必须另行设计接收端幂等。

## 静态服务门禁

HTML 和 SPA 路径包含 nosniff、DENY、strict-origin-when-cross-origin 与 no-store。成功的指纹资源包含同样安全头及一年 immutable 缓存；资源 404 也包含安全头，且不附成功资源的长期缓存。API 代理响应单独验证安全头。

Nginx 的 location 内若使用 add_header，则需显式包含安全头，不能假设继承 server 配置。

## 验证

- Web 组件测试验证权限过滤导航、同路由组织切换和迟到响应；实际浏览器验证尺寸、焦点和双组织指标/列表。
- WorkflowOperationsMySqlTest 仅在授权的 `127.0.0.1:13306/audit_flowora` URL 下运行，创建并清理自己的组织夹具。覆盖定时入口双进程、事务中断、重复通知保护、重试至死信与重放审计、诊断/指标一致及实际审批终态。
- CI 强制运行真实 MySQL 测试。生产 Compose 中执行 `bash tools/verification/nginx-headers.sh` 及 `promtool test rules alerts.test.yml`，验证目标 Nginx 和 Prometheus。

本阶段无新迁移，不修改已应用迁移或历史业务数据。
