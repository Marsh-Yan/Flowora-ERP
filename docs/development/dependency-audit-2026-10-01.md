# R5 依赖扫描记录（2026-10-01）

> 历史范围：本页保留 R5 第一批升级前的扫描，告警与“未执行”状态均是当时结果。R5 第二批已更新 lockfile、后端依赖与运行镜像，并加入持续门禁；当前记录见 [依赖与镜像整改](dependency-remediation-2026-10-01.md)。不要将旧扫描用作当前版本结论。

基于 R4 合并提交 `a338671df5f9e604beb74eb18a15112d58c1ee84` 的现有 lockfile，执行 `pnpm audit --registry=https://registry.npmjs.org --json` 与 `pnpm audit --prod --registry=https://registry.npmjs.org --json`。两次均退出 1：全部依赖 25 条（15 high、10 moderate），生产依赖 13 条（8 high、5 moderate），critical 为 0。扫描使用 npm 官方审计端点；默认旧镜像源不提供此接口，不能将该错误当作零漏洞。

这是依赖版本匹配结果，**没有证明应用存在 25 个可利用漏洞**。生产依赖分类也不等于所有包都进入浏览器产物；Axios 的 Node HTTP/HTTP2 问题与浏览器适配器问题须分别判断。原始扫描与依赖路径留在本地，不包含在公开报告中。本批不修改 lockfile，安全发布门禁保持未通过。

## 当前命中与审计建议版本

下表版本来自这次审计响应的 `findings.version` 和 `patched_versions`，不是对未来版本的永久安全保证。升级前确认上游修复、当前版本、兼容性和 lockfile，升级后重新扫描并运行全部回归。

| 包 | 命中版本 | 记录数 | 审计响应的修复范围 | 范围 |
| --- | --- | --- | --- | --- |
| axios | 1.19.0 | 12（7 high / 5 moderate） | >=1.20.0 | `--prod` 也命中；区分浏览器/Node 路径 |
| nanoid | 3.3.17 | 1 high | >=3.3.18 | `--prod` 也命中；检查实际产物及自定义生成器调用 |
| vitest / @vitest/mocker | 3.2.7 | 2 moderate | >=4.1.11 | 开发测试工具；跨主版本需验证配置 |
| js-yaml | 4.3.1 | 1 high | >=4.3.2 | 开发工具依赖路径 |
| brace-expansion | 1.1.18 / 2.1.4 / 5.0.9 | 9（6 high / 3 moderate） | 对应 >=1.1.21 / >=2.1.7 / >=5.0.12 | 三个依赖分支分别命中三个公告 |

## 公告清单

| 公告 | 包 / 等级 | 审计描述摘要 |
| --- | --- | --- |
| [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8) | nanoid / high | 自定义生成器 size=0 无限循环 |
| [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) | vitest、@vitest/mocker / moderate，2 条 | mock 重定向的路径遍历和文件读取 |
| [GHSA-2883-xcg3-v3hh](https://github.com/advisories/GHSA-2883-xcg3-v3hh) | js-yaml / high | 空 merge source 可绕过 CPU 限制 |
| [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr) | brace-expansion / moderate，3 条 | 展开复杂度导致 CPU 拒绝服务 |
| [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7) | brace-expansion / high，3 条 | 嵌套递归耗尽栈 |
| [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) | brace-expansion / high，3 条 | comma parts 递归耗尽栈 |
| [GHSA-vh66-26gq-q6x8](https://github.com/advisories/GHSA-vh66-26gq-q6x8) | axios / moderate | fetch 适配器原型污染 gadget 改变请求 |
| [GHSA-9fr6-4gfg-395g](https://github.com/advisories/GHSA-9fr6-4gfg-395g) | axios / moderate | 继承的 method 覆盖请求方法 |
| [GHSA-c29m-xwm3-cm6r](https://github.com/advisories/GHSA-c29m-xwm3-cm6r) | axios / high | data URI 正则阻塞 Node 事件循环 |
| [GHSA-mghh-pgcx-3jjj](https://github.com/advisories/GHSA-mghh-pgcx-3jjj) | axios / high | 代理 host 归一化正则拒绝服务 |
| [GHSA-x97p-jq2g-jp4f](https://github.com/advisories/GHSA-x97p-jq2g-jp4f) | axios / high | toFormData 选项原型污染 gadget |
| [GHSA-3pq3-5fj3-cg6v](https://github.com/advisories/GHSA-3pq3-5fj3-cg6v) | axios / high | HTTP/2 绕过 DNS lookup 和代理控制 |
| [GHSA-542g-h47m-68v8](https://github.com/advisories/GHSA-542g-h47m-68v8) | axios / high | HTTP/2 初始化 error 未处理导致拒绝服务 |
| [GHSA-j8rh-479h-cp32](https://github.com/advisories/GHSA-j8rh-479h-cp32) | axios / moderate | 继承 headers 造成请求头注入 |
| [GHSA-4hqw-qxg8-jxx2](https://github.com/advisories/GHSA-4hqw-qxg8-jxx2) | axios / moderate | fetch 的 FormData getHeaders 请求头注入 |
| [GHSA-m8m8-qj5v-23w3](https://github.com/advisories/GHSA-m8m8-qj5v-23w3) | axios / high | Node 继承 createConnection 劫持 socket |
| [GHSA-44g4-m2mj-wpvx](https://github.com/advisories/GHSA-44g4-m2mj-wpvx) | axios / moderate | CIDR NO_PROXY 条目被忽略 |
| [GHSA-r4gj-5m52-g5wh](https://github.com/advisories/GHSA-r4gj-5m52-g5wh) | axios / high | fetch 未执行 maxRedirects=0 |

## 下一批放行条件

先处理 Axios 和生产依赖路径，再升级测试工具和传递依赖。禁止只提高版本声明而不更新 lockfile，或用忽略 high 的参数隐藏结果。记录修复后 exact versions、浏览器登录/MFA/CSRF/附件/导出回归以及完整 Web/API/隔离 HTTP CI；再执行全量和 `--prod` 审计。

后端 Maven 依赖及目标容器镜像扫描在本批**未执行**，不能从 pnpm 结果推断其安全。完整发布门禁见 [audit-verification.md](audit-verification.md)。
