package com.example.sonicwavev4.ui.register

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.NumberPicker
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentRegisterBinding
import com.example.sonicwavev4.ui.login.LoginFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class RegisterFragment : Fragment() {

    private val registerViewModel: RegisterViewModel by viewModels()
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var hasShownHumedsBindDialog = false
    private var hasNavigatedBack = false
    private var humedsBindDialog: AlertDialog? = null

    private fun hostContainerId(): Int {
        return if (requireActivity().findViewById<View?>(R.id.fragment_right_main) != null) {
            R.id.fragment_right_main
        } else {
            R.id.login_container_host
        }
    }

    private fun hostFragmentManager(): FragmentManager {
        return if (requireActivity().findViewById<View?>(R.id.fragment_right_main) != null) {
            requireActivity().supportFragmentManager
        } else {
            parentFragmentManager
        }
    }
    private var humedsPwdInput: EditText? = null
    private var lastCodeSent: Boolean = false
    private var lastNeedSmsInput: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAccountTypeSwitch()
        setupDatePicker()
        binding.etMobile.doAfterTextChanged {
            registerViewModel.onMobileChanged()
        }
        setupSendCode()
        setupRegister()
        setupLoginLink()
        collectUiState()
    }

    private fun setupAccountTypeSwitch() {
        binding.rgAccountType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_personal -> {
                    binding.layoutPersonal.visibility = View.VISIBLE
                    binding.layoutOrg.visibility = View.GONE
                }
                R.id.rb_org -> {
                    binding.layoutPersonal.visibility = View.GONE
                    binding.layoutOrg.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDatePicker() {
        binding.tvBirthday.setOnClickListener {
            showBirthdayPicker()
        }

        registerViewModel.birthday.value?.let { savedBirthday ->
            binding.tvBirthday.text = savedBirthday
        }
    }

    private fun showBirthdayPicker() {
        val dialog = Dialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_birthdate_picker, null)
        dialog.setContentView(view)

        val calendar = Calendar.getInstance()
        val yearPicker = view.findViewById<NumberPicker>(R.id.np_year)
        val monthPicker = view.findViewById<NumberPicker>(R.id.np_month)
        val dayPicker = view.findViewById<NumberPicker>(R.id.np_day)

        val currentYear = calendar.get(Calendar.YEAR)
        val minYear = 1900
        yearPicker.minValue = minYear
        yearPicker.maxValue = currentYear

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.displayedValues = (1..12).map { String.format(Locale.US, "%02d月", it) }.toTypedArray()

        fun updateDays(year: Int, month: Int) {
            val daysInMonth = Calendar.getInstance().apply {
                set(year, month - 1, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
            val currentDay = dayPicker.value.coerceAtMost(daysInMonth)
            // Reset displayedValues before changing min/max to avoid IllegalArgumentException
            dayPicker.displayedValues = null
            dayPicker.minValue = 1
            dayPicker.maxValue = daysInMonth
            dayPicker.displayedValues = (1..daysInMonth).map { String.format(Locale.US, "%02d日", it) }.toTypedArray()
            dayPicker.value = currentDay
        }

        val saved = registerViewModel.birthday.value
        if (saved != null && saved.split("-").size == 3) {
            val parts = saved.split("-")
            val year = parts[0].toIntOrNull() ?: currentYear
            val month = parts[1].toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)
            val day = parts[2].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
            yearPicker.value = year.coerceIn(minYear, currentYear)
            monthPicker.value = month.coerceIn(1, 12)
            updateDays(yearPicker.value, monthPicker.value)
            dayPicker.value = day.coerceIn(dayPicker.minValue, dayPicker.maxValue)
        } else {
            yearPicker.value = currentYear
            monthPicker.value = calendar.get(Calendar.MONTH) + 1
            updateDays(yearPicker.value, monthPicker.value)
            dayPicker.value = calendar.get(Calendar.DAY_OF_MONTH)
        }

        yearPicker.setOnValueChangedListener { _, _, newVal ->
            updateDays(newVal, monthPicker.value)
        }
        monthPicker.setOnValueChangedListener { _, _, newVal ->
            updateDays(yearPicker.value, newVal)
        }

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            val selectedYear = yearPicker.value
            val selectedMonth = monthPicker.value
            val selectedDay = dayPicker.value
            val formatted = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay)
            binding.tvBirthday.text = formatted
            registerViewModel.onBirthdaySelected(formatted)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), android.R.color.white))
        dialog.show()
        dialog.window?.let { window ->
            val width = resources.getDimensionPixelSize(R.dimen.date_picker_dialog_width)
            val height = resources.getDimensionPixelSize(R.dimen.date_picker_dialog_height)
            window.setLayout(width, height)

            // 将弹窗显示在日期控件上方（或顶部附近），而不是全屏居中
            val location = IntArray(2)
            binding.tvBirthday.getLocationOnScreen(location)
            val anchorY = location[1]
            val params = window.attributes
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // 尝试将弹窗顶端放在控件上方，若超出屏幕则贴顶
            params.y = (anchorY - height).coerceAtLeast(0)
            window.attributes = params
        }
    }

    private fun setupSendCode() {
        binding.btnSendCode.setOnClickListener {
            val mobile = binding.etMobile.text.toString()
            val accountType = if (binding.rbPersonal.isChecked) "personal" else "org"
            registerViewModel.sendCode(mobile = mobile, accountType = accountType)
        }
    }

    private fun setupRegister() {
        binding.btnRegister.setOnClickListener {
            val mobile = binding.etMobile.text.toString()
            val code = binding.etCode.text.toString()
            val password = binding.etPassword.text.toString()
            val accountType = if (binding.rbPersonal.isChecked) "personal" else "org"
            val birthday = if (accountType == "personal") registerViewModel.birthday.value else null
            val orgName = if (accountType == "org") binding.etOrgName.text.toString() else null

            registerViewModel.register(
                mobile = mobile,
                code = code,
                password = password,
                accountType = accountType,
                orgName = orgName
            )
        }
    }

    private fun setupLoginLink() {
        binding.loginLinkTextView.setOnClickListener {
            navigateBackToLogin()
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                registerViewModel.uiState.collectLatest { state ->
                    val sec = state.sendCodeCooldownSeconds
                    val needSms = state.needSmsInput
                    binding.layoutCodeContainer.visibility = View.VISIBLE
                    if (!needSms && state.codeSent) {
                        binding.btnSendCode.isEnabled = false
                        binding.btnSendCode.text = "已验证"
                    } else {
                        binding.btnSendCode.isEnabled = !state.isLoading && sec == 0
                        binding.btnSendCode.text = if (sec > 0) "重新发送(${sec}s)" else "获取验证码"
                    }
                    binding.btnRegister.isEnabled = !state.isLoading
                    val hint = state.flowHint?.trim().orEmpty()
                    if (hint.isNotEmpty()) {
                        binding.tvRegisterFlowHint.visibility = View.VISIBLE
                        binding.tvRegisterFlowHint.text = hint
                    } else {
                        binding.tvRegisterFlowHint.visibility = View.GONE
                    }

                    if (!needSms && state.codeSent) {
                        binding.etCode.isEnabled = false
                        binding.etCode.isFocusable = false
                        binding.etCode.isFocusableInTouchMode = false
                        binding.etCode.isCursorVisible = false
                        binding.etCode.hint = "无需验证码"
                        binding.etCode.setText("")
                        val d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle_green)
                        binding.etCode.setCompoundDrawablesWithIntrinsicBounds(null, null, d, null)
                        binding.etCode.compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
                        binding.etCode.setBackgroundResource(R.drawable.bg_edit_text_disabled)
                    } else {
                        binding.etCode.isEnabled = true
                        binding.etCode.isFocusable = true
                        binding.etCode.isFocusableInTouchMode = true
                        binding.etCode.isCursorVisible = true
                        binding.etCode.hint = "请输入验证码"
                        binding.etCode.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                        binding.etCode.setBackgroundResource(android.R.drawable.edit_text)
                    }

                    val justSent = state.codeSent && !lastCodeSent
                    if (justSent) {
                        if (needSms) {
                            focusCodeInput()
                        } else {
                            focusPasswordInput()
                        }
                    }
                    lastCodeSent = state.codeSent
                    lastNeedSmsInput = state.needSmsInput

                    state.statusMessage?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }

                    state.errorMessage?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }

                    if (state.statusMessage != null || state.errorMessage != null) {
                        registerViewModel.clearMessages()
                    }

                    humedsBindDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.isLoading

                    if (state.humedsBindStatus == "success") {
                        humedsBindDialog?.dismiss()
                        navigateBackToLoginOnce()
                    } else if (state.success) {
                        showHumedsBindDialogIfNeeded(state)
                    }
                }
            }
        }
    }

    private fun navigateBackToLogin() {
        hostFragmentManager().beginTransaction()
            .replace(hostContainerId(), LoginFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showHumedsBindDialogIfNeeded(state: RegisterViewModel.RegisterUiState) {
        if (!state.success) return
        if (hasShownHumedsBindDialog || hasNavigatedBack) return
        if (state.humedsBindStatus == "success") return

        hasShownHumedsBindDialog = true
        val messageText = buildString {
            if (state.humedsBindStatus == "failed" && !state.humedsErrorMessage.isNullOrBlank()) {
                append("上次绑定失败原因：")
                append(state.humedsErrorMessage)
                append("\n")
            }
            append("可输入 Humeds 密码进行绑定（不影响本应用登录）")
        }

        val padding = (20 * resources.displayMetrics.density).toInt()
        val marginTop = (12 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        val tvMsg = TextView(requireContext()).apply {
            text = messageText
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        val tvLabel = TextView(requireContext()).apply {
            text = "Humeds 密码"
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply { topMargin = marginTop }
        }

        val input = EditText(requireContext()).apply {
            hint = "请输入 Humeds 密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
            setPadding(marginTop, marginTop, marginTop, marginTop)
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            setBackgroundResource(android.R.drawable.edit_text)
            isCursorVisible = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.edittext_cursor)
            } else {
                try {
                    val field = TextView::class.java.getDeclaredField("mCursorDrawableRes")
                    field.isAccessible = true
                    field.set(this, R.drawable.edittext_cursor)
                } catch (_: Exception) {
                    // ignore if reflection fails; fallback to default cursor
                }
            }
        }

        container.addView(tvMsg)
        container.addView(tvLabel)
        container.addView(input)

        humedsPwdInput = input

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("绑定 Humeds")
            .setView(container)
            .setNegativeButton("跳过") { d, _ ->
                d.dismiss()
                navigateBackToLoginOnce()
            }
            .setPositiveButton("绑定", null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val pwd = input.text?.toString()?.trim().orEmpty()
                if (pwd.isBlank()) {
                    input.error = "请输入 Humeds 密码"
                    return@setOnClickListener
                }
                registerViewModel.repairHumedsBindingByPassword(pwd)
            }
        }

        dialog.setOnDismissListener {
            humedsBindDialog = null
            humedsPwdInput = null
        }

        humedsBindDialog = dialog
        dialog.show()
    }

    private fun navigateBackToLoginOnce() {
        if (hasNavigatedBack) return
        hasNavigatedBack = true
        navigateBackToLogin()
    }

    private fun focusCodeInput() {
        binding.etCode.post {
            binding.etCode.requestFocus()
            val len = binding.etCode.text?.length ?: 0
            binding.etCode.setSelection(len)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etCode, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun focusPasswordInput() {
        binding.etPassword.post {
            binding.etPassword.requestFocus()
            val len = binding.etPassword.text?.length ?: 0
            binding.etPassword.setSelection(len)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etPassword, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
