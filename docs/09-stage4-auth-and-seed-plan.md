# Stage 4 认证鉴权与种子数据实施清单

## 1. 目标

在当前已完成的 PostgreSQL 和本地基础设施之上，补齐可演示、可联调、可验收的最小认证与数据初始化闭环。

## 2. 先做什么

1. 先准备本地 seed 数据，保证 Navicat、后端接口和后续页面都有可见数据。
2. 再接入 Keycloak/OIDC/OAuth2，先打通登录和 JWT 校验。
3. 最后把网关和业务服务切到 Resource Server 模式。

## 3. 本阶段实施清单

### 3.1 种子数据

- 准备最小电站数据。
- 准备最小用户数据。
- 为本地验证准备一条可选的预测任务样例。
- 提供可重复执行的一键脚本。

### 3.2 认证底座

- 选定 Keycloak 作为本地 IdP。
- 定义 realm、client、role、scope 的最小集合。
- 约定前端登录方式为 Authorization Code + PKCE。
- 约定网关和服务的 JWT 校验与租户透传方式。

### 3.3 验收目标

- Navicat 能看到业务库和 seed 数据。
- 本地登录后能拿到可用 Access Token。
- 网关能识别用户身份并传递租户信息。
- 服务端能按 scope 和 role 做基础权限控制。

## 4. 当前状态

- PostgreSQL schema 已完成。
- PostgreSQL seed data 已开始实施。
- Keycloak 认证底座待落地。
