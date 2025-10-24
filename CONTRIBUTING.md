# SonicWave 开发约定

本文档适用于 SonicWave 项目的所有开发者与协作式 AI。开始任何开发任务前，请通读并遵守以下原则与流程。

---

## 1. 总体原则

1. **先理解、后动手**  
   - 在编码前先梳理“UI → ViewModel → Repository → 网络接口 → 后端控制器 → 数据库”完整流程。  
   - 任何功能都必须确认现有数据结构、接口约束及异常路径后，再给出修改方案。  
   - 对复杂改动或跨层修改，必须先撰写计划与设计说明，并获确认后执行。

2. **后端优先，迁移先行**  
   - 所有数据库结构变化必须通过 Knex migration 完成，并伴随可回滚脚本。  
   - 控制器层需要校验入参、记录上下文日志，并返回清晰的错误码。  
   - 接口签名稳定后，才能启动客户端侧的实现。

3. **严格 MVVM / 分层架构**  
   - Android 端：UI 仅负责展示与交互；业务逻辑集中在 ViewModel；Repository 负责 I/O；状态使用 LiveData/Flow，事件使用 SharedFlow。  
   - 后端：保持 routes → controllers → services/repositories 的清晰分层，避免跨层直接访问数据库。

4. **单一会话来源**  
   - `SessionManager` 仅负责 token 与用户基础信息的持久化；运行期账号态统一由 `UserViewModel` 或对应模块的 ViewModel 缓存。  
   - 登出流程：先广播状态变化（例如通过 `GlobalLogoutManager`），让前端/硬件有机会清理，再清除本地缓存和 token。

5. **实时审计与停止原因**  
   - 播放、硬件、用户操作等关键流程必须在状态变化时及时上传事件（开始、调参、停止、异常）。  
   - 停止指令必须带上明确的 reason（如 `manual`、`logout`、`countdown_complete`、`hardware_error` 等）以及必要的 detail。

6. **防御式错误处理**  
   - 所有网络、数据库、硬件调用都要使用 try/catch 包裹，失败时打日志并回滚本地状态。  
   - 后端捕获异常时需记录上下文信息（用户、接口、参数），以便排查。

---

## 2. 标准开发流程

1. **需求理解 & 方案设计**  
   - 梳理现状、痛点、目标；列出需要修改的层级；在文档或对话中确认方案。  
   - 复杂需求需先产出设计说明（含数据流、接口变更、异常场景处理）。

2. **后端改造**  
   - 编写/执行 Knex migration；更新控制器、路由与服务；添加参数校验、日志。  
   - 在本地运行 `npx knex --knexfile ./knexfile.js migrate:latest`，确保脚本生效。  
   - 同步更新 Swagger 文档或接口描述（如有）。

3. **Android 客户端实现**  
   - 更新网络模型（`OperationData.kt` 等）、`ApiService`、Repository。  
   - 在 ViewModel 中实现业务逻辑与状态管理；UI 只做展示、观察 ViewModel 状态。  
   - 确保 `UserViewModel` / `SessionManager` 的登录态变化会及时传递到业务模块。

4. **音频/硬件联动**  
   - 播放期间的频率、强度、时间调整需立即影响硬件与本地模拟音频。  
   - 确保硬件断开或异常时，能够停止播放并上报 `hardware_error`。

5. **验证与回归**  
   - 后端：启动服务并检查日志；确认数据表结构正确。  
   - Android：执行 `./gradlew :app:compileDebugKotlin`（需安装 JDK），通过编译后在模拟器/真机上复测关键场景。  
   - 手工验证主要路径：  
     1. 未登录：按键应禁用。  
     2. 登录普通账号：硬件未就绪时禁止启动。  
     3. 登录测试账号：无 CH341 也能播放，并实时上报调参。  
     4. 停止原因：手动停止、登出、倒计时结束、硬件断开都能写入数据库。  
   - 检查数据库 `user_operations`、`user_operation_events` 是否有正确记录。

6. **安全与工具**  
   - 定期运行 `npm audit`，记录无法立即修复的 moderate/low 风险；对高危漏洞需优先处理。  
   - Android/Node 项目保持 lint、格式化工具开启（如 ktlint、ESLint）。

7. **交付与文档**  
   - 完成开发后更新相关 README / CHANGELOG / 接口文档。  
   - 在提交或交付说明里列出：改动摘要、数据库迁移、验证步骤、已知风险。

---

## 3. 与 AI 协作的要求

1. 每次启动 AI 助手（如 Gemini CLI）前，明确指出需遵循本 `CONTRIBUTING.md`。  
2. AI 给出方案前必须先分析现状、列出修改点；未经确认不得擅自变更。  
3. AI 进行后端改动时，必须先写 migration，再改控制器；前端改动必须遵循 MVVM 与上述状态原则。  
4. 修改完成后，AI 要提供验证步骤和未执行的校验项；若由于环境限制未运行编译/测试，必须在总结中说明。  
5. 若 AI 在调试中遇到连续失败，应按照文档的“错误处理与上报机制”及时停止并反馈。

---

## 4. 适用范围

本约定适用于：

- Android 客户端模块（SonicWaveV4、SonicWaveV1 等）；  
- Node.js 后端（`SonicWaveV5-backend`、`SW-backend-admin` 等）；  
- 任何新增的服务或对话框开发，除非目标项目另有更严格的规范。

如需对流程进行补充或调整，请在合并前更新该文档，并通知所有协作者与 AI。谢谢配合。  
