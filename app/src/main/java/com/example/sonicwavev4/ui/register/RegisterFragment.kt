package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentRegisterBinding
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
                    Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModels() {
        // Observe registration result
        registerViewModel.registerResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "Registration successful! Logging in...", Toast.LENGTH_SHORT).show()
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString()
                // Trigger the complete login flow in the ViewModel
                loginViewModel.login(email, password)
            }.onFailure {
                Toast.makeText(requireContext(), "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
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
            event.getContentIfNotHandled()?.let { userName ->
                navigateToUserFragment(userName)
            }
        }
    }

    private fun setupLoginLink() {
        binding.loginLinkTextView.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun navigateToUserFragment(userName: String) {
        Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment.newInstance(userName))
            .commit()
    }

    private fun handleLoginFailure(error: Throwable) {
        Log.e("RegisterFragment", "Auto-login after registration failed", error)
        Toast.makeText(requireContext(), "Auto-login failed: ${error.message}", Toast.LENGTH_LONG).show()
        binding.registerButton.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
