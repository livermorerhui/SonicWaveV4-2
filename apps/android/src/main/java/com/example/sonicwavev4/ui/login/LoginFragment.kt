package com.example.sonicwavev4.ui.login

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.BuildConfig
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.databinding.FragmentLoginBinding
import com.example.sonicwavev4.network.EndpointProvider
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.register.RegisterFragment
import com.example.sonicwavev4.ui.login.ResetPasswordFragment
import com.example.sonicwavev4.ui.user.UserFragment
import com.example.sonicwavev4.utils.OfflineModeRemoteSync
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by activityViewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var isSettingStoredPassword = false
    private var isSettingAccountProgrammatically = false
    private var isSettingRememberProgrammatically = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loginViewModel.ensureCredentialStoreInitialized(requireContext().applicationContext)
        setupLoginForm()
        setupClickListeners()
        collectUiState()
        collectLoginFormState()
        collectEvents()
        setupDebugEnvSwitcher()
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            OfflineModeRemoteSync.sync(requireContext(), force = false)
        }
    }

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

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val mobile = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            loginViewModel.handleIntent(AuthIntent.Login(mobile, password))
        }
        binding.registerTextView.setOnClickListener {
            hostFragmentManager().beginTransaction()
                .replace(hostContainerId(), RegisterFragment())
                .commit()
        }
        binding.forgotPasswordTextView.setOnClickListener {
            hostFragmentManager().beginTransaction()
                .replace(hostContainerId(), ResetPasswordFragment())
                .commit()
        }
        binding.offlineModeButton.setOnClickListener {
            loginViewModel.handleIntent(AuthIntent.EnterOfflineMode)
        }
    }

    private fun setupLoginForm() {
        binding.passwordTextInputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        binding.passwordTextInputLayout.setEndIconOnClickListener {
            loginViewModel.onTogglePasswordVisibility()
        }

        binding.rememberPasswordCheckBox.setOnCheckedChangeListener { _, checked ->
            if (isSettingRememberProgrammatically) return@setOnCheckedChangeListener
            loginViewModel.onRememberCheckedChanged(checked)
        }

        binding.usernameEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isSettingAccountProgrammatically) return
                loginViewModel.onAccountChanged(s?.toString().orEmpty())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isSettingStoredPassword) return
                val text = s?.toString().orEmpty()
                val currentFormState = loginViewModel.loginFormState.value

                if (currentFormState.passwordSource == PasswordSource.STORED) {
                    if (text != MASK_TOKEN) {
                        loginViewModel.onPasswordChanged("")
                        Toast.makeText(
                            requireContext(),
                            "已清除保存的密码，请重新输入",
                            Toast.LENGTH_SHORT
                        ).show()
                        isSettingStoredPassword = true
                        binding.passwordEditText.setText("")
                        isSettingStoredPassword = false
                    }
                    return
                }

                loginViewModel.onPasswordChanged(text)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.uiState.collectLatest { state ->
                    binding.loginButton.isEnabled = !state.isLoading
                    binding.offlineModeButton.isVisible = state.offlineModeAllowed
                    binding.offlineModeButton.isEnabled = state.offlineModeAllowed && !state.isLoading
                }
            }
        }
    }

    private fun collectLoginFormState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.loginFormState.collectLatest { state ->
                    binding.rememberPasswordCheckBox.isVisible = state.rememberSupported
                    if (state.rememberSupported) {
                        if (binding.rememberPasswordCheckBox.isChecked != state.isRememberChecked) {
                            isSettingRememberProgrammatically = true
                            binding.rememberPasswordCheckBox.isChecked = state.isRememberChecked
                            isSettingRememberProgrammatically = false
                        }
                    }

                    val desiredAccount = state.accountText
                    if (binding.usernameEditText.text?.toString() != desiredAccount) {
                        isSettingAccountProgrammatically = true
                        binding.usernameEditText.setText(desiredAccount)
                        binding.usernameEditText.setSelection(desiredAccount.length)
                        isSettingAccountProgrammatically = false
                    }

                    if (state.passwordSource == PasswordSource.STORED) {
                        if (binding.passwordEditText.text?.toString() != MASK_TOKEN) {
                            isSettingStoredPassword = true
                            binding.passwordEditText.setText(MASK_TOKEN)
                            binding.passwordEditText.setSelection(MASK_TOKEN.length)
                            isSettingStoredPassword = false
                        }
                    }

                    val shouldMask = (state.passwordSource == PasswordSource.STORED) || !state.isPasswordVisible
                    binding.passwordEditText.transformationMethod =
                        if (shouldMask) PasswordTransformationMethod.getInstance() else null
                    binding.passwordEditText.isCursorVisible = true

                    if (state.passwordSource == PasswordSource.STORED && !state.isStoredAccountMatched) {
                        binding.passwordTextInputLayout.error = "账号已变更，请重新输入密码"
                    } else {
                        binding.passwordTextInputLayout.error = null
                    }

                    val iconRes = when {
                        state.passwordSource == PasswordSource.STORED -> com.google.android.material.R.drawable.design_ic_visibility_off
                        state.isPasswordVisible -> com.google.android.material.R.drawable.design_password_eye
                        else -> com.google.android.material.R.drawable.design_ic_visibility_off
                    }
                    binding.passwordTextInputLayout.setEndIconDrawable(iconRes)
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.events.collectLatest { event ->
                    when (event) {
                        is AuthEvent.ShowToast ->
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        is AuthEvent.ShowError ->
                            handleLoginFailure(event.message)
                        is AuthEvent.ShowHumedsHint ->
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                        AuthEvent.NavigateToUser ->
                            navigateToUserFragment()
                        AuthEvent.NavigateToLogin -> Unit
                    }
                }
            }
        }
    }

    private fun setupDebugEnvSwitcher() {
        if (!BuildConfig.DEBUG) {
            binding.debugEnvContainer.isVisible = false
            return
        }

        binding.debugEnvContainer.isVisible = true

        applyEnvRadioTint()

        when (EndpointProvider.currentEnvLabelForDebug()) {
            "阿里云" -> {
                binding.rbEnvAliyun.isChecked = true
                binding.rbEnvLocal.isChecked = false
            }
            "本地" -> {
                binding.rbEnvAliyun.isChecked = false
                binding.rbEnvLocal.isChecked = true
            }
        }

        binding.debugEnvRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!BuildConfig.DEBUG) {
                return@setOnCheckedChangeListener
            }

            when (checkedId) {
                binding.rbEnvAliyun.id -> EndpointProvider.useAliyunBackendForDebug()
                binding.rbEnvLocal.id -> EndpointProvider.useLocalBackendForDebug()
            }

            val appContext = requireContext().applicationContext
            RetrofitClient.reinitialize(appContext)

            val label = EndpointProvider.currentEnvLabelForDebug()
            Toast.makeText(requireContext(), "已切换后端环境：$label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyEnvRadioTint() {
        val checked = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            ),
            intArrayOf(
                0xFF00C853.toInt(), // green when checked
                0xFF9E9E9E.toInt(), // gray when unchecked
            )
        )
        binding.rbEnvAliyun.buttonTintList = checked
        binding.rbEnvLocal.buttonTintList = checked
    }

    private fun navigateToUserFragment() {
        hostFragmentManager().beginTransaction()
            .replace(hostContainerId(), UserFragment())
            .commit()
    }

    private fun handleLoginFailure(message: String) {
        Log.e("LoginFragment", "登录失败: $message")
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        loginViewModel.handleIntent(AuthIntent.ClearError)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
