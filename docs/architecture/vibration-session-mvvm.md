# 振动会话 MVVM 结构说明（草稿）

> 本文描述 SonicWave Android 端「振动会话」相关代码的 MVVM 结构与约定，  
> 覆盖 Home 模式、预设模式、自设模式三个入口，方便后续维护与扩展。

---

## 1. 设计目标

- **统一的会话模型**：三种入口（Home / 预设 / 自设）共享一套会话状态与意图契约。
- **视图无业务**：Fragment 只负责展示与收集用户操作，不直接操作硬件或网络。
- **可测试性**：振动会话逻辑集中在 ViewModel / Repository，便于做单元测试与回归。
- **易于扩展**：未来新增会话入口时，复用同一套 `UiState + Intent` 即可。

> TODO：在这里可以补一两句业务背景，例如为什么要做 Phase 2 / 这次重构的动机。

---

## 2. 总体结构概览

振动会话相关代码主要分布在：

- 核心契约
  - `apps/android/src/main/java/com/example/sonicwavev4/core/vibration/VibrationSessionContracts.kt`
- 会话入口（View + ViewModel）
  - Home 模式  
    - `ui/home/HomeFragment.kt`  
    - `ui/home/HomeViewModel.kt`
  - 预设模式  
    - `ui/persetmode/PersetmodeFragment.kt`  
    - `ui/persetmode/PersetmodeViewModel.kt`（或等价类）
  - 自设模式  
    - `ui/custompreset/CustomPresetFragment.kt`  
    - `ui/custompreset/CustomPresetViewModel.kt`（或等价类）
- 硬件与数据访问
  - `data/home/HomeHardwareRepository.kt`  
  - 其他与振动控制相关的 Repository / Gateway（如：[补充实际类名]）

数据流向（单向）：

> 用户操作 → Fragment 发 Intent → ViewModel 处理业务逻辑 / 调用 Repository →  
> 产出 `VibrationSessionUiState` → Fragment 订阅状态并渲染 UI。

> TODO：如有需要，可以加一张简易 ASCII 图或链接到现有的 domain diagram。

---

## 3. 核心契约：VibrationSessionContracts

文件位置：

- `core/vibration/VibrationSessionContracts.kt`

主要职责：

- 定义「振动会话」的统一状态模型 `VibrationSessionUiState`：
  - 会话是否运行中
  - 当前频率 / 强度 / 时长
  - 当前模式（Home / 预设 / 自设）下需要关心的标志位
  - 校验 / 错误信息（如有）
- 定义「会话意图」集合（例如 `VibrationSessionIntent`）：
  - 开始会话
  - 停止会话
  - 更新频率 / 强度 / 时长
  - 其他与会话生命周期直接相关的操作

设计约定：

- 该文件视为振动会话的 **单一事实源（Source of Truth）**：
  - 各会话入口不得各自发明新的状态模型；
  - 需要扩展时，应通过 **向后兼容** 的方式修改此契约。

> TODO：根据实际代码，补充具体的 `UiState` 字段、Intent 列表的简介。

---

## 4. 三种入口的 MVVM 结构

### 4.1 Home 模式

- `HomeViewModel`：
  - 持有 `StateFlow<VibrationSessionUiState>`（或等价可观察类型）；
  - 提供方法接收来自 Fragment 的用户操作（封装为会话 Intent）；  
  - 调用 `HomeHardwareRepository` 完成硬件控制与会话状态更新。
- `HomeFragment`：
  - 订阅 `VibrationSessionUiState`，根据状态更新 UI；
  - 将按钮点击、参数滑动等事件转换为 ViewModel 调用。

> TODO：在这里列出 Home 模式中一两个典型交互路径，例如「点击开始 → ViewModel → Repository → UiState 变化」。

### 4.2 预设模式（Preset Mode）

- `PersetmodeViewModel`（名称以实际代码为准）：
  - 同样基于 `VibrationSessionUiState` 表达会话状态；
  - 处理预设选择逻辑，将选中的预设参数注入会话状态；
  - 通过统一的会话 Intent 触发开始 / 停止。
- `PersetmodeFragment`：
  - 不直接控制硬件；
  - 通过 ViewModel 完成预设选择和会话控制。

> TODO：可补充「预设切换时如何更新 UiState」的简要说明。

### 4.3 自设模式（Custom Preset）

- `CustomPresetViewModel`（名称以实际代码为准）：
  - 负责：
    - 自设模式的增删改查；
    - 将自设参数映射到 `VibrationSessionUiState`；
    - 通过会话 Intent 统一控制振动。
- `CustomPresetFragment`：
  - 负责输入/编辑自设参数，并通过 ViewModel 保存；
  - 通过 ViewModel 触发会话开始 / 停止。

> TODO：如有 Room / 本地存储，简要说明和会话状态衔接方式。

---

## 5. 生命周期与资源管理

振动会话 MVVM 结构在生命周期上的约定（示例，可按实际代码调整）：

- Fragment 层：
  - 使用 `viewLifecycleOwner` 订阅 `UiState`，在 `onDestroyView` 解除引用；
- ViewModel 层：
  - 使用 `viewModelScope` 管理协程；
  - 在 `onCleared` 时停止会话 / 释放资源（如有）；
- Repository / 硬件层：
  - 对硬件控制类（如 CH341 / AD9833 / MCP41010）的生命周期由上层明确控制；
  - 会话结束时必须有对应的停止调用，避免长时间占用资源。

> TODO：结合实际实现补充 1–2 条具体约束，例如「哪些方法必须成对调用」。

---

## 6. 新增会话入口时的参考步骤

若未来新增一个新的振动会话入口（例如「专家模式 v2」），建议步骤：

1. 在 UI 层创建对应 Fragment / ViewModel；
2. 在 ViewModel 中：
   - 引入或复用 `VibrationSessionUiState` 作为状态输出；
   - 使用现有会话 Intent（必要时对 contracts 做向后兼容扩展）；
   - 通过统一的 Repository / Gateway 驱动硬件；
3. 在 Fragment 中：
   - 订阅 `UiState` 并渲染 UI；
   - 将所有用户操作转为 ViewModel 方法调用，而不是直接操作硬件；
4. 为新入口补一段简单的「交互路径说明」与「基本测试步骤」，记录在本文或相邻文档中。

---

> 本文为结构性说明，细节以 `VibrationSessionContracts.kt` 及各 ViewModel 的实现为准。  
> 当会话模型发生向后兼容扩展时，请同步更新本文件。
