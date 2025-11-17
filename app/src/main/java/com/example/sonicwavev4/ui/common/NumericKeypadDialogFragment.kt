package com.example.sonicwavev4.ui.common

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.sonicwavev4.databinding.DialogNumericKeypadBinding

/**
 * 简单可拖动的数字键盘，用于自设模式步骤行。
 * 通过 FragmentResult 返回最终值。
 */
class NumericKeypadDialogFragment : DialogFragment() {

    private var _binding: DialogNumericKeypadBinding? = null
    private val binding get() = _binding!!

    private var minValue: Int = 0
    private var maxValue: Int = Int.MAX_VALUE
    private var currentValue: Int = 0
    private var requestKey: String = RESULT_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentValue = it.getInt(ARG_INITIAL)
            minValue = it.getInt(ARG_MIN)
            maxValue = it.getInt(ARG_MAX)
            requestKey = it.getString(ARG_REQUEST_KEY, RESULT_KEY)
        }
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNumericKeypadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDisplay.text = currentValue.toString()
        setupButtons()
        makeDraggable(binding.root)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
        }
    }

    private fun setupButtons() {
        val digitButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )
        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { appendDigit(digit) }
        }

        binding.btnKeyClear.setOnClickListener { clearLastDigit() }
        binding.btnKeyEnter.setOnClickListener { confirmResult() }
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun appendDigit(digit: String) {
        val newValue = (binding.tvDisplay.text.toString() + digit).toIntOrNull() ?: return
        currentValue = newValue.coerceIn(minValue, maxValue)
        binding.tvDisplay.text = currentValue.toString()
    }

    private fun clearLastDigit() {
        val currentText = binding.tvDisplay.text.toString()
        val newText = if (currentText.length > 1) currentText.dropLast(1) else "0"
        currentValue = newText.toIntOrNull() ?: 0
        currentValue = currentValue.coerceIn(minValue, maxValue)
        binding.tvDisplay.text = currentValue.toString()
    }

    private fun confirmResult() {
        setFragmentResult(
            requestKey,
            Bundle().apply { putInt(RESULT_VALUE, currentValue) }
        )
        dismiss()
    }

    private fun makeDraggable(target: View) {
        var lastX = 0f
        var lastY = 0f
        target.setOnTouchListener { _, event ->
            val window = dialog?.window ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    val params = window.attributes
                    params.x += dx.toInt()
                    params.y += dy.toInt()
                    window.attributes = params
                    lastX = event.rawX
                    lastY = event.rawY
                }
            }
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL = "arg_initial"
        private const val ARG_MIN = "arg_min"
        private const val ARG_MAX = "arg_max"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        const val RESULT_KEY = "numeric_keypad_result"
        const val RESULT_VALUE = "numeric_keypad_value"

        fun newInstance(
            initialValue: Int,
            minValue: Int,
            maxValue: Int,
            requestKey: String = RESULT_KEY
        ): NumericKeypadDialogFragment {
            return NumericKeypadDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INITIAL, initialValue)
                    putInt(ARG_MIN, minValue)
                    putInt(ARG_MAX, maxValue)
                    putString(ARG_REQUEST_KEY, requestKey)
                }
            }
        }
    }
}
