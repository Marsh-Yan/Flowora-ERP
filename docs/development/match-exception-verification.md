# 采购发票匹配异常页面验证

日期：2026-10-05。基线为 PR48 合并后的 main `7baf3933bdd40d831cd0764b032611aafecbf792`。

原有后端支持匹配异常审批，但普通页面没有入口，发票详情未返回已存审批人/原因。本批增加“匹配异常”查看入口，展示行数量、单价、数量/价格率/税额差异及来源关联；有finance:view可查看，有finance:match-exception且详情为SUPPLIER_INVOICE/DRAFT/EXCEPTION才可提交非空、最长500字的原因。沿用现有ALL数据范围，不变更服务端审批/过账规则，也不新增迁移。

发票读取新增matchExceptionApprovedBy、matchExceptionReason，来自已有数据库列；审批后及过账后均可查看审计记录。审批只是放行匹配差异，不产生凭证、不代替过账、不豁免来源容量。列表禁止对EXCEPTION发票直接点击过账。读取失败阻止提交；提交中阻止重复操作/关闭；失败重新读取并刷新父页面，不自动重试。关闭重开/切换发票或组织清空原因，迟到读取不覆盖新状态。

本地完整Maven238项，失败/错误/跳过0；FinanceMySqlTest11、OrganizationModuleAuthorizationTest9、TradeInventoryMySqlTest48实际执行，V1–V20 validate及package通过。真实MySQL新增从合法采购来源创建价格12对比订单10的20%异常，批准前过账拒绝、无凭证；批准保存trim原因/操作者/递增版本且仍DRAFT/无凭证；重复审批不覆盖记录；单独过账生成平衡12元凭证且来源耗尽。授权测试覆盖审批权限及ALL范围。Web11文件119项、lint、类型检查和生产构建通过；新增7项覆盖差异、原因边界、只读查看、失败/未知结果、重复提交与关闭、迟到读取、切换/重开、状态和类型限制。

普通浏览器使用全新合成组织的预置合法采购来源和异常草稿R5X-PI-EXCEPTION，2件单价12税率10%，采购基准单价10税率0%，差异数量0/价格20%/税额2.4，总额26.4 CNY。该夹具通过SQL预置，浏览器验证的是读取、审批和单独过账，不宣称浏览器创建异常草稿或完整库存闭环。390px界面可阅读长来源ID、填写原因和提交；空原因时按钮禁用。审批后显示DRAFT/APPROVED_EXCEPTION和已保存审批人/中文原因；SQL0凭证，版本1。随后普通过账成为POSTED，版本2，审批记录仍可读取，1凭证借贷各26.4、不平0，2005借24/1200借2.4/2000贷26.4，M4应付26.4。旧财务卡片“应付未结”仍采用旧payables读取显示0，该读口径差异保留为后续统一验收待办，不算本批全财务闭环完成。

本地证据.cache/r5x-api-test.log、r5x-web-test/lint/build.log、r5x-approved-db.log、r5x-posted-db.log、r5x-match-narrow.png、r5x-approved-narrow.png、r5x-posted-review.png。初次命令使用不存在的test:unit，改pnpm test全量执行；新增测试误用entries方法，改journals后全量通过。两项属于执行/测试修正。普通注销到登录页，临时tab关闭、viewport复原，测试用户停用/组织归档各1，四个隔离端口无监听，业务记录保留。

贷记页面、外币/汇差操作、旧新财务读口径统一、完整角色/恢复/历史/性能和发布门禁继续跟踪，F27仍部分完成。完整GitHub正常CI不省略；数据库实证以隔离MySQL job为准。
