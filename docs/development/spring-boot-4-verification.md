# Spring Boot 4迁移验证（2026-10-07，R5-Z）

基线为PR50提交d0cc77577abed1fd0648246a450a8a47e7a6b413；用户授权继续处理框架升级。继续同一草稿PR，主工作区既有修改保留。

## 升级与兼容范围

原CI37580006292因spring-webmvc6.2.19/CVE-2026-47884在实际JAR和Compose镜像扫描被阻断。[Spring官方公告](https://spring.io/security/cve-2026-47884/)的公开修复版本为7.0.9；6.2.20属于企业支持版本。升级Boot3.5.16至公开稳定维护版本4.0.8，Framework7.0.9、Netty4.2.17.Final随BOM管理；移除旧主版本覆盖。Boot默认Tomcat11.0.24与Jackson3.1.5仍在CI37597139964被依赖检查阻断，按[Tomcat官方公告](https://tomcat.apache.org/security-11.html)及[Jackson3.1.7发布说明](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7)分别补到同一主版本Tomcat11.0.26与Jackson BOM3.1.7。实际JAR同时保留已补丁Jackson2.21.7；不因应用仍走Jackson2忽略打包的Jackson3依赖。保留Java25。

按[官方Boot4迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)改为WebMVC、Flyway、Redis Session显式starter，补MVC/安全/HTTP客户端测试starter及新测试包。standalone排除新包DataSource/Hibernate/DataRedis/Session/RedisSession自动配置，local/production仍清空排除列表、使用真实数据库与Redis。

暂用官方spring-boot-jackson2兼容模块及preferred-json-mapper: jackson2，保留Jackson2.21.7补丁。现有注入ObjectMapper、数据库JSON和HTTP序列化保持Jackson2路径；未同时切换存储JSON和幂等请求指纹。MVC slice显式导入Jackson2自动配置；TestRestTemplate显式自动配置。

Jackson2兼容模块已被官方弃用，未来仍需独立迁移至Jackson3并核对存量JSON与请求内容指纹。它属于当前官方迁移桥接范围，不以兼容模式宣称未来Boot版本可直接升级。

不修改已应用迁移、业务写规则、授权/CSRF规则、CI工作流、扫描忽略项或门禁等级。公共scan中厂商/扫描器严重等级差异保留。

## 验证

完整本地Maven package242项，失败/错误/跳过0（正式财务MySQL12、组织权限9、交易库存MySQL48），Flyway V1–V20成功校验。新增实际应用JSON契约测试：首个可写JSON的MVC converter为Jackson2，LocalDate/Instant为ISO字符串、BigDecimal12.3400保留数值及小数位、反序列化相等；新增local/production两项实际Boot属性绑定测试，要求带索引会话且namespace保持spring:session；无数据库standalone健康和既有MVC测试通过。

初次迁移发现测试slice缺HttpSecurity配置与Jackson2自动配置；完整测试发现旧Redis排除类名无效导致standalone健康503，均按Boot4模块修正，保留原健康成功断言，没有放宽测试。

第一次普通登录发现SESSION_STORE_UNAVAILABLE；实际Boot4 SessionDataRedisProperties前缀为spring.session.data.redis，旧spring.session.redis.repository-type未生效。local/production改用新前缀并保留INDEXED，不改SessionGovernanceService规则。仅单元测试通过未能发现这一真实启动兼容问题，最终普通登录和现有CI会话回归补足证据。

普通隔离浏览器在新运行时实际登录、创建2*12税10%的供应商发票并单独过账。SQL核对POSTED/CNY总额26.4、核销与贷记0、1凭证借贷各26.4、不平0；M4与正式卡片均26.40 CNY。真实local启动含Hibernate schema validate、Flyway、Redis会话。普通注销至登录页，关闭临时标签；测试用户停用/组织归档、四隔离端口关闭、业务历史保留。

本地补丁后完整回归证据.cache/r5za-api-test.log、r5z-artifact-verification.log；初轮及普通浏览器证据.cache/r5z-api-test.log、r5z-api-final.log、r5z-db-verification.log、r5z-upgrade-finance.png、r5z-fixture-cleanup.log、r5z-service-cleanup.log。完整现有GitHub工作流继续执行，最终放行以PR50最新提交CI为准，旧失败运行保留历史记录。

## 仍待工作

本批证明框架迁移与所列现有回归、普通财务路径，不代表所有角色/业务、历史数据库及附件恢复、性能或GA验收完成。贷记/外币UI、其他旧财务读口径、完整发布门禁及Jackson3迁移继续跟踪，F27部分完成。
