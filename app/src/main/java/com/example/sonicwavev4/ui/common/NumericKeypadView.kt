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

    var onValueConfirmed: ((Int) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var minValue: Int = 0
    private var maxValue: Int = Int.MAX_VALUE
    private var currentValue: Int = 0

    private val display: TextView
    private val label: TextView

    init {
        inflate(context, R.layout.view_numeric_keypad, this)
        display = findViewById(R.id.tv_display)
        label = findViewById(R.id.tv_label)
        setupButtons()
    }

    fun bindConfig(labelText: String, initialValue: Int, minValue: Int, maxValue: Int) {
        this.label.text = labelText
        this.minValue = minValue
        this.maxValue = maxValue
        currentValue = initialValue.coerceIn(minValue, maxValue)
        display.text = currentValue.toString()
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
        val newValue = (display.text.toString() + digit).toIntOrNull() ?: return
        currentValue = newValue.coerceIn(minValue, maxValue)
        display.text = currentValue.toString()
    }

    private fun clearLastDigit() {
        val currentText = display.text.toString()
        val newText = if (currentText.length > 1) currentText.dropLast(1) else "0"
        currentValue = newText.toIntOrNull()?.coerceIn(minValue, maxValue) ?: minValue
        display.text = currentValue.toString()
    }
}
