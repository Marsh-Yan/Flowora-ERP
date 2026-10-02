# 组织财务初始化与库存时间（R5-G，2026-10-02）

目录 API 创建组织时，在同一事务中初始化组织、财务设置、管理员角色与成员关系。财务本位币使用规范化的组织币种，财年起始月使用创建参数；匹配数量、价格和税额容差沿用数据库默认零值。金额/单价/数量精度仍保存在组织配置中，不复制父组织的财务配置，不新增角色授权或自动创建会计科目。

V19 只为缺少 `flowora_finance_setting` 的旧组织补齐币种和财年起始月，已有设置和自定义容差保持原样。重复执行补缺 SQL 不会新增重复记录；不改写历史库存、凭证或时间。V1–V18 保持不变。实际升级仍须先做数据库及附件备份，并在隔离副本验证。补齐设置不等同于所有财务业务已配置：盘点仍需要可过账的库存/费用科目，原生财务流程还需要各自科目映射及业务主数据。

## 时间契约

local/production 的 Hikari 连接设置 `connectionTimeZone=+00:00`、`forceConnectionTimeZoneToSession=true`、`preserveInstants=true`，新建物理连接执行 `SET time_zone = '+00:00'`。JPA 的 JDBC 时区继续为 UTC。只修改应用连接的会话，不修改 MySQL 全局时区、操作系统时区或其他客户端。数据库 `TIMESTAMP`、Java `Instant` 和 JSON 时间代表同一瞬间；前端继续按原有显示时区格式化，不叠加人工八小时修正。

`DB_URL` 可以沿用 `serverTimezone=UTC`，它是连接时区属性的别名。不要通过 URL 或外部 Hikari 配置覆盖为其他时区、关闭瞬间保持或替换 UTC 初始化语句；URL 属性可能覆盖驱动属性。本阶段没有增加配置冲突时的全局启动拦截，部署前应核查生效配置及数据库 epoch。UTC 会话下 `NOW()`/`CURDATE()` 也使用 UTC，不能把它们直接当作任意组织的业务日期。组织时区和日期字段的业务规则保持原样。

该实现遵循 [Connector/J 时间属性](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)及[瞬间保持规则](https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html)。仅指定 `serverTimezone=UTC` 不会设置服务器会话时区，原偏移来自两者不一致。

已有 `TIMESTAMP` 只按数据库存储的 epoch 读取，不进行历史加减时区迁移。若旧写入程序本身写错了瞬间，仅调整读取不能恢复原意；需单独依据业务证据预检。没有据隔离数据推断正式库需要修复。

## 验证与范围

- `OrganizationFinanceMySqlTest` 4项：组织币种/财年/默认容差，后续失败时完整事务回滚，外国父组织拒绝，V19重复补缺及已有配置保留。
- `InventoryTimeZoneMySqlTest` 8组合：local/production 配置 × UTC/Asia-Shanghai JVM × UTC/+08:00 历史写入会话；实际 canonical 视图读取等于历史 epoch，新 JDBC 时间写入也等于指定瞬间，测试只清理自有夹具。
- `inventory-pagination-smoke.ps1` 8场景：不再写 SQL 财务夹具，真实组织 API 自动初始化；HTTP盘点51条、分页/汇总/权限/组织边界、两个读取别名的全部时间与独立SQL epoch及实际操作窗口一致。SQL仅用于只读对照。

本地完整 Maven 144项失败/错误/跳过均0，V1–V19迁移通过。HTTP入口仍仅允许授权 `audit_flowora`/13306、API18080，MySQL客户端密码不进入命令参数或公开日志。CI独立运行两组新增数据库测试并保留脱敏HTTP结果。

本阶段不包含全业务浏览器和窄屏矩阵、转派资格及原生v2权限矩阵、选项/可用量分页、多币种估值、历史写入语义恢复、备份恢复及发布验收；F27仍为部分完成。
