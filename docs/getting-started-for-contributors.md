# Getting Started for Contributors

欢迎来到 SonicWave V4 开发仓库。本指南补充 Android 客户端的上手路径，并在 Phase 2 中说明振动会话域的 MVVM 试点结构。

## Android 振动会话域（Phase 2 试点）
- **核心入口**：`apps/android/src/main/java/com/example/sonicwavev4/ui/home/`（首页快速会话）。
- **MVVM 结构**：
  - UI（Fragment）仅渲染 `VibrationSessionUiState`，将按钮/输入事件封装成 `VibrationSessionIntent` 并交给 ViewModel。
  - `HomeViewModel` 负责会话状态管理、参数校验、倒计时与硬件/网络协调，依赖网关接口而非具体仓库类。
  - 共享模型与接口位于 `apps/android/src/main/java/com/example/sonicwavev4/core/vibration/`，可直接复用于预设/自设/专家模式。
- **阅读顺序建议**：
  1. 先阅读 `docs/architecture/domain-overview.md`、`docs/architecture/domain-usecases.md` 了解振动会话域的用例编号（1-1/1-2/1-3）。
  2. 再看 `docs/architecture/phase2-android-vibration-mvvm.md`，理解试点范围、状态/事件模型与目录规划。
  3. 最后按照文档路径进入 `HomeViewModel` 与 `HomeFragment`，对照 `core/vibration` 模板查阅状态流与 Intent 处理。
- **原则**：UI 只负责展示与收集输入，不直接调用硬件或网络；业务状态、参数转换、Start/Stop 流程统一在 ViewModel 或 UseCase 层完成。

后续 Phase 会逐步将预设模式、自设模式、专家模式等其他振动入口迁移到同一模板，保持行为稳定的同时降低 UI 逻辑重复。

## Android 客户与账户域（Phase 3 收敛）
- **核心入口**：`apps/android/src/main/java/com/example/sonicwavev4/ui/login/`（登录/注册）、`ui/customer/`（客户列表、详情与新增弹窗）、`ui/user/UserFragment`（账户主页与登出）。
- **MVVM 结构**：
  - `LoginViewModel` 暴露 `AuthUiState` / `AuthEvent`，处理登录、注册、离线登录、登出，依赖 `AuthRepository` 统一管理 Token、Session 与心跳。
  - `CustomerViewModel` 暴露 `CustomerListUiState` / `CustomerEvent`，统一客户列表加载、搜索、选中、在线新增/更新与离线新增，桥接 `CustomerRepository` 与 `OfflineCustomerRepository`。
  - UI Fragment/Dialog 只负责采集输入并订阅状态/事件，导航逻辑与提示保持原有行为。
- **阅读顺序建议**：
  1. 阅读 `docs/architecture/domain-overview.md`、`docs/architecture/domain-usecases.md` 了解用例 2-1/2-2/2-3。
  2. 参考 `docs/architecture/phase2-android-vibration-mvvm.md` 与 `docs/architecture/phase3-android-account-customer-mvvm.md` 了解跨域的 UiState/Intent 模板。
  3. 进入 `LoginViewModel`、`CustomerViewModel` 对照 UI 文件查看状态渲染与事件分发路径，再根据需要下钻到对应仓库/数据源。

## Backend API 快速上手（apps/backend-api）
- **环境要求**：Node.js 18+、本地 MySQL 8（或 Docker 运行的 mysql 服务）、npm。可选：Docker Compose 以启动 `apps/backend-api/docker-compose.yml`。
- **关键环境变量**（在 `apps/backend-api/.env` 中配置）：
  - 数据库：`DB_HOST`（默认 `127.0.0.1`）、`DB_PORT`（默认 `3306`）、`DB_USER`（默认 `sonicwave`）、`DB_PASSWORD`（默认 `sonicwave_pwd`）、`DB_NAME`（默认 `sonicwave_db`）。
  - 管理员种子：`ADMIN_SEED_EMAIL`、`ADMIN_SEED_PASSWORD`（可选 `ADMIN_SEED_USERNAME`，默认 `admin`）。
- **初始化数据库与启动后端**：
  ```bash
  cd apps/backend-api
  npm install        # 首次克隆仓库需要
  npm run db:migrate # 使用 Knex 迁移创建/升级所有表
  npm run dev        # 启动 backend HTTP + WebSocket 服务
  ```
- **管理员账号种子逻辑**：后端启动时，若 `.env` 提供了 `ADMIN_SEED_EMAIL` 和 `ADMIN_SEED_PASSWORD`，`AdminService` 会检查 `users` 表并自动创建/提升管理员账号（依赖迁移创建的 `users.role`、`users.account_type` 字段）。
- **admin-web 登录提醒**：
  ```bash
  cd apps/admin-web
  npm install
  npm run dev
  ```
  浏览器访问 `http://localhost:5173`，使用上一步配置的管理员账号登录。
