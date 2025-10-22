package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentLoginBinding
import com.example.sonicwavev4.ui.register.RegisterFragment
import com.example.sonicwavev4.ui.user.UserFragment

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by viewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                binding.loginButton.isEnabled = false
                loginViewModel.login(email, password)
            } else {
                Toast.makeText(requireContext(), "请输入邮箱和密码", Toast.LENGTH_SHORT).show()
            }
        }
        binding.registerTextView.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_right_main, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observeViewModel() {
        loginViewModel.loginResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                result.onSuccess { loginResponse ->
                    // Login success is handled by the navigation event
                    Log.d("LoginFragment", "Login API call successful.")
                }.onFailure { error ->
                    handleLoginFailure(error)
                }
            }
        }

        loginViewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { // No userName needed here anymore
                navigateToUserFragment()
            }
        }
    }

    private fun navigateToUserFragment() {
        Toast.makeText(requireContext(), "登录成功！", Toast.LENGTH_SHORT).show()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment())
            .commit()
    }

    private fun handleLoginFailure(error: Throwable) {
        Log.e("LoginFragment", "登录失败", error)
        Toast.makeText(requireContext(), error.message ?: "登录失败，请稍后再试", Toast.LENGTH_LONG).show()
        binding.loginButton.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
