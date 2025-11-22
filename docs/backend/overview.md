# Backend API 概览

后端代码位于 `apps/backend-api`，提供 HTTP 与 WebSocket 服务，并以 Knex 管理 MySQL Schema。

## 目录结构速览
- `controllers/`：HTTP 路由入口，例如 `auth.controller.js`、`users.controller.js`、`customer.controller.js`、`device.controller.js`、`heartbeat.controller.js`、`logs.controller.js`、`operations.controller.js`、`music.controller.js`、`featureFlags.controller.js`。
- `routes/`：与控制器一一对应的路由定义。
- `services/`：领域服务与跨领域组合逻辑，包含 `admin.service.js`（管理员种子与后台配置）、`auth.service.js`、`customer.service.js`、`device.service.js`、`featureFlags.service.js` 等。
- `repositories/` / `db/`：数据库访问层与 Knex 迁移文件（`db/migrations`）。
- `realtime/`：WebSocket 控制通道（如 `offlineControlChannel.js`）。
- `config/`、`middleware/`、`utils/`：配置文件、鉴权/日志等中间件与公共工具。

## 典型请求链路示例
- **登录**：`POST /api/auth/login` → `routes/auth.routes.js` → `controllers/auth.controller.js` → `services/auth.service.js`（校验账号、生成 token）→ 数据库查询 `users` 表 → 返回 JWT 与用户信息。
- **离线模式控制（WebSocket）**：客户端通过 WebSocket 连接 `realtime/offlineControlChannel.js` → channel 调用 `services/featureFlags.service.js`、`services/device.service.js` 更新功能开关和设备权限 → `repositories` 层更新数据库 → 通过 channel 广播最新状态给在线设备。

## 运行要点
- 环境配置见 `apps/backend-api/knexfile.js` 与 `.env`，推荐通过 `npm run db:migrate` 初始化数据库，再使用 `npm run dev` 启动服务。
- 管理员账号可通过设置 `ADMIN_SEED_EMAIL` 与 `ADMIN_SEED_PASSWORD` 在服务启动时自动创建。
