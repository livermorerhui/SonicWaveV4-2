# Phase 2 – Android 振动会话域 MVVM 试点

## 背景与目标
- 振动会话域负责组合频率、强度、时长并触发硬件输出，是 1-1/1-2/1-3 用例链路的核心。
- 本次 Phase 2 聚焦 **首页快速会话（手动参数输入）→ 开始/停止振动** 的完整链路，保持业务参数与硬件调用不变，同时抽取可复用的 MVVM 结构模板，方便后续扩展到预设模式、自设模式等分支。

## 当前实现概览（Before）
- 入口：`apps/android/src/main/java/com/example/sonicwavev4/ui/home/HomeFragment.kt`。
- 主要业务逻辑分散：
  - Fragment 中负责输入高亮、数值格式化、按钮状态切换。
  - `HomeViewModel` 同时维护多组 `LiveData`，直接驱动硬件仓库与会话仓库。
  - 硬件与会话依赖直接绑定到具体仓库类，测试隔离成本高。
- 调用链：
  - HomeFragment 收集用户输入 → 直接调用 ViewModel 方法修改数值 → ViewModel 将参数下发到 `HomeHardwareRepository`、`HomeSessionRepository`。
  - Start/Stop 状态通过倒计时更新 UI，完成后停止硬件输出并写入 Session 日志。

## 目标 MVVM 结构（After）
- 分层说明：
  - **UI 层**：仅负责渲染 `VibrationSessionUiState`、将用户操作封装为 `VibrationSessionIntent` 并交给 ViewModel。
  - **ViewModel 层**：集中持有会话状态（频率/强度/时长、倒计时、运行状态），处理 Intent，协调硬件/会话仓库，并暴露事件（Toast/Error）。
  - **数据/UseCase 层**：通过 `VibrationHardwareGateway` 与 `VibrationSessionGateway` 抽象具体仓库，便于后续替换或扩展为独立 UseCase 模块。
  - **硬件/网络层**：沿用现有 `HomeHardwareRepository`、`HomeSessionRepository` 实现，不改动参数与行为，仅通过接口暴露。
- 目录规划示例：
  - `core/vibration/`：放置网关接口、UI 状态与 Intent 模板。
  - `ui/home/`：HomeFragment（UI）与 HomeViewModel（会话状态机）。
  - 其他会话相关 UI（如预设/自设模式）可按包内新建 `viewmodel/` 或复用 `core/vibration` 模型。

## 试点用例的状态与事件模型
- **UI 状态模型**：`VibrationSessionUiState`
  - 核心字段：`frequencyValue`、`intensityValue`、`timeInMinutes`、`countdownSeconds`、`isRunning`、`startButtonEnabled`、`activeInputType`、`isHardwareReady`、`isTestAccount`、`playSineTone`，以及格式化后的展示文案。
- **用户意图/事件**：`VibrationSessionIntent`
  - 输入/编辑：`SelectInput`、`AppendDigit`、`DeleteDigit`、`ClearCurrent`、`CommitAndCycle`。
  - 参数调整：`AdjustFrequency`、`AdjustIntensity`、`AdjustTime`。
  - 会话控制：`ToggleStartStop`、`ClearAll`。
- **处理流程示例**：
  1. UI 发送 `SelectInput("frequency")` → ViewModel 切换缓冲区并刷新状态。
  2. 输入/调整参数 → ViewModel 更新状态并调用硬件仓库 `applyFrequency/applyIntensity`。
  3. `ToggleStartStop` → ViewModel 校验登录/硬件状态 → 调用会话仓库 `startOperation`，启动硬件输出/倒计时 → 状态切换为 Running。
  4. 再次 `ToggleStartStop` 或倒计时结束 → 停止输出、记录 stop 事件、恢复初始展示。

## After: 可复用的模板要点
- 以 `VibrationSessionUiState` + `VibrationSessionIntent` 驱动 UI，避免 Fragment 手写格式化与状态判断。
- 通过 `VibrationHardwareGateway`、`VibrationSessionGateway` 解耦 ViewModel 与具体仓库，实现可替换的 UseCase/Repository 或测试桩。
- UI 只收集 `StateFlow` 并渲染，所有业务校验、状态切换、硬件/网络调用都在 ViewModel 内统一处理。
- 倒计时、Start/Stop、Toast/Error 等通用逻辑可直接在其他会话入口（预设、自设、专家模式）复用或平移。

## 与后续 Phase 的衔接
- 本次仅覆盖 “首页手动参数 → 振动开始/停止” 链路；预设模式、自设模式、专家模式沿用现状，后续可逐步迁移到同一 `core/vibration` 模型。
- TODO 建议：
  - 为自设/专家模式引入相同的网关接口，复用状态/Intent 结构。
  - 将硬件/会话网关提炼到独立 UseCase 层，便于跨域（如客户/账户、遥测）组合。
  - 结合离线/在线模式与遥测记录，扩展统一的运行状态与日志上报流程。

## 分支与 PR
- 试点改动已汇总到分支 `feature/phase2-android-vibration-mvvm`，便于与 `main` 对比评审。
