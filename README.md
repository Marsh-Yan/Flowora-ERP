# Flowora ERP

> An international, workflow-first ERP workspace for modern businesses.

Flowora ERP is an enterprise-grade showcase project built as a front-end/back-end monorepo. It focuses on clear business workflows, internationalization, operational visibility, and collaboration.

## 技术栈 / Stack

- Web: Vue 3, TypeScript, Vite, Element Plus
- API: Java 25 LTS, Spring Boot, Maven 3.9+
- Data: MySQL 8.0, Redis
- Contract: OpenAPI
- Quality: ESLint, Prettier, Vitest, JUnit, GitHub Actions

## 目录 / Structure

```text
apps/web/          Vue 3 web application
services/api/      Spring Boot API
docs/              Product and engineering documentation
.github/           CI and collaboration templates
```

## 本地要求 / Prerequisites

- Node.js 24+
- pnpm 11+
- Java 25 LTS
- Maven 3.9+
- MySQL 8.0+
- Redis 7+

项目依赖缓存默认配置到项目根目录的 `.cache/`，以减少 C 盘写入；本项目位于 E 盘时，缓存也会留在 E 盘。

## 启动前端 / Start the web app

```powershell
pnpm install
pnpm dev:web
```

## 启动后端 / Start the API

默认启动只加载健康检查，不要求数据库服务。连接 MySQL/Redis 时使用 `local` profile：

```powershell
mvn -pl services/api -am spring-boot:run -Dspring-boot.run.profiles=local
```

## 质量检查 / Verification

```powershell
pnpm verify:web
mvn -B -pl services/api -am test
```

## 项目规范 / Project process

详见：

- [项目章程](docs/product/project-charter.md)
- [技术架构 ADR](docs/architecture/adr-0001-stack.md)
- [开发工作流](docs/development/workflow.md)
- [安全与隐私规范](docs/development/security.md)
- [OpenAPI 契约](docs/api/openapi.yaml)

## License

This project is currently intended as a portfolio demo. Licensing will be finalized before public release.
