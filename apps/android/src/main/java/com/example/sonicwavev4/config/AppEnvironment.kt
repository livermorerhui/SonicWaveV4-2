package com.example.sonicwavev4.config

import com.example.sonicwavev4.BuildConfig

/**
 * Simple environment descriptor to explain backend switching strategy.
 */
object AppEnvironment {
    val baseUrl: String = BuildConfig.BACKEND_BASE_URL
    val environment: String = BuildConfig.ENVIRONMENT

    /*
    环境用途说明（对应 BuildConfig.ENVIRONMENT）：

    - dev：
      本地 / 局域网开发环境，例如 emulator 上的 http://10.0.2.2:3000。
      主要用于在开发机上调试，验证码可以由后端本地服务固定生成并校验。

    - test：
      阿里云轻量应用服务器测试环境，例如 http://47.107.66.156:3000。
      当前 debug 构建默认指向该环境，用于联调注册 / 登录等新接口。

    - prod：
      生产环境，使用正式域名（例如 https://api.myserver.com）。
      当前 release 构建的 BACKEND_BASE_URL 暂时也指向阿里云测试环境，
      后续切换到正式域名时，只需要在 apps/android/build.gradle.kts 中调整
      release.buildTypes 的 BACKEND_BASE_URL 即可。

    Android 端只关心 Base URL 与环境标签，验证码的发送 / 校验逻辑全部由服务端控制。
    */
}
