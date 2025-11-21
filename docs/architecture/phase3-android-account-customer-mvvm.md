# Phase 3 – Android 客户与账户域 MVVM 收敛说明

## 1）背景与目标
- **领域作用**：客户与账户域涵盖账号注册/登录、Token 管理、客户列表及离线客户新增，是振动会话域依赖的身份与服务对象入口。
- **Phase 3 目标**：在 Android 端对登录、注册、客户选择/新增（含离线）流程进行 MVVM 收敛，保持既有业务行为和提示不变，同时为后续遥测/媒体域迁移提供模板。

## 2）当前实现概览（Before）
- **登录/注册（用例 2-1）**：`ui/login`、`ui/register` 中的 Fragment 负责输入校验、调用 `RetrofitClient` 登录/注册接口，直接保存 Token、记录登录事件并启动心跳。离线模式入口（测试账号）也在 Fragment 按钮中触发。
- **客户列表/选择（用例 2-2）**：`ui/customer/CustomerListFragment` 通过 `UserViewModel` 拉取客户列表、过滤搜索、在列表点击时直接更新选中客户并导航到详情。
- **离线客户新增（用例 2-3）**：`OfflineAddCustomerDialogFragment` 直接调用 `OfflineCustomerRepository` 写入本地数据库，结果提示在对话框内部完成。
- **逻辑分散点**：
  - Fragment/Activity 中混有输入校验、网络调用、Session/Heartbeat 控制。
  - 客户在线/离线入口分散在多个对话框，缺少统一的状态流或事件流。

## 3）目标 MVVM 结构（After）
- **分层设计**：
  - **UI 层**：Fragment 仅收集输入、渲染 `UiState`，通过 Intent 触发 ViewModel 方法，导航保持原路径。
  - **ViewModel 层**：
    - `LoginViewModel` 管理登录/注册/离线登录/登出状态，暴露单一 `AuthUiState` 与 `AuthEvent`。
    - `CustomerViewModel` 统一客户列表、搜索、选中、在线/离线新增与更新，暴露 `CustomerListUiState` 与 `CustomerEvent`。
  - **Repository / Gateway 层**：
    - `AuthRepository` 封装登录/注册/登出、Session 与心跳控制；
    - `CustomerRepository`、`OfflineCustomerRepository` 作为数据源，被 `CustomerViewModel` 统一调度。
- **目录示意**：
  - `core/account/`: `AuthIntent`、`AuthUiState`、`AuthEvent`、`AuthResult`、`CustomerListUiState`、`CustomerEvent` 等状态/事件模型与网关接口。
  - `ui/login`: `LoginViewModel`（处理登录、注册、登出、离线登录）及 Login/Register Fragment 绑定。
  - `ui/customer`: `CustomerViewModel`、客户列表/详情/新增弹窗；对话框仅发送意图，状态与结果均从 ViewModel 观察。

## 4）状态与事件模型
- **AuthUiState / AuthIntent / AuthEvent**：
  - `AuthUiState`：`isLoading`、`isLoggedIn`、`isOfflineMode`、`accountType`、`username`、`offlineModeAllowed`、`errorMessage`。
  - `AuthIntent`：`Login`、`Register`、`EnterOfflineMode`、`Logout`、`ClearError`。
  - `AuthEvent`：`ShowToast`、`ShowError`、`NavigateToUser`、`NavigateToLogin`。
- **CustomerListUiState / CustomerEvent**：
  - `CustomerListUiState`：`customers`、`filteredCustomers`、`selectedCustomer`、`searchQuery`、`isLoading`、`errorMessage`、`source (ONLINE/OFFLINE)`。
  - `CustomerEvent`：`CustomerSaved`（成功提示）、`Error`（错误提示）。

## 5）与 Phase 2 的衔接
- 振动会话域（Phase 2）的 `HomeViewModel`、`PersetmodeViewModel` 等通过新的 `AuthUiState` 获取登录/账号类型，通过 `CustomerViewModel` 的 `selectedCustomer` 作为会话上下文，保持原有启动/停止行为。
- 本次仅覆盖用例 2-1/2-2/2-3；遥测/媒体域仍按现有实现工作，后续可复用本次的 UiState/Intent 模板进行迁移。
