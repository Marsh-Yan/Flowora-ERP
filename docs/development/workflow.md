# 开发工作流

## 分支

- `main`：可构建、可验证的稳定分支。
- `feature/<short-name>`：新功能。
- `fix/<short-name>`：缺陷修复。
- `docs/<short-name>`：文档变更。

## 提交

使用 Conventional Commits：

```text
feat(web): add dashboard shell
fix(api): handle invalid workflow state
docs: update local setup guide
chore: update build tooling
```

## 本地验证

提交前至少执行：

```powershell
pnpm verify:web
mvn -B -pl services/api -am test
```

## 环境变量

不得提交密钥、密码或真实数据库地址。使用 `.env.example` 提供变量名称和说明。
