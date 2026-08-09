# Flowora ERP 1.0 发布验证

本文件定义 1.0.0 发布前的可重复验证入口。所有命令都应在仓库根目录执行；依赖缓存可以放在项目目录的 `.cache/`，避免把项目依赖写入系统盘。

## 自动化质量门禁

前端完整检查：

```powershell
pnpm verify:web
```

后端测试与构建：

```powershell
$env:JAVA_HOME = 'D:\softwares\Java\jdk-25.0.4'
$maven = 'D:\softwares\Maven\apache-maven-3.9.16\bin\mvn.cmd'
$repo = 'E:\code\codex\my_erp\.cache\m2'
& $maven '-B' "-Dmaven.repo.local=$repo" '-o' '-pl' 'services/api' '-am' 'test' 'package'
```

跨平台环境可以直接使用：

```text
pnpm verify:release
```

该命令依次执行前端检查、后端测试和后端打包。CI 仍分别执行前端检查与 Maven 测试/打包，便于定位失败步骤。

## 关键闭环验收

| 闭环 | 验收路径 | 关键结果 |
| --- | --- | --- |
| 采购到付款 | 采购申请 → 采购订单 → 入库 → 应付 → 供应商付款 | 库存增加、库存流水生成、借贷平衡凭证生成、应付余额减少 |
| 销售到收款 | 报价 → 销售订单 → 部分出库 → 应收 → 客户收款 | 已履约数量与剩余数量正确、库存减少、应收余额减少 |
| 销售到项目 | 销售订单 → 项目 → 里程碑/任务 → 工时/费用 → 可计费依据 | 订单关联、进度、成本和可计费依据可追踪 |
| 权限与审计 | 登录 → 角色路由守卫 → 管理员重置演示数据 | 未授权请求被拒绝，重置有审计事件和请求追踪号 |

## 数据库演示验收

真实数据库闭环需要 MySQL 8 和 Redis 7。准备本地凭据后，使用 `demo` profile 启动：

```powershell
$env:DB_USERNAME = 'your-local-user'
$env:DB_PASSWORD = 'your-local-password'
$env:FLOWORA_DEMO_SEED_ON_START = 'true'
java -jar services/api/target/flowora-api-1.0.0.jar --spring.profiles.active=demo
```

登录演示账号见 [demo-accounts.md](./demo-accounts.md)。管理员登录后检查演示数据状态，并执行一次重置；随后按上表逐条验证。重置只针对 `org-demo`，不可影响其他组织。

当前自动化测试不替代真实 MySQL/Redis 验收：本地数据库凭据不进入仓库，也不在 CI 中使用固定账号。

## 发布前检查

- [ ] GitHub Actions 的 Web 与 API job 全部通过。
- [ ] `pnpm verify:web` 通过，且构建产物可生成。
- [ ] Maven 测试与 `package` 通过。
- [ ] 已完成一次空库迁移、演示数据初始化和演示数据重置。
- [ ] 三条关键业务闭环完成并记录结果。
- [ ] 已确认已知限制与 2.0 路线图，并发布 `docs/releases/1.0.0.md`。
