# Android 注册/登录后端配置检查（2025-03-15）

- `BACKEND_BASE_URL`（debug）：`http://47.107.66.156:3000`
- `BACKEND_BASE_URL`（release）：`http://47.107.66.156:3000`

网络层使用情况：

- `apps/android/src/main/java/com/example/sonicwavev4/network/MyBackendApiService.kt` 的 Retrofit 使用 `BuildConfig.BACKEND_BASE_URL.trimEnd('/')` 生成 `baseUrl`，无硬编码 IP。
- 工程 Kotlin 代码中未发现与注册/登录相关的硬编码 IP/URL（`:3000` 等）；现有字符串仅出现在环境说明注释里。

遗留配置说明：

- `SERVER_BASE_URL_RELEASE` 等仍为 `http://8.148.182.90:3000`（以及局域网/模拟器地址），当前用于旧音频/其他接口，暂未迁移；如后续统一迁移，请先确认对应服务。

总结：

- Android 端注册/登录相关后端配置已全部指向阿里云 `47.107.66.156:3000`，可直接用于联调与验收。
