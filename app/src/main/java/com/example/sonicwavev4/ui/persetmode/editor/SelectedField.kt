package com.example.sonicwavev4.ui.persetmode.editor

enum class FieldType {
    FREQUENCY,
    INTENSITY,
    DURATION
}

data class SelectedField(
    val stepIndex: Int,
    val fieldType: FieldType
)
