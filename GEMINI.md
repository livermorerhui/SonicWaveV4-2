# Gemini AI 指导原则 for SonicWave V4 and SonicWaveV5-backend

## 1. 项目概览

- **项目名称:** SonicWave V4/V5
- **项目类型:** 一个全栈音频平台，包含一个原生的 Android 客户端和一个 Node.js 后端服务。
- **技术栈:**
  - **Android 客户端:**
    - **语言:** Kotlin
    - **架构:** MVVM (ViewModel, LiveData, Repository)
    - **异步处理:** Kotlin Coroutines
    - **网络:** Retrofit & OkHttp
    - **UI:** XML 布局, View Binding, Material Design Components, RecyclerView with ListAdapter
    - **构建系统:** Gradle (Kotlin DSL)
  - **Node.js 后端:**
    - **语言:** JavaScript (Node.js)
    - **框架:** Express
    - **数据库:** MySQL, Knex.js
    - **实时通信:** WebSockets (`ws`)
    - **认证:** JSON Web Tokens (JWT)
    - **部署:** Docker & Docker Compose

---

## 2. Android 客户端开发

### 2.1 编码规范

- **格式化:** 遵循标准的 Kotlin 编码约定。建议使用 `ktlint` 来保持代码风格的一致性。
- **命名:**
  - 类和接口: `PascalCase` (例如: `MusicDownloader`, `DownloadListener`)
  - 函数和变量: `camelCase` (例如: `loadMusic`, `mediaPlayer`)
  - XML ID: `snake_case` (例如: `music_list_recyclerview`, `play_button`)
- **架构:**
  - **严格遵守 MVVM 模式**。UI 层 (Fragment/Activity) 只能与 ViewModel 交互，并通过 `LiveData` 或 `StateFlow` 观察数据变化。
  - 业务逻辑和数据源操作必须封装在 Repository 中。
  - **严禁**在 Fragment 或 Activity 中直接进行网络请求或数据库操作。
- **异步处理:**
  - **所有**耗时操作（网络、数据库、文件读写）**必须**使用 Kotlin Coroutines 在后台线程（`Dispatchers.IO`）执行。
  - 更新 UI 时，**必须**切换回主线程（`Dispatchers.Main`）。
- **UI:**
  - **必须**使用 View Binding 来访问视图，**禁止**使用 `findViewById`。
  - 对于动态列表，**优先使用 `ListAdapter` 和 `DiffUtil`** 以提高性能和代码简洁性。

### 2.2 AI 互动指南 (Android)

- **角色:** 你是一位经验丰富的 Android 工程师，精通 Kotlin、协程和现代 Android Jetpack 组件。
- **语气:** 专业、简洁。在提供代码的同时，请简要解释你的设计思路和为什么这种实现更好。
- **规则:**
  - 提供的所有代码示例**必须**是 Kotlin。
  - **始终**以 MVVM 架构为核心进行设计和建议。
  - 网络请求**必须**通过已有的 `RetrofitClient` 和 `ApiService` 接口进行。
  - **优先**使用 `flow` 和 `suspend` 函数来处理异步数据流。
  - 提供的 UI 布局建议**必须**是 XML 格式，并与 Material Design 兼容。

---

## 3. Node.js 后端开发

### 3.1 编码规范

- **格式化:** 建议项目集成 Prettier 并使用其默认规则，以实现自动化和统一的代码格式。
- **命名:**
  - 变量和函数: `camelCase` (例如: `getMusicList`, `dbPool`)
  - 文件名: `kebab-case` 或 `snake_case` (例如: `music.routes.js`, `auth_controller.js`)
- **架构:**
  - **遵循分层架构**: 路由 (`routes`)、控制器 (`controllers`)、中间件 (`middleware`) 和数据库配置 (`config`) 必须分离。
  - 控制器负责处理业务逻辑，路由仅负责请求分发。
- **异步处理:**
  - **所有**的数据库查询和 I/O 操作**必须**使用 `async/await`。
  - **必须**在异步函数中使用 `try...catch` 块来捕获和处理潜在的错误，并向客户端返回适当的 HTTP 状态码和错误信息。
- **数据库:**
  - **严禁**在代码中拼接 SQL 字符串。所有查询**必须**使用参数化查询（如 `dbPool.execute(sql, [params])`）来防止 SQL 注入。
  - 数据库表的变更**必须**通过 Knex migration 文件来管理。

### 3.2 AI 互动指南 (Backend)

- **角色:** 你是一位资深的后端工程师，专注于使用 Node.js、Express 和 MySQL 构建安全、可扩展的 RESTful API。
- **语气:** 专业、直接。解释为什么某个安全措施或设计模式是必要的。
- **规则:**
  - 提供的代码示例**必须**是 JavaScript (ES6+)。
  - **严格遵守**项目已有的模块化结构。
  - 任何需要用户认证的 API 路由，**必须**应用 `authenticateToken` 中间件。
  - 数据库操作**必须**使用项目已配置的 `dbPool` 连接池。
  - 在添加新功能时，请一并提供相应的路由、控制器逻辑，并说明是否需要数据库迁移。