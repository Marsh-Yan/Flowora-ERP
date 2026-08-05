# ADR-0001：前后端分离的国际化 Monorepo

## 状态

Accepted

## 决策

使用以下技术基线：

- 前端：Vue 3 + Composition API + TypeScript + Vite + Element Plus。
- 后端：Java 25 LTS + Spring Boot 模块化单体 + Maven 3.9+。
- 数据库：MySQL 8.0。
- 缓存与会话：Redis。
- API：REST + OpenAPI。
- 仓库：单仓库 Monorepo。

## 原因

Vue 3 和 Vite 适合大型后台应用的快速迭代；Element Plus 能覆盖 ERP 高频的表格、表单、树、分页和弹窗场景。Java 服务负责事务、权限、流程和数据库，避免业务逻辑分散在前端。

Spring Boot 按业务域组织包结构，先保持模块化单体，避免首期引入微服务部署复杂度；未来可以按照模块边界拆分服务。

## 国际化边界

- 前端使用 `vue-i18n` 管理业务文案。
- Element Plus 使用 `el-config-provider` 配置组件语言。
- 服务端返回 ISO 8601 时间、货币代码、十进制定额和消息键。
- 金额在 Java 中使用 `BigDecimal`，数据库使用定点数类型。

## C 盘控制

项目级 npm、pnpm、Maven 缓存固定在 `.cache/`。运行时本身不迁移；系统级缓存不承诺完全消除。
