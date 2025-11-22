# Knex 迁移与数据库初始化

本文介绍如何在新的目录结构下使用 Knex 管理 SonicWave 后端的 MySQL Schema。

## 使用的工具与配置来源
- 数据库迁移工具：Knex（配合 `mysql2` 驱动）。
- 迁移目录：`apps/backend-api/db/migrations/`（如有 seeds，则位于 `apps/backend-api/db/seeds/`）。
- 连接配置：从 `apps/backend-api/.env` 读取，再由 `apps/backend-api/knexfile.js` 提供默认值与目录。

## 常用命令
在 `apps/backend-api` 目录下执行：

- 初始化/升级 schema：
  ```bash
  npm run db:migrate
  ```
- 回滚最近一批迁移（如需）：
  ```bash
  npm run db:rollback
  ```
- 运行 seeds（若存在 seed 文件）：
  ```bash
  npm run db:seed
  ```

## 从空数据库到可登录 admin-web 的步骤
1. 启动本地 MySQL（或 Docker 容器），并保证 `.env` 中的 `DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`、`DB_NAME` 与 `knexfile.js` 默认值一致。
2. 进入后端目录并执行迁移：
   ```bash
   cd apps/backend-api
   npm install
   npm run db:migrate
   ```
3. 在 `.env` 中设置管理员种子：
   ```env
   ADMIN_SEED_EMAIL=admin@example.com
   ADMIN_SEED_PASSWORD=Admin123!
   # ADMIN_SEED_USERNAME 默认为 admin，如需自定义可在此设置
   ```
4. 启动后端，观察日志确认 AdminService 自动创建或提升管理员账号：
   ```bash
   npm run dev
   ```
5. 启动后台管理前端并登录：
   ```bash
   cd ../admin-web
   npm install
   npm run dev
   ```
   打开浏览器访问 `http://localhost:5173`，使用步骤 3 中的管理员账号登录。

## 说明
Phase 1 之后仓库不再提交或挂载 MySQL 运行时 data 目录。初始化数据库的规范方式是运行上述 Knex 迁移，而不是恢复旧版本的 data 文件夹。
