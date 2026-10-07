# 正式应付余额验证（R5-Y，2026-10-07）

基线：PR49已合并，main `de2d5e0b6c7d4cff90256d1f964f3e9e174077ee`；合并时间2026-10-05 22:55:39（Asia/Shanghai）。

## 问题与行为

F50：FinanceView原“应付未结”把旧版应付单第一页直接相加，遗漏正式供应商发票、超出分页的单据，且混合原币种。现改读已有正式财务dashboard的全部POSTED供应商发票本位币剩余余额，扣除有效核销和贷记，并读取组织设置显示本位币。当前余额不随外层凭证/报表日期范围改变。过账与核销事件重新读取。

读取失败或币种不可用显示“—”及重试提示；不以旧表或0替代。旧应付单列表和付款入口保留，明确标注旧版范围。组织切换清空旧值并忽略迟到结果；注销清空读取状态及弹窗，不再发送财务读取。未修改财务写规则、生产后端实现或迁移。

## 自动验证

- 完整本地Maven package：239项，失败/错误/跳过0；FinanceMySqlTest12、OrganizationModuleAuthorizationTest9、TradeInventoryMySqlTest48，V1–V20校验成功。
- Web12文件126项及lint、类型检查、build通过。新增7项组件测试覆盖分页旧表与币种、失败/重试、设置失败、独立旧表失败、过账刷新、日期、组织迟到读取和注销。
- 新增真实MySQL测试：EUR100发票按汇率2，核销EUR25并贷记EUR10，本位币余额130；草稿999不占用，不同查询日期仍130，旧应付表0。

首次组件测试的未完整Element组件桩与分页类型夹具已修正；显式显示金额和币种间空格。测试准备错误不计生产缺陷。构建保留既有Rollup注释/chunk提示。GitHub完整既有工作流由本阶段PR运行，最终状态以对应提交CI为准。

## 普通浏览器及数据库证据

仅使用隔离合成组织和普通业务页面，无额外安全探测或异常HTTP脚本。页面实际创建PI-1791352031119：2件×12、税10%，26.40 CNY；草稿余额0，单独过账后M4和新卡片均26.40。页面创建并过账PY-1791352160409付款10元，未核销时余额仍26.40；普通核销10后两处均16.40。

外层结束日期设2026-10-06：当期凭证借方0，当前正式余额仍16.40 CNY。旧版应付标签为空且明确说明范围，不影响正式卡片。

限定测试组织SQL：发票POSTED总额26.4、allocated10、credited0；付款POSTED10/allocated10；ACTIVE核销10；旧应付单0；当前本位币余额16.4；两凭证借贷各36.4、不平0。1000贷10、1200借2.4、2000借10贷26.4、5000借24。剩余16.4未结清。

本地证据在主工作区.cache：r5y-api-test.log、r5y-web-test/lint/build.log、r5y-db-verification.log、r5y-balance-final.png、r5y-balance-date-legacy.png。浏览器普通注销至登录页、标签关闭；合成用户停用与组织归档，业务历史保留，四个隔离服务端口关闭。

## 仍待验收

本阶段只统一正式应付余额卡片；旧版应付表、账龄及其他历史报表未宣称统一。贷记页面、外币/汇兑页面、历史读口径覆盖、完整角色与业务矩阵、恢复与历史升级、性能及发布决策继续跟踪。F27部分完成，CI通过不能替代完整发布门禁。


## 首轮CI依赖门禁历史记录（2026-10-07）

首轮CI37579059067：隔离MySQL回归及Windows路径检查成功；Web、API、Production Compose未通过，不能放行。Web新增公告命中source-map-js1.2.1、Vue server-renderer3.5.40、postcss-selector-parser7.1.4；更新Vue至3.5.43并将两个传递分支分别锁定补丁1.2.2和7.1.6，保留测试工具2.4.11。

官方依据：[Vue公告](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6)、[source-map-js公告](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)、[selector parser公告](https://github.com/advisories/GHSA-rj75-hqrm-r3gf)。不运行公告中的复现代码。

API打包成功，但现有实际JAR扫描阻断CVE-2026-47884/spring-webmvc6.2.19；[Spring官方公告](https://spring.io/security/cve-2026-47884/)将6.2.20列为企业支持版本，公开修复为7.0.9。公共Maven Central元数据的6.2分支仍止于6.2.19。扫描器标为CRITICAL，官方标为MEDIUM，差异如实保留；不修改门禁级别、忽略项或扫描逻辑来放行。

Spring/Boot主版本升级与应用兼容、完整数据库/启动/Compose复验需要单独交付；本阶段未升级后端框架，PR保持草稿，CI失败阻止合并/发布。后续CI结果应按对应提交重新核对，不能用首轮成功job证明不同提交。


用户已授权后续框架升级，同一PR50已补[Boot4迁移与验证](spring-boot-4-verification.md)，上述“未升级/待独立阶段”描述为首轮交付历史；最新放行结果以对应PR提交CI为准。
