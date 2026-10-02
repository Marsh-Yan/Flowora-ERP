# Flowora ERP 2.0 发布验证

## 当前可重复入口

在仓库根目录执行 `pnpm verify:web` 与 `./mvnw -B -pl services/api -am test package`（Windows 使用 `mvnw.cmd`）。`pnpm verify:release` 合并这些本地检查；它不等同于全部发布验收。

CI 必须通过五个 job：Web checks、API checks、Isolated MySQL HTTP regression、Production Compose smoke、Windows Maven Wrapper paths。普通 API job 不启用真实 MySQL 专项测试；隔离数据库 job 必须分别运行 `TradeInventoryMySqlTest`、`FinanceMySqlTest`、`WorkflowOperationsMySqlTest`、`AnalyticsScopeMySqlTest` 和六个 HTTP 脚本（库存、财务、身份安全、scope-smoke、module-smoke、workflow-compat-smoke）。

| 层级 | 入口与断言 | 环境 |
| --- | --- | --- |
| Web | 官方 registry 全依赖审计、类型、lint、组件单测、生产构建 | Node 24 / pnpm 11.9.0 |
| API | 默认 standalone 启动、服务单测、迁移防篡改、打包、实际 JAR 扫描 | Java 25 / Maven Wrapper |
| 数据库 | 库存历史切换及重复 seed、履约竞争、财务来源锁及 FX、outbox 并发和恢复 | CI 临时 MySQL 8.0 / Redis 7 |
| HTTP | [库存](../../tools/verification/compat-stock-smoke.ps1)、[财务](../../tools/verification/finance-smoke.ps1)、[身份安全](../../tools/verification/security-smoke.ps1)、[行范围](../../tools/verification/scope-smoke.ps1)、[组织模块](../../tools/verification/module-smoke.ps1)、[兼容工作流](../../tools/verification/workflow-compat-smoke.ps1) | 固定 loopback API 18080 / `audit_flowora` |
| 部署 | 空库 V1–V18、production readiness、无 demo 用户、Redis 密码及重启、Nginx 头与缓存、告警激活及恢复 | Compose MySQL 8.4 / Redis 7.4 / Nginx 1.29 |
| Windows | 新缓存安装和 Junction 路径下 Wrapper 执行 | CI Windows |

Compose job 还扫描五个实际运行镜像；所有等级及包清单保留为 artifact，HIGH/CRITICAL/UNKNOWN 阻断。Prometheus OpenPGP 告警只有当次实际二进制的严格包级分析证明不适用时才放行，其他 UNKNOWN 不豁免。缺失、失败或空扫描均阻断。范围、工具固定版本、证据保留期与限制见 [安全整改记录](dependency-remediation-2026-10-01.md)。

本地业务测试仅可连接明确授权的 `jdbc:mysql://127.0.0.1:13306/audit_flowora?serverTimezone=UTC` 和 Redis 16379；不得连接默认业务库。专项测试设置 `FLOWORA_R2_MYSQL_URL`（密码用 `FLOWORA_R2_MYSQL_PASSWORD`）；HTTP API 使用同一 `DB_URL`。合成账号密码分别通过 `FLOWORA_R2_HTTP_PASSWORD`、`FLOWORA_R3_HTTP_PASSWORD`、`FLOWORA_R5_HTTP_PASSWORD` 注入，脚本支持 `-Username`。

安全脚本创建本次唯一的角色、用户、子组织、草稿订单和附件；退出时禁用本次用户并注销会话，保留合成业务记录以供检查。不要把 `FLOWORA_R2_EMPTY_CI_DATABASE=true` 设置到已有数据库：该开关仅用于 CI 临时空库的历史样本构造。

## 证据和发布门禁

记录 PR/head SHA、CI run URL、日期、profile、数据库/Redis 版本、用例 ID、实际通过/失败/未执行和限制。HTTP 端到端不能替代浏览器端到端；unit 的通过不能替代真实事务和迁移演练。

目前整改覆盖和未完成的发布门禁见 [audit-verification.md](audit-verification.md)。历史 V14→V15 演练不能作为最新 V18 的备份恢复证据；完整角色矩阵、浏览器全业务分支、备份恢复、依赖与镜像扫描、TLS/容量及 UAT 必须分别记录完成状态。

## 历史记录：1.0 发布验证

> 下文为原 1.0 验收记录。旧产物名、CI 范围和数据库认证描述已过期，不作为 2.0 操作指引。

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
