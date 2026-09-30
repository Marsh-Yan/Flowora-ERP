# R0 启动与验证记录

日期：2026-09-30。阶段范围：F01、F02、F18、F21；F27 启动。此记录描述本地验证结果，目标 Compose CI 结果以对应 PR 检查为准。

| 编号 | 改动 | 本地结果 | 阶段验收核对 |
|---|---|---|---|
| F01 | production 激活持久化身份、权限、交易、工作流组件；排除 DemoUserStore | 隔离 MySQL 8.0.32 / Redis 3.2 上 readiness `UP`，版本接口正常；未启用 demo seed | 目标 Compose 已启动并就绪；CI 增加空库用户数为 0 的断言，结果以 PR 检查为准 |
| F02 | standalone 只扫描健康、版本及必要安全组件 | 无数据库启动，`/api/v1/health`、`/api/v2/system/version`、`/actuator/health` 均为 200；业务接口不会返回 200；自动化启动测试已加入 | CI API 检查通过 |
| F18 | Maven Wrapper 在 Windows 用 Join-Path 处理普通目录/目录链接 | `mvnw.cmd -version` 输出 Maven 3.9.16；完整测试/打包通过 | CI Windows 普通目录首次下载、链接目录及 Linux API 检查通过 |
| F21 | Redis 健康探针取得与启动命令相同的凭据；API 镜像提供 curl 供就绪探针使用，且缺密码时配置失败 | YAML 解析通过；首轮目标 CI 发现 API 镜像无 wget，补充 curl 后目标 Compose 冒烟通过；本机无 Docker | CI 目标镜像健康、Web `/healthz`、错误密码拒绝及重启恢复均通过 |
| F27 | 增加 standalone 启动测试与生产 Compose 冒烟任务 | Maven 80 tests / 0 failures / 0 skips，package 成功 | CI API 与生产冒烟通过；后续阶段继续扩展安全和业务用例 |

测试命令：

```powershell
& 'D:/softwares/Maven/apache-maven-3.9.16/bin/mvn.cmd' -B -o '-Dmaven.repo.local=E:/code/codex/my_erp/.cache/m2' -pl services/api -am test package
.\mvnw.cmd -version
```

本地生产实例只连接到前次审计授权的隔离 `audit_flowora`，未触及原业务库。测试完成后停止临时 API/MySQL/Redis；隔离数据保留用于复查。已有本地测试库含演示种子；CI 已增加空卷环境不播种用户的断言。

阶段判断：目标 Compose、Redis 凭据与重启、Windows Wrapper 验证已在 [CI run #45](https://github.com/Marsh-Yan/Flowora-ERP/actions/runs/36662628295) 通过；空库不播种断言以更新后的 PR 检查为准。阶段仍须审核通过后才进入 R1。本机 Docker 不可用，本地 Redis 3.2 的结果由目标 Redis 7.4 CI 验证补足。
