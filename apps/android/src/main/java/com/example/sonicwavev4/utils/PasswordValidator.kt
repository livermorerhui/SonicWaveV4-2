package com.example.sonicwavev4.utils

object PasswordValidator {

    data class Result(val isValid: Boolean, val message: String?)

    fun validate(password: String): Result {
        val trimmed = password.trim()
        if (trimmed.length < 6) {
            return Result(false, "密码至少 6 位，且需包含数字、字母、符号中至少两种")
        }

        var hasLetter = false
        var hasDigit = false
        var hasSymbol = false

        trimmed.forEach { ch ->
            when {
                ch.isLetter() -> hasLetter = true
                ch.isDigit() -> hasDigit = true
                else -> hasSymbol = true
            }
        }

        val typesCount = listOf(hasLetter, hasDigit, hasSymbol).count { it }
        return if (typesCount >= 2) {
            Result(true, null)
        } else {
            Result(false, "密码至少 6 位，且需包含数字、字母、符号中至少两种")
        }
    }
}
