# Android 按钮自定义颜色指引（通用给 Codex / Gemini）

目标：按钮使用自定义颜色，不受主题默认 tint 影响，便于再次修改。

## 通用步骤
1. **准备资源**  
   - 颜色：在 `res/values/colors.xml` 定义，例如 `#2196F3`、`#FFD600`、`#F44336`。  
   - 背景：在 `res/drawable` 创建 `shape` 文件（圆角 + `solid` 颜色），如 `bg_button_blue/yellow/red.xml`。

2. **禁用主题 tint 干扰**  
   - 主题（`res/values/themes.xml`）：  
     ```xml
     <item name="android:buttonStyle">@style/ButtonNoTint</item>
     <item name="colorButtonNormal">@android:color/transparent</item>
     <item name="colorControlHighlight">@android:color/transparent</item>
     <item name="colorControlActivated">@android:color/transparent</item>
     <item name="colorControlNormal">@android:color/transparent</item>
     ```  
   - 样式（`res/values/styles.xml`）：  
     ```xml
     <style name="ButtonNoTint" parent="@android:style/Widget.Button">
         <item name="android:backgroundTint">@null</item>
     </style>
     ```

3. **在布局中应用背景**  
   - 直接设置背景为自定义 drawable，必要时加 `android:backgroundTintMode="src_atop"`（或不加）：  
     ```xml
     <Button
         android:background="@drawable/bg_button_blue"
         android:backgroundTint="@null"
         style="@style/ButtonNoTint"/>
     ```
   - 避免使用继承 tint 的 Material 样式（或确保 `backgroundTint` 设为 `@null`）。

4. **状态切换**  
   - 若颜色随状态变化：用 selector drawable；若仅文案/启用状态变化，在 Kotlin 中切换 `text/visibility`，不要在代码里反复设置 tint。

5. **验证与排查**  
   - 若仍显示主题色，检查是否存在重复 id/重复定义、父样式继承 Material tint、或 Activity/Fragment 使用的 overlay 覆盖了颜色。  
   - 可以在视图创建后（`onViewCreated`）调用 `button.backgroundTintList = null` 作为兜底清除。

## 适用范围
- 适用于后续新增的按钮，只需：定义颜色/背景 → 确认主题禁 tint → 布局引用背景/样式。
- 平板/手机多布局时，确保同一 id 在各布局都引用相同样式或背景，避免重复定义。

## 快速修改示例
- 将某按钮改为绿色：新增 `bg_button_green.xml`，在布局中 `android:background="@drawable/bg_button_green"`，保持 `style="@style/ButtonNoTint"`，无需改 Kotlin。
