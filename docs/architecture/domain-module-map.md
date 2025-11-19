# SonicWave V4 – 领域到代码模块映射草稿

本文件是 Phase 0 的辅助“地图”，用于将现有代码与 5 个领域（见 `domain-overview.md` / `domain-usecases.md`）进行对应。它不是强制规范，但可为后续 Phase 1（仓库瘦身）与 Phase 3（Android MVVM 重构）提供定位参考。跨域职责的模块允许在多个领域下重复出现；暂未实现或待确认的模块以“暂无/待确认”标注。

---

## 1. 振动会话域 – 代码映射

| 平台 | 模块/目录 | 典型类/文件 | 关联用例 | 备注 |
| --- | --- | --- | --- | --- |
| Android | `app/src/main/java/com/example/sonicwavev4/ui/persetmode`、`ui/custompreset`、`ui/home`、`ui/dashboard` | `PresetModeActivity`、`CustomPresetActivity`、`HomeFragment`、`DashboardFragment` | 1-1、1-2、1-3 | 会话执行与预设/自设模式管理的主要 UI |
| Android | `app/src/main/java/com/example/sonicwavev4/harddriver` | `Ad9833Controller.java`、`Mcp41010Controller.java` | 1-1、1-2、1-3 | 硬件驱动层，提供频率/强度输出 |
| Android | `app/src/main/java/com/example/sonicwavev4/utils/SessionManager.kt`、`utils/TestToneSettings.kt` | `SessionManager`、`TestToneSettings` | 1-1、1-3 | 会话状态、测试正弦波设置 |
| Android | `app/src/main/java/com/example/sonicwavev4/data/custompreset`、`data/home`、`repository` | 数据实体与仓储 | 1-1、1-2 | 预设/自设模式的存取 | 
| 后端 | `SonicWaveV5-backend/controllers/presetModes.controller.js`、`routes/presetModes.routes.js` | 预设模式接口 | 1-1、1-2 | 会话/模式管理 API |
| 后端 | `SonicWaveV5-backend/controllers/operations.controller.js` | 会话操作/日志（跨域） | 1-1、1-2 | **跨域：** 同时出现在“遥测与审计域” |
| 后端 | `SonicWaveV5-backend/routes/operations.routes.js` | 会话操作路由 | 1-1、1-2 | 与上同 |
| 管理端 Web | 暂无会话/模式管理视图 | 暂无 | 1-1、1-2 | 待确认是否需要新增页面 |

## 2. 客户与账户域 – 代码映射

| 平台 | 模块/目录 | 典型类/文件 | 关联用例 | 备注 |
| --- | --- | --- | --- | --- |
| Android | `app/src/main/java/com/example/sonicwavev4/ui/login`、`ui/register` | 登录/注册页面 | 2-1 | 账号登录、Token 获取 |
| Android | `app/src/main/java/com/example/sonicwavev4/ui/customer`、`ui/user`、`ui/common` | 客户/用户列表与选择 UI | 2-2、2-3 | 支持在线/离线客户管理 |
| Android | `app/src/main/java/com/example/sonicwavev4/ui/OfflineAddCustomerDialogFragment.kt`、`ui/AddCustomerDialogFragment.kt` | 离线/在线新增客户弹窗 | 2-2、2-3 | 覆盖离线场景 |
| Android | `app/src/main/java/com/example/sonicwavev4/core/CustomerSource.kt`、`data/offlinecustomer` | 客户来源定义、本地客户存储 | 2-2、2-3 | 离线客户持久化 |
| Android | `app/src/main/java/com/example/sonicwavev4/network/AuthInterceptor.kt`、`TokenAuthenticator.kt`、`network/ApiService.kt` | 认证拦截与接口 | 2-1、2-2 | 登录/请求鉴权 |
| 后端 | `SonicWaveV5-backend/controllers/auth.controller.js`、`routes/auth.routes.js` | 登录/注册 API | 2-1 | 用户鉴权 |
| 后端 | `SonicWaveV5-backend/controllers/users.controller.js`、`customer.controller.js`、`device.controller.js` | 用户/客户/设备接口 | 2-2、2-3 | 客户主数据与设备绑定 |
| 后端 | `SonicWaveV5-backend/routes/users.routes.js`、`customer.routes.js`、`device.routes.js` | 对应路由 | 2-2、2-3 | — |
| 管理端 Web | `SW-backend-admin/src/views/UsersPage.tsx`、`views/CustomersPage.tsx`、`views/DevicesPage.tsx` | 用户、客户、设备管理页面 | 2-2 | 设备页辅助客户-设备绑定 |
| 管理端 Web | `SW-backend-admin/src/viewmodels/useUsersVM.ts`、`useCustomersVM.ts`、`useDevicesVM.ts` | 对应视图模型 | 2-2 | 数据拉取与状态管理 |

## 3. 遥测与审计域 – 代码映射

