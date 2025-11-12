package com.example.sonicwavev4.data.custompreset.model

data class CreateCustomPresetRequest(
    val name: String,
    val steps: List<CustomPresetStep>
)

data class UpdateCustomPresetRequest(
    val id: String,
    val name: String,
    val steps: List<CustomPresetStep>
)

data class ImportResult(
    val imported: Int,
    val skipped: Int
)
