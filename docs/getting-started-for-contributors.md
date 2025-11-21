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