| 平台 | 模块/目录 | 典型类/文件 | 关联用例 | 备注 |
| --- | --- | --- | --- | --- |
| Android | `app/src/main/java/com/example/sonicwavev4/logging` | `LogRepository.kt`、`LogUploadWorker.kt` | 3-1、3-3 | 日志收集与上传 |
| Android | `app/src/main/java/com/example/sonicwavev4/utils/HeartbeatManager.kt`、`utils/DeviceHeartbeatManager.kt` | 心跳与设备状态上报 | 3-1、3-2 | 定时心跳任务 |
| Android | `app/src/main/java/com/example/sonicwavev4/network/OperationData.kt`、`AuthEventData.kt` | 操作/事件上报模型 | 3-1、3-3 | — |
| 后端 | `SonicWaveV5-backend/controllers/heartbeat.controller.js`、`routes/heartbeat.routes.js` | 心跳接口 | 3-1、3-2 | — |
| 后端 | `SonicWaveV5-backend/controllers/logs.controller.js`、`routes/logs.routes.js` | 日志接口 | 3-1、3-3 | — |
| 后端 | `SonicWaveV5-backend/controllers/operations.controller.js`、`routes/operations.routes.js` | 操作日志/会话操作（跨域） | 3-3、1-1 | **跨域：** 也列于振动会话域 |
| 后端 | `SonicWaveV5-backend/controllers/reports.controller.js`、`routes/reports.routes.js` | 报表/统计 | 3-3 | 审计报表 |
| 管理端 Web | 暂无遥测/日志视图 | 暂无 | 3-1、3-2、3-3 | 待确认是否需要新增页面 |

## 4. 媒体播放域 – 代码映射

| 平台 | 模块/目录 | 典型类/文件 | 关联用例 | 备注 |
| --- | --- | --- | --- | --- |
| Android | `app/src/main/java/com/example/sonicwavev4/ui/music` | 音乐列表/播放器 UI | 4-1、4-2 | 本地播放、下载入口 |
| Android | 顶层 `MusicDownloader.kt`、`MusicDownloadDialogFragment.kt`、`DownloadedMusicRepository.kt` | 在线音乐下载、持久化、刷新 | 4-2 | 下载并刷新列表（`loadMusic()`） |
| Android | `app/src/main/java/com/example/sonicwavev4/SampleMusicSeeder.kt`、`MusicItem.kt`、`DownloadableMusicAdapter.kt` | 本地/在线曲目模型与适配器 | 4-1、4-2 | — |
| 后端 | `SonicWaveV5-backend/controllers/music.controller.js`、`routes/music.routes.js` | 在线音乐列表/下载接口 | 4-2 | — |
| 管理端 Web | 暂无媒体管理视图 | 暂无 | 4-1、4-2 | 待确认 |

## 5. 基础设施与配置域 – 代码映射

| 平台 | 模块/目录 | 典型类/文件 | 关联用例 | 备注 |
| --- | --- | --- | --- | --- |
| Android | `app/src/main/java/com/example/sonicwavev4/core` | `AppMode.kt` | 5-1 | 运行模式定义 |
| Android | `app/src/main/java/com/example/sonicwavev4/utils/OfflineCapabilityManager.kt`、`OfflineControlMessageHandler.kt`、`OfflineForceExitManager.kt`、`OfflineModeRemoteSync.kt` | 离线模式控制/强退/同步 | 5-1、5-3 | 控制通道处理 |
| Android | `app/src/main/java/com/example/sonicwavev4/network/OfflineControlWebSocket.kt`、`EndpointProvider.kt` | 控制通道连接、服务端地址 | 5-1、5-3 | 10 秒重连、设备 ID 绑定 |
| Android | `app/src/main/java/com/example/sonicwavev4/network/FeatureFlagsResponse.kt`、`utils/GlobalLogoutManager.kt` | 配置/功能开关同步、全局登出 | 5-2 | 功能开关支撑 |
| Android | `app/src/main/java/com/example/sonicwavev4/network/RetrofitClient.kt`、`AuthInterceptor.kt`、`NetworkLoggingInterceptor.kt` | 通用网络配置 | 5-1、5-2 | — |
| 后端 | `SonicWaveV5-backend/realtime/offlineControlChannel.js` | 离线模式控制通道 | 5-3 | WebSocket 控制 |
| 后端 | `SonicWaveV5-backend/services/featureFlags.service.js`、`repositories/featureFlags.repository.js`、`routes/app.routes.js` | 功能开关、配置 | 5-2 | — |
| 后端 | `SonicWaveV5-backend/config`、`middleware`、`utils`、`deploy.sh`、`docker-compose.yml` | 环境配置、日志、部署脚本 | 5-1、5-2 | 通用基础设施 |
| 管理端 Web | `SW-backend-admin/src/views/FeatureFlagsPage.tsx`、`viewmodels/useDevicesVM.ts`（设备状态） | 功能开关与设备列表 | 5-2、5-3 | 设备管理支撑离线控制 |
| 管理端 Web | 路由/权限守卫：`SW-backend-admin/src/routes/index.tsx`、`viewmodels/AuthProvider.tsx`、`useAuthVM.ts` | 权限控制/登录态 | 5-2 | 通用支撑 |
| 管理端 Web | 暂无控制通道/配置更改以外的基础设施页面 | 暂无 | 5-1 | 待确认 |

---

> 说明：本映射基于当前代码结构和 `domain-usecases.md` 的用例编号，是第一版草稿。后续 Phase 1/Phase 3 可根据拆分与重构结果更新表格，跨域模块可继续在多个领域下标注或调整定位。