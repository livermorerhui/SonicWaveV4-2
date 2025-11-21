package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentRegisterBinding
import com.example.sonicwavev4.ui.login.LoginFragment
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.ui.user.UserFragment

class RegisterFragment : Fragment() {

    private val registerViewModel: RegisterViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()

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
        setupRegisterButton()
        observeViewModels()
        setupLoginLink()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            navigateBackToLogin()
        }
    }

    private fun setupRegisterButton() {
        binding.registerButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                if (password == confirmPassword) {
                    binding.registerButton.isEnabled = false
                    registerViewModel.register(username, email, password)
                } else {
                    Toast.makeText(requireContext(), "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "请填写完整的注册信息", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModels() {
        // Observe registration result
        registerViewModel.registerResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "注册成功，正在登录...", Toast.LENGTH_SHORT).show()
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString()
                // Trigger the complete login flow in the ViewModel
                loginViewModel.login(email, password)
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "注册失败，请稍后再试", Toast.LENGTH_LONG).show()
                binding.registerButton.isEnabled = true
            }
        }

        // Observe login result (only for failure cases now)
        loginViewModel.loginResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                result.onFailure { error ->
                    handleLoginFailure(error)
                }
            }
        }

        // Observe navigation event (for success cases)
        loginViewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { // No userName needed here anymore
                navigateToUserFragment()
            }
        }
    }

    private fun setupLoginLink() {
        binding.loginLinkTextView.setOnClickListener {
            navigateBackToLogin()
        }
    }

    private fun navigateToUserFragment() {
        Toast.makeText(requireContext(), "登录成功！", Toast.LENGTH_SHORT).show()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment())
            .commit()
    }

    private fun navigateBackToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commit()
    }

    private fun handleLoginFailure(error: Throwable) {
        Log.e("RegisterFragment", "Auto-login after registration failed", error)
        Toast.makeText(requireContext(), error.message ?: "自动登录失败，请稍后再试", Toast.LENGTH_LONG).show()
        binding.registerButton.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
