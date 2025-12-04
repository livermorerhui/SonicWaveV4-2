package com.example.sonicwavev4.config

import com.example.sonicwavev4.BuildConfig

/**
 * Simple environment descriptor to explain backend switching strategy.
 */
object AppEnvironment {
    val baseUrl: String = BuildConfig.BACKEND_BASE_URL
    val environment: String = BuildConfig.ENVIRONMENT

    /*
    环境用途说明：
    - local/dev（例如 emulator 上的 10.0.2.2:8080）用于与本地/测试服务端对接，验证码可由后端固定 6 位码返回。
    - prod 使用正式域名，验证码由 Humeds 下发。
    Android 端只关心 Base URL 与环境标签，真正的验证码发送/校验逻辑交给服务端。
    */
}
