package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.util.Base64
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
import com.example.sonicwavev4.network.LoginResponse
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.ui.user.UserFragment
import com.example.sonicwavev4.util.Event
import com.example.sonicwavev4.utils.SessionManager
import org.json.JSONObject

class RegisterFragment : Fragment() {

    private val registerViewModel: RegisterViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var sessionManager: SessionManager
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
        sessionManager = SessionManager(requireContext())
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
        registerViewModel.registerResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "Registration successful! Logging in...", Toast.LENGTH_SHORT).show()
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString()
                loginViewModel.login(email, password)
            }.onFailure {
                Toast.makeText(requireContext(), "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
                binding.registerButton.isEnabled = true
            }
        }

        loginViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { loginResponse ->
                handleLoginSuccess(loginResponse)
            }.onFailure {
                handleLoginFailure(it)
                binding.registerButton.isEnabled = true
            }
        }

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

    private fun handleLoginSuccess(loginResponse: LoginResponse) {
        val userSessionData = processAndSaveSession(loginResponse)
        if (userSessionData == null) {
            handleLoginFailure(IllegalStateException("Could not parse a valid User ID from token."))
            return
        }
        loginViewModel.startPostLoginTasks(userSessionData.userName)
    }

    private fun processAndSaveSession(loginResponse: LoginResponse): UserSessionData? {
        val token = loginResponse.token
        val userName = loginResponse.username
        val email = binding.emailEditText.text.toString()
        val userId = decodeUserIdFromJwt(token) ?: return null

        sessionManager.saveUserSession(
            token = token,
            userId = userId,
            userName = userName,
            email = email
        )
        RetrofitClient.updateToken(token)
        Log.d("RegisterFragment", "User session saved, User ID: $userId")
        return UserSessionData(userId, userName)
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
        parentFragmentManager.popBackStack()
    }

    private fun decodeUserIdFromJwt(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            return JSONObject(payload).getString("userId")
        } catch (e: Exception) {
            Log.e("RegisterFragment", "Error decoding JWT", e)
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class UserSessionData(val userId: String, val userName: String)
}
