package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.databinding.FragmentLoginBinding
import com.example.sonicwavev4.ui.register.RegisterFragment
import com.example.sonicwavev4.ui.user.UserFragment
import com.example.sonicwavev4.utils.OfflineModeRemoteSync
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by activityViewModels()
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
        collectUiState()
        collectEvents()
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            OfflineModeRemoteSync.sync(requireContext(), force = false)
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val mobile = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            loginViewModel.handleIntent(AuthIntent.Login(mobile, password))
        }
        binding.registerTextView.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_right_main, RegisterFragment())
                .commit()
        }
        binding.offlineModeButton.setOnClickListener {
            loginViewModel.handleIntent(AuthIntent.EnterOfflineMode)
        }
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

    private fun navigateToUserFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment())
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
