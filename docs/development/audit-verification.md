# 2026-10-01 审计整改覆盖与验收边界

R0–R4 已分别通过 [PR #21](https://github.com/Marsh-Yan/Flowora-ERP/pull/21)、[#22](https://github.com/Marsh-Yan/Flowora-ERP/pull/22)、[#23](https://github.com/Marsh-Yan/Flowora-ERP/pull/23)、[#24](https://github.com/Marsh-Yan/Flowora-ERP/pull/24)、[#25](https://github.com/Marsh-Yan/Flowora-ERP/pull/25) 合并。本文公开可复测的覆盖和剩余门禁，不包含私有审计中的账号状态、日志、截图或业务记录。原审计报告保留为历史发现。

R5 第一批补齐真实安全 HTTP 回归、覆盖矩阵与过期文档。F01–F26 已有修复及相应验证；F27 的真实启动、数据库与关键 HTTP 门禁已补齐，但全部业务浏览器自动化、完整恢复与安全发布门禁仍未完成，因此 F27 不标记全量验收完成，也不宣告 GA。

## 发现到验证入口

“单测”表示服务或组件断言；“数据库”表示真实 MySQL 事务；“HTTP”表示真实应用和 Redis 会话；“部署”表示目标 Compose。以前阶段手工浏览器结果仅证明当时版本的指定场景，不能替代最新 head 的完整浏览器回归。

| ID | 修复范围 | 可重复验证入口 / 层级 |
| --- | --- | --- |
| F01 | production 启动 | CI Production Compose smoke：readiness、空库迁移、无 demo 用户 / 部署 |
| F02 | standalone 启动 | `StandaloneStartupTest` / 应用启动 |
| F03 | v1 MFA 绕过 | security-smoke SEC-04 / HTTP；`AuthControllerTest` |
| F04 | MFA 更换破坏有效因子 | SEC-05 / HTTP；`DatabaseMfaServiceTest` |
| F05 | 会话列表与密码重置撤销 | SEC-02/03/06/10 / HTTP |
| F06 | 最大会话限制 | SEC-02 / HTTP；并发请求边界仍需专项扩展 |
| F07 | 兼容读取和搜索模块权限 | SEC-07 / HTTP；`GlobalSearchServiceTest`、`FloworaAuthorizationTest` |
| F08 | 查询、导出和汇总范围 | SEC-12；SCOPE-01–11：DEPARTMENT/SELF/ASSIGNED/模块、部门变更下载、项目成员及分析；AnalyticsScopeMySqlTest / 数据库、HTTP |
| F09 | 组织管理边界 | SEC-08 / HTTP；`PlatformDirectoryServiceTest` |
| F10 | 必须改密限制 | SEC-01/10 / HTTP |
| F11 | 非法履约状态 | `TradeInventoryMySqlTest`、compat-stock-smoke / 数据库、HTTP |
| F12 | 收货来源供应商发票 | `FinanceMySqlTest`、finance-smoke / 数据库、HTTP |
| F13 | 本位币舍入不平 | `FinanceMySqlTest`、`FinancePostingPolicyTest`、finance-smoke / 单测、数据库、HTTP |
| F14 | 撤销核销汇兑冲销 | `FinanceMySqlTest`、finance-smoke：反向凭证、余额、幂等重放 / 数据库、HTTP |
| F15 | 400/404/405 错误契约 | `GlobalExceptionHandlerTest` / 单测；finance-smoke 400/404 / HTTP |
| F16 | 财务页面日期与加载 | `finance-closure.test.ts` / 组件；R3 手工浏览器证据 / 指定页面 |
| F17 | 新旧库存口径及来源行 | `TradeInventoryMySqlTest`、compat-stock-smoke：多行收发、退货、汇总和导出 / 数据库、HTTP |
| F18 | Windows Wrapper 路径 | CI Windows Maven Wrapper paths / Windows |
| F19 | 移动导航 | `workspace-navigation.test.ts` / 组件；R4 手工窄屏浏览器 / 指定页面 |
| F20 | 切换组织数据刷新 | `workspace-navigation.test.ts` / 组件；R4 同路由切换浏览器 / 指定页面 |
| F21 | Redis Compose 密码健康检查 | CI Production Compose smoke：正确/错误密码、重启恢复 / 部署 |
| F22 | Nginx 响应头与缓存 | nginx-headers.sh：HTML、SPA、成功资产、404、API / 部署 |
| F23 | DEAD/RETRY 指标及告警 | `WorkflowOperationsMySqlTest` / 数据库；promtool alerts.test.yml / 告警规则 |
| F24 | outbox 定时事务边界 | `WorkflowOperationsMySqlTest`：两个 worker、崩溃回滚、重试及重放 / 数据库 |
| F25 | 个人审批使用 v2 任务 | `WorkflowOperationsMySqlTest`：实际流程、当前 assignee、旧任务排除 / 数据库；R4 浏览器卡片 |
| F26 | 工作流 If-Match CORS | SEC-11：真实预检和不允许来源 / HTTP |
| F27 | 持续验证不足 | CI 五个 job、七个 HTTP 脚本及本矩阵；发布剩余项未完成 |

所有入口见 [release-verification.md](release-verification.md)，专项测试代码位于 [API 测试目录](../../services/api/src/test/java/com/flowora/erp)，Web 测试位于 [Web 源码](../../apps/web/src)。HTTP 脚本输出稳定 SEC-01–SEC-12 用例 ID，只输出用例和结果，不打印密码、恢复码、Cookie 或 MFA secret。

## 本批实际执行

基线为 R4 合并提交 `a338671df5f9e604beb74eb18a15112d58c1ee84`。2026-10-01 在授权隔离实例 `audit_flowora`、loopback MySQL 13306 / Redis 16379 / API 18080 执行：

| 检查 | 实际结果 | 限制 |
| --- | --- | --- |
| `pnpm verify:web` | lint、类型、5 个单测和 build 全部通过 | 非浏览器全业务端到端；Element Plus chunk 915.98 kB / gzip 296.48 kB 仍需性能量化 |
| Maven `test package`，启用隔离 MySQL | 109 项，失败 0、错误 0、跳过 0；V1–V18 validate 通过 | 本地 MySQL 8.0.32、Redis 3.2.100；目标版本另由 Compose CI 检查 |
| compat-stock-smoke | 多行兼容收发、状态、来源行、原生退货、余额与导出通过 | 合成数据；不证明全部库存 UI 分支 |
| finance-smoke | 实际来源暂估清理、舍入拒绝、FX 冲销与重放、报表口径、400/404 通过 | 合成数据；不证明完整月结、预算、银行及项目 UI |
| security-smoke | SEC-01–SEC-12 全部通过 | SELF 已实测；DEPARTMENT/ASSIGNED 四象限仍需扩展 |
| npm 官方依赖审计 | **未通过**：全部依赖 15 high / 10 moderate；`--prod` 8 high / 5 moderate | 告警数不等于可利用漏洞数；需逐项判定运行位置和输入可达性，见 [依赖扫描记录](dependency-audit-2026-10-01.md) |

本批没有新增数据库迁移或改动业务实现。CI 的 head SHA 和 run URL 以本批 PR 检查为准；将这张表用于后续版本时必须重新执行并记录，不能沿用日期和结果。

## 尚未完成的发布门禁及下一批顺序

R5 第二批已完成前端依赖补丁、后端补丁和实际 JAR/五个镜像扫描门禁；第一批表中的 25 条 npm 告警是历史结果，第二批全依赖及生产依赖均为 0。新增版本、扫描范围、Prometheus OpenPGP 二进制判断和持续限制见 [第二批整改记录](dependency-remediation-2026-10-01.md)。F27 保持未完成，下一批优先浏览器与角色矩阵。

R5 第三批补齐部门/本人/受派项目与分析汇总范围对照，修复复现的 7 个失败场景；具体读取边界和 11 个 HTTP 场景见 [分析数据范围](analytics-scope.md)。这些结果不替代全业务 UI、所有模块的角色/写入四象限或事务异常覆盖。

R5 第四批补齐组织级库存/财务权限：不支持行范围的组织数据要求 ALL，兼容直接过账改用实际细分权限，页面只读与可选加载行为同步。新增方法安全代理 5 项及 MODULE 9 场景，见 [组织级访问边界](organization-module-access.md)。完整模块/项目/跨组织矩阵仍需继续。

R5 第五批修复隔离 HTTP 复现的兼容工作流权限问题：创建改为 submit + 来源范围，审批/转派/完成/取消采用细分权限并保留受派限制，附带评论在动作修改前校验权限与范围。新增 10 项后端测试和 10 个 HTTP 场景；本地完整 Maven 128 项失败/错误/跳过均 0。具体规则及未覆盖边界见 [工作流当前契约](workflow-v2.md)。库存分页与全量汇总留待下一批，F27 继续部分完成。

R5 第六批增加库存余额/流水独立分页与服务端全量汇总，保护旧请求、失败重试和空/未知结果。新增后端4项、前端5项及HTTP6场景，详见 [库存分页](inventory-pagination.md)。本地完整Maven132项及Web18项通过。夹具准备发现新建组织缺少财务设置；本批仅补测试夹具，保留该问题待处理，不宣称全部分页或多币种估值完成。

R5 第七批将组织财务设置纳入创建事务，V19只补旧组织缺失设置，保留已有配置；local/production连接使用UTC会话与瞬间保持，沿用数据库历史epoch。新增组织事务/补缺4项及实际JDBC时区8组合，本地完整Maven144项失败/错误/跳过0。库存HTTP扩展至8场景，取消SQL财务夹具写入，核对自动初始化及API/数据库epoch与实际操作窗口；本地API在UTC和上海JVM均通过该脚本。配置边界及剩余范围见 [组织财务与时间](organization-finance-time.md)，F27仍未全量完成。

| 顺序 | 待办 | 放行证据 |
| --- | --- | --- |
| 1 | 已补依赖补丁及 JAR/镜像持续扫描；继续处置残余 OS 中低等级和 Redis 自身公告覆盖 | 每次构建的版本、包清单、原始扫描及实际二进制适用性证据；不得沿用旧 image ID |
| 2 | 完整浏览器端到端和角色矩阵 | 桌面/窄屏登录 click/Enter/重复提交、MFA、改密、组织切换、采购/销售与退货、库存调拨/盘点/冻结、期间/预算/银行、项目工时/费用/开票/评论、导出；结果逐用例记录 |
| 3 | 安全与事务边界扩展 | 跨组织四象限、DEPARTMENT/ASSIGNED 范围、并发登录、MFA 错误计数及恢复码并发、附件空文件/上限/丢失文件/事务回滚；附件删除当前无公开 API，需明确产品范围 |
| 4 | 最新历史副本升级、备份恢复与中断演练 | 停写后的数据库及附件成对备份、校验 hash、隔离恢复、V19 validate、控制总数/附件下载/登录/权限、实际 RPO/RTO；MySQL DDL 不宣称事务回滚 |
| 5 | 历史风险只读预检 | 凭证本位币不平、撤销核销遗留 FX、非法履约、库存旧账/canonical 差异的只读清单；发现业务数据修复须另行批准，不用测试库结果推断业务库 |
| 6 | 性能、环境与发布决策 | chunk 和关键页面加载基线、容量与多 worker 应用演练、TLS、告警接收链路、v2BusinessWritesEnabled 语义、多币种分析口径、Pilot/UAT 和 GA 审批 |

历史迁移记录 [m5-migration-rehearsal.md](m5-migration-rehearsal.md) 仅证明当时 V15 样本；CI 的历史切换样本也不能替代完整数据库与附件恢复。以上任何一项尚未执行，均应保持“未完成”，不得用“CI 全绿”代替。

## 文档状态

`release-verification`、`demo-data`、`demo-accounts` 已新增当前启动/认证/验证说明；`master-data`、`procurement-inventory`、`sales-fulfillment`、`finance`、`projects`、`workbench`、`workflow` 保留旧阶段正文并显著标注历史范围，链接当前替代契约。README、升级手册、M5 迁移与发布记录同步明确 V18 和证据时效。标记历史文档不表示其中所有功能已重新验收。
