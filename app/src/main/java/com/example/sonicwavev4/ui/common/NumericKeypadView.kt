package com.example.sonicwavev4.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.example.sonicwavev4.R

class NumericKeypadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class Config(
        val labelText: String,
        val initialValue: Int,
        val minValue: Int = 0,
        val maxValue: Int = Int.MAX_VALUE,
        val replaceOnFirstKey: Boolean = true
    )

    var onValueConfirmed: ((Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var minValue: Int = 0
    private var maxValue: Int = Int.MAX_VALUE
    private var currentValue: Int = 0
    private var replaceOnFirstKey: Boolean = true
    private var firstKeyPending: Boolean = true

    private val display: TextView
    private val label: TextView

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        inflate(context, R.layout.view_numeric_keypad, this)
        display = findViewById(R.id.tv_display)
        label = findViewById(R.id.tv_label)
        setupButtons()
    }

    fun bindConfig(config: Config) {
        label.text = config.labelText
        minValue = config.minValue
        maxValue = config.maxValue
        replaceOnFirstKey = config.replaceOnFirstKey
        currentValue = config.initialValue.coerceIn(minValue, maxValue)
        display.text = currentValue.toString()
        firstKeyPending = replaceOnFirstKey
        requestFocus()
        requestFocusFromTouch()
    }

    /**
     * Prepare for immediate editing after a field is selected.
     * 保持显示当前值，但首击可覆盖。
     */
    fun focusForEditing() {
        firstKeyPending = replaceOnFirstKey
        requestFocus()
        requestFocusFromTouch()
    }

    private fun setupButtons() {
        val digitButtons = listOf(
            findViewById<Button>(R.id.btn_key_0) to "0",
            findViewById<Button>(R.id.btn_key_1) to "1",
            findViewById<Button>(R.id.btn_key_2) to "2",
            findViewById<Button>(R.id.btn_key_3) to "3",
            findViewById<Button>(R.id.btn_key_4) to "4",
            findViewById<Button>(R.id.btn_key_5) to "5",
            findViewById<Button>(R.id.btn_key_6) to "6",
            findViewById<Button>(R.id.btn_key_7) to "7",
            findViewById<Button>(R.id.btn_key_8) to "8",
            findViewById<Button>(R.id.btn_key_9) to "9"
        )
        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { appendDigit(digit) }
        }
        findViewById<Button>(R.id.btn_key_clear).setOnClickListener { clearLastDigit() }
        findViewById<ImageButton>(R.id.btn_key_enter).setOnClickListener { onValueConfirmed?.invoke(currentValue) }
    }

    private fun appendDigit(digit: String) {
        val baseText = if (replaceOnFirstKey && firstKeyPending) "" else display.text.toString()
        val newValue = (baseText + digit).toIntOrNull() ?: return
        currentValue = newValue.coerceIn(minValue, maxValue)
        display.text = currentValue.toString()
        firstKeyPending = false
    }

    private fun clearLastDigit() {
        val currentText = display.text.toString()
        val newText = if (currentText.length > 1) currentText.dropLast(1) else minValue.toString()
        currentValue = newText.toIntOrNull()?.coerceIn(minValue, maxValue) ?: minValue
        display.text = currentValue.toString()
        // 清除后，再次输入仍按首击覆盖/追加规则
    }
}
