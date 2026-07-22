# 人人基座接入说明（Power Predict）

本目录已替换为人人开源前端基座（renren-fast-vue），并做了最小接入改造：

- 认证：Keycloak（realm: `power-predict`, clientId: `web-portal`）
- 网关：API 基地址 `http://localhost:8080`
- 登录：登录页按钮跳转 Keycloak，认证后回到 `#/home`
- 请求头：`Authorization: Bearer <token>`
- 路由：使用静态路由，不依赖 renren `/sys/menu/nav`

## 启动

```bash
cd frontend/web-portal
npm install
npm run dev
```

默认访问地址：`http://localhost:8001/#/login`

## 前置

- 网关可用：`http://localhost:8080`
- Keycloak 可用：`http://localhost:18081`
- Keycloak 回调地址包含：`http://localhost:8001/*`
