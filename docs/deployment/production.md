# Flowora ERP 2.0 生产部署

## 基线架构

`web` 仅暴露 8080，反向代理同源 `/api/` 到 API。MySQL、Redis、API 管理端口和 Prometheus 只在容器网络内可见。附件与导出、数据库、Redis 和监控数据均使用独立持久卷。

## 上线前准备

1. 复制 `.env.example` 为部署平台的密钥配置，生成彼此独立的数据库、Redis 和 MFA 密钥；不得提交 `.env`。
2. 将 `FLOWORA_ALLOWED_ORIGINS` 设置为最终 HTTPS 域名，不使用通配符。
3. 在独立副本完成升级演练、控制总数核对和恢复演练。
4. 配置 TLS 终止、日志采集、卷级备份与告警接收人。

## 启动与验证

```text
docker compose build
docker compose up -d
docker compose ps
```

验证 `/healthz` 返回 `UP`、`/api/v2/system/version` 显示 M5，并以各角色执行登录、查询与一笔可回滚的业务冒烟测试。Prometheus 指标不可从公网访问。

## 安全基线

Compose 的 MySQL 与 Redis 使用仓库 Dockerfile 构建：MySQL 8.4.11 移除未使用的 mysql-shell，保留 mysql/mysqladmin/mysqldump，以固定源码和校验和重建 gosu；API、Web、MySQL、Redis 在构建时更新发行版补丁，Prometheus 使用 3.15.0。构建和更新须重新执行五镜像扫描及目标启动检查，不能依赖可变标签的旧结果。依赖版本、扫描边界及实际二进制分析见 [安全整改记录](../development/dependency-remediation-2026-10-01.md)。数据库与附件备份恢复仍需独立演练。

- 生产配置强制安全、HttpOnly、SameSite 会话 Cookie，CSRF、CSP、防嵌入和来源策略保持开启。
- v1 只允许读取和认证兼容；v1 业务写入返回 426，客户端全部使用 v2。
- 演示数据关闭；诊断包只包含白名单配置和健康状态。
- 数据库账号采用最小权限，root 不用于应用运行；所有密钥由部署平台注入。

## 容量与扩缩容

应用数据库连接使用 UTC 会话及瞬间保持，`DB_URL` 不应覆盖为其他连接时区或关闭相关驱动属性。升级至 V19 时只补缺失组织的财务设置；配置要求、历史时间边界和验证方式见 [组织财务与时间](../development/organization-finance-time.md)。这不会设置数据库全局时区或修正历史误写时间。

先观察数据库连接池、请求延迟、导出积压、工作流逾期和 outbox 失败。API 可水平扩容，但必须共享 Redis 会话、MySQL 和持久化附件/导出存储。导出行数及保留期通过环境变量限制。
