package com.example.sonicwavev4.ui.register

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.NumberPicker
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
            val accountType = if (binding.rbPersonal.isChecked) "personal" else "org"
            val mobile = binding.etMobile.text.toString()
            registerViewModel.sendCode(mobile, accountType)
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
                    binding.btnSendCode.isEnabled = !state.isLoading
                    binding.btnRegister.isEnabled = !state.isLoading

                    state.statusMessage?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }

                    state.errorMessage?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }

                    if (state.success) {
                        navigateBackToLogin()
                    }
                }
            }
        }
    }

    private fun navigateBackToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
