package com.example.sonicwavev4.ui.register

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        val calendar = Calendar.getInstance()
        registerViewModel.birthday.value?.let { savedBirthday ->
            val parts = savedBirthday.split("-")
            if (parts.size == 3) {
                val yearPart = parts[0].toIntOrNull()
                val monthPart = parts[1].toIntOrNull()?.minus(1)
                val dayPart = parts[2].toIntOrNull()
                if (yearPart != null && monthPart != null && dayPart != null) {
                    calendar.set(yearPart, monthPart, dayPart)
                }
            }
        }

        val dialog = DatePickerDialog(
            requireContext(),
            R.style.SpinnerDatePickerDialogTheme,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                binding.tvBirthday.text = formatted
                registerViewModel.onBirthdaySelected(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
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
