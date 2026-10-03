# 兼容采购、销售访问契约（R5-J，2026-10-03）

当前规则适用于 local/production 的 `/api/v2/compat/procurement` 和 `/api/v2/compat/sales`；`/api/v1` 同名读取使用相同策略，v1 写入仍返回 426。角色名称不替代实际权限。权限和服务调用使用当前组织有效成员的最新身份，旧会话的权限、成员状态及数据范围变化在下一次请求生效。

## 读取与操作

| 接口/操作 | 当前要求 | 范围与副作用 |
| --- | --- | --- |
| 采购、销售订单列表 | 对应模块 `view` | 使用当前 ALL/SELF/DEPARTMENT 范围过滤查询与总数；ASSIGNED 无指派策略时返回空 |
| 采购申请列表 | `procurement:view` + ALL | 申请尚无行范围策略，其他范围拒绝 |
| 报价、出库、应收及应收付款列表 | `sales:view` + ALL | 组织共享查询；不支持的范围拒绝，不推断为整组织可读 |
| 创建兼容采购订单 | `procurement:create` + `procurement:submit` | 创建即 APPROVED；引用申请另需 ALL + `procurement:view` |
| 取消兼容采购订单 | `procurement:submit` + `procurement:view` | 先验证订单读取范围，再进入原有状态锁和取消规则 |
| 创建兼容销售订单 | `sales:create` + `sales:submit` + ALL | 创建即 CONFIRMED，生成应收和记账；引用报价另需 `sales:view` |
| 创建采购申请 | `procurement:create/view/submit` + `workflow:view/submit` + ALL | 创建包含自动提交流程，必须具备完整能力 |
| 创建销售报价 | `sales:create/view/submit` + `workflow:view/submit` + ALL | 创建包含自动提交流程，必须具备完整能力 |
| 审批/拒绝报价 | `sales:view` + `workflow:approve` + ALL | 保留任务受派、当前能力及状态检查；ADMIN 名称不授予审批能力 |
| 兼容销售出库 | `inventory:post` + ALL | 保留仓库、来源行、订单锁、履约和库存规则；自定义仓库角色不需要销售角色名称 |
| 兼容客户收款 | `finance:post` + ALL | 保留应收归属、余额、期间和记账规则；自定义财务角色不需要销售角色名称 |

## 模板缺失的事务回退

模板匹配在引擎写入前发生。没有匹配模板时，通过专用 `WorkflowTemplateNotFoundException` 保留原有兼容回退：采购申请自动批准，销售报价进入旧任务审批策略。模板匹配与启动两个事务代理仅对此异常使用 `noRollbackFor`，调用方只捕获该类型。歧义模板、审批人解析失败以及其他写入异常仍回滚整个创建事务。

原生工作流启动没有兼容回退，缺少模板仍按原来的 `WORKFLOW_TEMPLATE_NOT_FOUND` / 409 返回。没有迁移、历史业务改写、默认权限扩张或全局忽略事务异常。

## 页面行为

采购、销售页依据当前客户端权限显示操作；SELF/DEPARTMENT/ASSIGNED 用户保留订单视图，不请求申请、报价或共享应收数据。主数据选择器只在 `master:view` 可用时加载；读取失败保留其他成功列表，并显示错误和重试。失败的金额或统计显示“—”，不能当作真实零。

页面的新订单使用原生 v2 草稿接口，只需对应模块 `create` 和主数据选择器权限；兼容接口创建即批准/确认的提交要求不能套到原生草稿。报价审批、出库和收款按钮分别受对应权限保护，草稿销售订单不提供出库按钮。

页面依据已加载的客户端身份；管理员修改角色后，菜单和按钮的全局实时刷新尚未完成，服务端使用最新权限拒绝越权请求。页面来源选择器不代表原生 v2 来源行及所有写动作范围矩阵已验收。

## 验证及限制

- `CompatibilityTradeAuthorizationTest`：8 项真实方法安全代理，验证自定义能力、旧角色绕过拒绝、完整提交能力、共享范围拒绝、最新身份及成员失效。
- `ProcurementAccessTest` / `SalesServiceTest`：来源权限与取消范围在资源读取、写入之前检查；保留原有销售业务测试。
- `WorkflowFallbackMySqlTest`：3 项真实 MySQL 和注解事务代理，分别证明缺模板可回退提交、歧义模板回滚、引擎写入后失败回滚。普通 API job 跳过该专项，隔离 job 必须执行。
- Web 新增 6 项权限与错误状态组件测试；下拉框在组件测试中替身，不作为实际浏览器控件验收证据。
- [compat-trade-access-smoke.ps1](../../tools/verification/compat-trade-access-smoke.ps1)：TRADE-01–19，其中 12 分 SELF/DEPARTMENT/ASSIGNED，共 21 场景。真实 HTTP 覆盖兼容自定义创建、旧角色撤权、当前数据范围、成员停用、来源读取、受派审批、合法出库及收款、跨组织拒绝、v1 只读。拒绝请求对照 24 张业务/流程/库存/凭证表的完整有序行，SQL 只读。夹具仅修改本次创建的数据，结束禁用用户、注销会话并归档子组织。

本地执行必须仅连接已授权 `audit_flowora` / MySQL 13306、Redis 16379、API 18080。脚本需 MySQL 客户端，密码通过环境注入，不打印身份凭据。保留用例结果及当前 head/CI 证据；持续扫描和全部发布门禁见 [覆盖及待办](audit-verification.md)。

未覆盖：全部原生 v2 写动作和范围组合、默认角色池资格、历史任务及来源权限变化、完整业务桌面/窄屏/角色矩阵、并发与故障注入、历史预检和成对恢复、TLS/容量/告警/UAT。HTTP 和组件测试不等于这些门禁完成，F27 仍部分完成。
