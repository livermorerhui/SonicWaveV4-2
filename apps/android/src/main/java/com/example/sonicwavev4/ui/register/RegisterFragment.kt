package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.databinding.FragmentRegisterBinding
import com.example.sonicwavev4.ui.login.LoginFragment
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.ui.user.UserFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private val loginViewModel: LoginViewModel by activityViewModels()
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
        collectUiState()
        collectEvents()
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
            loginViewModel.handleIntent(AuthIntent.Register(username, email, password, confirmPassword))
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.uiState.collectLatest { state ->
                    binding.registerButton.isEnabled = !state.isLoading
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.events.collectLatest { event ->
                    when (event) {
                        is AuthEvent.ShowToast -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        is AuthEvent.ShowError -> handleLoginFailure(event.message)
                        AuthEvent.NavigateToUser -> navigateToUserFragment()
                        AuthEvent.NavigateToLogin -> Unit
                    }
                }
            }
        }
    }

    private fun setupLoginLink() {
        binding.loginLinkTextView.setOnClickListener {
            navigateBackToLogin()
        }
    }

    private fun navigateToUserFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment())
            .commit()
    }

    private fun navigateBackToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commit()
    }

    private fun handleLoginFailure(errorMessage: String) {
        Log.e("RegisterFragment", "Auto-login after registration failed: $errorMessage")
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        loginViewModel.handleIntent(AuthIntent.ClearError)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
