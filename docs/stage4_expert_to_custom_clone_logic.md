## Stage 4：专家模式 → 自设模式复制逻辑（开发任务书）

目标：完善 Stage 3 的【编辑】按钮，点击时将当前专家模式复制为自设模式并进入真实编辑。支持复用已有副本，避免重复创建。在线/离线行为一致。

---

### Step 0：梳理现有类型与入口（只读）
- 专家模式：`PresetMode`（id/displayName/steps）、`Step`。
- 自设模式：`CustomPreset` / `CustomPresetStep`（Room 实体/DAO），`CustomPresetRepository` / `CustomPresetRepositoryImpl` 的创建/更新。
- 自设模式编辑入口：查看“新建/编辑自设模式”如何调用 `CustomPresetEditorFragment`。

### Step 1：ViewModel 实现“专家 → 自设”复制
- 在 `PersetmodeViewModel` 中新增公开函数（示例）：
  ```kotlin
  /**
   * 将当前选中的专家模式复制为自设模式并保存，返回新建或复用的 presetId。
   * @return Long? 若当前不是专家模式则返回 null。
   */
  suspend fun cloneCurrentExpertToCustom(): Long?
  ```
- 逻辑要点：
  1) 若当前类别不是专家模式，直接返回 null。  
  2) 获取当前选中的 `PresetMode`，无则返回 null。  
  3) 查找是否已有对应的自设模式（避免重复创建），可用名称规则如 `"${mode.displayName} - 自设"`。找到则复用并返回。  
  4) 若不存在则创建：将专家模式 steps 映射为 `CustomPresetStep`，调用仓库创建，得到 `presetId`。  
  5) 更新 UI state：切换到自设模式类别并选中该 `presetId`。  
  6) 返回最终 `presetId`。
- 注意：不得修改 DB schema；只用现有实体/仓库。

### Step 2：专家模式 Fragment 使用复制函数并打开编辑器
- 在 `PersetmodeFragment` 中替换 Stage 3 占位点击逻辑：点击【编辑】调用 `cloneCurrentExpertToCustom()`；若返回 `presetId`，打开 `CustomPresetEditorFragment` 进行编辑。
- 复用自设模式列表里的“打开编辑器”方法（例如 `openCustomPresetEditorFor(presetId)`），保证入口一致。

### Step 3：处理重复点击（去重/复用）
- 在 `cloneCurrentExpertToCustom()` 内实现“查找已有”逻辑，优先复用已有自设模式（例如按名称规则查询）。避免同一专家模式反复点击产生大量副本。
- 可记录日志便于调试。

### Step 4：验证流程
- 场景：进入客户详情（`selectedCustomer != null`）→ 专家模式 → 显示【编辑】。  
  - 第一次点击：创建并保存自设模式，切换 UI 到该自设模式，打开编辑器显示复制内容。  
  - 之后点击同一专家模式：复用已有自设模式，打开编辑器显示同一条（含已修改内容）。  
- 自设模式菜单在 Stage 2 已根据 `selectedCustomer` 控制；点击编辑后切换到自设模式菜单应能看到该条记录。

---

### Constraints
- 不改 DB schema；不改专家模式定义结构。
- 不改 Stage1/2/3 的客户、菜单显隐和按钮显隐规则。
- 复制逻辑封装在 ViewModel；UI 只负责调用/获取 `presetId`/打开编辑器。
- 显示规则仍只依赖 `selectedCustomer` 和当前是否专家模式，与 AppMode 无关。
- 重复点击同一专家模式应优先复用已有自设模式，避免无限副本。
