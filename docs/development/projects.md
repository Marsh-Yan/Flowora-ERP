# 项目协同中心

阶段 07 为 Flowora-ERP 1.0 提供项目交付的最小闭环：项目、里程碑、任务、工时、费用、预算和待计费依据。

## 业务规则

- 项目可独立创建，也可以关联客户和销售订单；关联销售订单时会自动继承订单客户和币种。
- 项目状态为 `PLANNED → ACTIVE → AT_RISK → COMPLETED → ARCHIVED`。风险项目允许恢复为 `ACTIVE`，已完成项目只能归档。
- 任务状态为 `TODO → IN_PROGRESS → BLOCKED → DONE`。阻塞任务可恢复为进行中，已完成任务为终态。
- 工时和费用必须归属于项目，可选关联任务。工时使用 `hours × costRate` 形成实际成本，若可计费则使用 `hours × billingRate` 形成计费依据。
- 费用实际成本为费用金额；可计费费用的计费依据为费用金额。计费依据只表示待后续开票/应收处理的来源，不会直接生成已收款应收单。
- 项目进度按已完成任务数 / 项目任务总数计算；暂无任务时进度为 0%。
- 1.0 采用 `PROJECT_MANAGER` 粗粒度权限。项目操作记录写入项目活动时间线和审计事件，并沿用现有工作流资源类型 `PROJECT`。

## API

核心接口位于 `/api/v1/projects`：

- `GET/POST /projects`：项目列表与创建。
- `GET /projects/{id}/summary`、`POST /projects/{id}/status`：交付指标与状态。
- `GET/POST /projects/{id}/milestones`、`GET/POST /projects/{id}/tasks`：计划与任务。
- `GET/POST /projects/{id}/timesheets`、`GET/POST /projects/{id}/expenses`：成本记录。
- `GET/POST /projects/{id}/budgets`：预算明细。
- `GET /projects/{id}/billing-basis`：待计费工时/费用。

## 1.0 边界

本阶段不包含可视化流程设计器、完整项目财务、外部系统集成、附件和发票/收款自动生成；这些内容保留到 2.0 或财务扩展阶段。
