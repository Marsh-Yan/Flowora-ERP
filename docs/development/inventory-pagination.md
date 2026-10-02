# 库存分页与全量汇总（R5-F，2026-10-02）

库存余额和流水默认每页50条，页面提供独立翻页。新 GET `/api/v2/compat/inventory/summary`（只读别名 `/api/v1/inventory/summary`）返回当前组织全部余额的 `inventoryValue`、仓库/物料汇总行数 `balanceCount` 和流水行数 `ledgerCount`。三个指标都不依赖当前页，保持 ALL + inventory:view 权限和当前身份刷新；不支持行范围的用户继续拒绝，不自动提权。

local/production 汇总读取 canonical 库存余额视图；按仓库/物料合并库位、批次、序列号，金额为全部存储成本 `quantity × average_cost` 的合计。流水数按已过账 movement line 的入/出方向计数，调拨两端为两条，不是单据数。legacy 服务回退聚合原余额/流水，并沿用原明细每行4位舍入。没有读取旧账来补加 canonical 数据，也没有新迁移。

这里修复分页遗漏，沿用现有存储成本口径，不新增货币换算或宣称已解决多币种估值。混合来源币种的成本归一化、配置精度和大额前端数值精度仍需单独验证；金额显示沿用两位小数。库存明细数也不改成去重SKU数。

余额、流水、汇总独立加载：某页失败保留成功汇总，并显示失败和重试；汇总失败显示“—”，不从当前页计算备用数字。成功空结果才显示空数据。刷新返回第一页，失效页请求退回最后有效页；晚到的旧页、旧刷新和组件卸载后的响应不能覆盖当前结果。不同HTTP读取之间不是并发业务写入的同一快照。

## 可复测入口

| 入口 | 验证 |
| --- | --- |
| TradeInventoryMySqlTest 新增2项 | 51条余额/流水的两页完整性、全量金额1332（含第二库位6）、分组及另一组织99900排除、空组织0 |
| InventoryServiceTest / OrganizationModuleAuthorizationTest 各新增1项 | legacy未分页聚合；真实方法安全代理、ALL/权限/撤权边界 |
| organization-modules.test.ts 新增5项 | 独立分页、汇总不随页变、失败重试、刷新乱序、页码收缩及空/不可用区分 |
| inventory-pagination-smoke.ps1 八场景（R5-G扩展） | 新建唯一组织自动财务初始化，以HTTP建51个物料及盘点，全组织金额1326与两页对照、入账流水51、时间epoch、空数据、权限和跨组织排除 |

HTTP脚本严格限制 audit_flowora/127.0.0.1:13306 和API18080。MySQL client 默认 `mysql`，Windows可用 `FLOWORA_MYSQL_CLIENT` 指定已有可执行文件；密码沿用隔离 `DB_PASSWORD`，不打印到参数或日志。R5-F曾直接初始化测试组织的财务夹具；R5-G已移除该写入，通过真实组织API初始化，SQL只读对照设置与时间epoch。当前契约见 [组织财务与时间](organization-finance-time.md)。

本地完整后端132项（失败/错误/跳过0）、前端18项及lint/type/build通过。最终PR/head/CI以本阶段检查为准。手工浏览器证据仅证明当时隔离51行数据的翻页，不替代全业务端到端或F27验收。

## 仍需继续

R5-G已处理新组织财务设置及默认连接配置下的流水读取偏移，未改写历史时间。可用量和主数据/采购选项的完整分页、成本与币种/精度语义、容量和查询性能、全业务/窄屏/项目/角色矩阵、事务竞争及成对恢复仍需后续阶段。F27保持部分完成。
