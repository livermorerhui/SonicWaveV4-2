package com.example.sonicwavev4.network

/**
 * Represents a backend business error response that should be surfaced to the user.
 */
data class ApiError(
    val code: Int? = null,
    val msg: String? = null,
    val data: Any? = null
)
