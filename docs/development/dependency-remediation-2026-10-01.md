# R5 第二批：依赖与镜像整改（2026-10-01）

本批基于 R5 第一批合并提交 `1719f573ba9873b9ca692d7060df2ab95fa000e1`，交付 [PR #27](https://github.com/Marsh-Yan/Flowora-ERP/pull/27)。旧扫描见 [第一批历史记录](dependency-audit-2026-10-01.md)。这里的告警数是扫描记录数，包含重复依赖位置，不表示存在同等数量的可利用漏洞。

## 版本与部署变化

| 范围 | 修复前 | 本批版本或措施 |
| --- | --- | --- |
| Axios | 1.19.0 | 1.20.0，同源浏览器 API/CSRF 客户端保留 |
| Vitest / mocker | 3.2.7 | 4.1.11；组件测试继续通过 |
| nanoid / js-yaml | 3.3.17 / 4.3.1 | 3.3.18 / 4.3.2 |
| brace-expansion | 三个分支 | 分别约束 1.1.21、2.1.7、5.0.12，保留各自 major |
| Spring Boot | 3.5.9 | 3.5.16；Spring Framework / Security / Data / Micrometer 跟随 BOM |
| BOM 之外的修补 | 旧 Jackson / Netty / Tomcat / Log4j | Jackson BOM 2.21.7、Netty 4.1.138.Final、Tomcat 10.1.60、Log4j 2.25.5 |
| API / Web / Redis | 旧发行版包 | 构建时更新发行版补丁；Redis 改为仓库 Dockerfile 构建 |
| MySQL | 官方镜像附带 mysqlsh Python 工具及旧 Go gosu | 8.4.11，移除未使用的 mysql-shell；以固定上游 gosu 1.19 源码、Go 1.27.1、x/sys 0.44.0 重建 gosu |
| Prometheus | 3.5.0 | 3.15.0；保留规则、告警测试与内部网络 |

gosu 源码使用不可变 commit 和 SHA256 校验；最终镜像保持上游 entrypoint、mysql 用户降权和数据卷。`mysql`、`mysqladmin`、`mysqldump` 保留，`mysqlsh` 不再提供。目标 CI 实际验证空库启动、V1–V18、production readiness、无 demo 用户、Redis 密码和重启恢复、Nginx 响应头及 Prometheus 规则。没有新数据库迁移、历史数据修复或业务规则改动。

新增 `.dockerignore` 排除本地缓存、测试数据、环境密钥、私有审计目录及构建产物，避免带入构建上下文。基础镜像标签及发行版仓库仍可能更新；扫描证据只适用于当次实际构建的 image ID，不能作为未来相同标签的永久保证。

## 安全门禁与证据

- Web 每次 CI 通过 npm 官方 registry 执行全依赖审计，moderate 及以上阻断。本地全依赖与 `--prod` 审计均退出 0，各等级为 0；更新后的 lockfile 已提交。
- API 使用 Trivy 0.74.0 **rootfs** 扫描打包后的实际 Spring Boot JAR。普通 `fs` 扫描可能不解析 JAR；结果没有包清单或没有 Spring Boot 时必须失败。
- Compose 扫描运行中的 API、Web、MySQL、Redis、Prometheus 实际 image ID。保留所有等级和包清单，HIGH、CRITICAL、UNKNOWN 阻断；不使用 `ignore-unfixed`。
- Trivy 从官方发行包下载并验证固定 SHA256。扫描失败、缺失包清单、缺失预期组件或无法判读均不算通过。门禁解析器与 Go 证据检查有 14 项回归测试。
- 原始扫描 JSON、Go 分析与二进制 SHA256/image ID 记录作为 CI artifact 保留 14 天。需要长期发布证据时应在到期前归档。

Prometheus 3.15.0 的两个二进制均含 `golang.org/x/crypto v0.56.0`，Trivy 对 [GO-2026-5932](https://vuln.go.dev/ID/GO-2026-5932) 返回 UNKNOWN。公告仅针对 OpenPGP 包及其子包，不能据此认为整个 crypto 模块的所有使用都受影响。

CI 从当次运行容器复制 `/bin/prometheus` 与 `/bin/promtool`，使用固定 `govulncheck v1.8.0`、官方 Go 漏洞库和 `-mode=binary -scan=package -format=json` 重新分析。只有协议、模式、数据库、实际模块版本、公告范围和模块告警齐全，且没有 OpenPGP 包告警时，才将**该二进制的该条公告**记录为不适用。公告扩大范围、包被编译进来、符号提取的保守回退、空结果、异常或版本不匹配都保持阻断；其他 UNKNOWN 不豁免。完整 Trivy 告警仍保留，不以 JSON 模式退出 0 作为无漏洞证明。该方法的静态分析限制见 [官方 govulncheck 说明](https://pkg.go.dev/golang.org/x/vuln/cmd/govulncheck)。

## 已执行回归与限制

本地仅使用已授权 MySQL `127.0.0.1:13306/audit_flowora`、Redis 16379、API 18080、Web 15173。Web 类型/lint/5 项组件测试/build 通过；后端含真实隔离 MySQL 的 109 项测试，失败/错误/跳过均为 0，V1–V18 validate 通过。库存、财务和 SEC-01–SEC-12 HTTP 回归通过。浏览器抽查登录、财务加载和组织切换；这些抽查不等于完整浏览器端到端覆盖。

首次五个镜像扫描共有 214 条 HIGH/CRITICAL/UNKNOWN 记录（API 37、MySQL 31、Web 42、Redis 4、Prometheus 100）。修复代码提交 `0463b5dfacc6bb5eccf713ed82dcf25ef2e5967a` 的 [CI 36880047882](https://github.com/Marsh-Yan/Flowora-ERP/actions/runs/36880047882) 五项全部通过，实际 artifacts 结果如下：

| 产物 | 包清单记录数 | LOW | MEDIUM | HIGH / CRITICAL | 原始 UNKNOWN |
| --- | --- | --- | --- | --- | --- |
| API JAR | 97 | 0 | 0 | 0 / 0 | 0 |
| API 镜像 | 236 | 10 | 37 | 0 / 0 | 0 |
| MySQL 镜像 | 121 | 0 | 0 | 0 / 0 | 0 |
| Web 镜像 | 71 | 0 | 0 | 0 / 0 | 0 |
| Redis 镜像 | 19 | 0 | 0 | 0 / 0 | 0 |
| Prometheus 镜像 | 439 | 0 | 0 | 0 / 0 | 2 |

后端 JAR 曾余一条 `CVE-2026-49844` / Log4j API MEDIUM，升级 2.25.5 后为 0。两条 Prometheus UNKNOWN 原始记录保留，CI 的两个实际二进制分析均为 OpenPGP 包未出现，`prometheus-triage.json` 记录对应 image ID 和两个 SHA256。因此当次扫描没有未处置的阻断结果，不能改写成“所有镜像零告警”。后续 head 必须重新运行，PR 的最新检查及 artifacts 是合并依据。

中低等级 OS 告警仍会完整保留，后续按修复版本与实际输入边界继续评估。Redis 的 C 运行程序不属于 APK 包清单，扫描只能证明其发行版依赖覆盖；Redis 自身仍需结合上游公告及版本审查。扫描不能发现所有应用逻辑漏洞，也不能替代完整角色/并发/附件矩阵、数据库与附件成对恢复、性能/TLS/告警链路和 Pilot/UAT。F27 仍未完成，不据本批结果宣告 GA。
