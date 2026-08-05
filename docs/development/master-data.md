# 阶段 02：主数据与系统设置

阶段 02 提供业务单据可以引用的主数据基础能力，所有记录都按 `organization_id` 隔离。

## 接口分组

- `/api/v1/masters/customers`
- `/api/v1/masters/suppliers`
- `/api/v1/masters/items`
- `/api/v1/masters/warehouses`
- `/api/v1/masters/currencies`
- `/api/v1/masters/exchange-rates`
- `/api/v1/masters/tax-rates`
- `/api/v1/masters/accounts`
- `/api/v1/organizations/settings`

列表接口统一支持 `query`、`page`、`size` 参数，页码从 0 开始，单页最大 100 条。删除接口执行停用，不物理删除主数据，避免破坏业务单据引用。

## CSV 导入导出

导入接口为 `POST /api/v1/masters/{resource}/import`，使用 `multipart/form-data` 的 `file` 字段。导入按行处理，返回成功数量、拒绝数量和行号错误；一行失败不会阻断其他有效行。

支持导入的资源及表头：

| 资源 | 表头 |
| --- | --- |
| customers | `code,name,contactName,email,phone,address,currencyCode,paymentTermsDays,active` |
| suppliers | `code,name,contactName,email,phone,address,currencyCode,paymentTermsDays,active` |
| items | `code,name,type,unit,salesPrice,purchasePrice,averageCost,taxRate,inventoryManaged,active` |
| warehouses | `code,name,address,active` |

编码会自动去除首尾空格并转为大写；同一组织内重复编码会被拒绝。导出接口为 `GET /api/v1/masters/{resource}/export.csv`，返回 UTF-8 CSV。

## 权限

- 客户、供应商、商品/服务：`ADMIN`、`BUSINESS` 可维护。
- 仓库：`ADMIN`、`WAREHOUSE` 可维护。
- 币种、汇率、税率、科目：`ADMIN`、`FINANCE` 可维护。
- 组织设置：仅 `ADMIN` 可修改，其他已登录角色可查看。
- 所有读取和写入都使用当前会话中的组织 ID，不接受客户端传入组织 ID。

## 数据约束

- 金额和税率使用 `BigDecimal`，服务端校验非负金额及 0–100 的税率范围。
- 汇率必须大于 0，并保存生效日期。
- 商品分为 `GOODS` 和 `SERVICE`；只有维护库存的商品参与库存业务。
- 科目类型为 `ASSET`、`LIABILITY`、`EQUITY`、`REVENUE`、`EXPENSE`。
- V3 迁移为演示组织预置 USD、CNY、EUR、零税率和基础科目。
