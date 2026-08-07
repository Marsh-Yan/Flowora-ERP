# 开发工作流

## 分支

- `main`：可构建、可验证的稳定分支。
- `feature/<short-name>`：新功能。
- `fix/<short-name>`：缺陷修复。
- `docs/<short-name>`：文档变更。

## 提交

使用 Conventional Commits：

```text
feat(web): add dashboard shell
fix(api): handle invalid workflow state
docs: update local setup guide
chore: update build tooling
```

## 本地验证

提交前至少执行：

```powershell
pnpm verify:web
mvn -B -pl services/api -am test
```

## 环境变量

不得提交密钥、密码或真实数据库地址。使用 `.env.example` 提供变量名称和说明。

## 第 03 阶段：工作流与协同中心

第 03 阶段提供固定规则审批、待办任务、站内通知、评论和业务活动时间线。所有记录都带有 `organization_id`，服务层查询和写入统一使用当前登录用户的组织上下文。

### 规则

- 金额大于或等于组织配置的审批阈值时，任务进入 `OPEN`，默认分配给 `MANAGEMENT` 角色。
- 低于阈值的采购/销售/库存任务自动进入 `APPROVED`。
- `PROJECT` 与 `GENERAL` 在 1.0 版本不触发金额审批。
- 只有当前分配用户、分配角色成员或管理员可以审批、驳回、转交和完成；发起人可以取消自己发起的任务。
- 转交会记录 `TRANSFERRED` 活动，但任务状态回到 `OPEN`，以便新负责人继续处理。
- 审批、驳回、转交、完成、取消、评论都会同时写入活动时间线和 `flowora_audit_event`。

### API

基础路径为 `/api/v1/workflow`，详见 [`docs/api/openapi.yaml`](../api/openapi.yaml)：

- `GET/POST /tasks`：查询待办、创建流程任务。
- `POST /tasks/{id}/actions`：执行审批动作。
- `GET /notifications`、`GET /notifications/unread-count`、`POST /notifications/{id}/read`：通知中心。
- `GET/POST /resources/{resourceType}/{resourceId}/comments`：评论。
- `GET /resources/{resourceType}/{resourceId}/activities`：活动时间线。

### 验收场景

1. 以业务角色创建金额低于阈值的采购任务，确认自动批准且不创建审批通知。
2. 创建金额达到阈值的采购订单审批任务，确认管理角色收到通知，非分配角色无法审批。
3. 管理角色审批、驳回、转交任务，确认状态、通知、活动时间线和审计记录一致。
4. 在任务详情添加评论，确认组织内可见且按时间排序。
5. 使用两个组织的用户查询同一任务 ID，确认跨组织访问返回资源不存在。
