package com.example.sonicwavev4.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.databinding.FragmentUserBinding
import com.example.sonicwavev4.ui.customer.CustomerListFragment
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.login.LoginFragment
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.ui.humeds.HumedsTestDialogFragment
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.TestToneSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: LoginViewModel by activityViewModels()
    private val customerViewModel: CustomerViewModel by activityViewModels()
    private var suppressToneSwitchChange = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLogoutButton()
        ensureCustomerListFragment()
        collectAuthState()
        collectAuthEvents()
        setupToneSwitch()
        observeTestToneSetting()
        setupHumedsTestButton()
    }

    private fun collectAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collectLatest { state ->
                    val username = state.username ?: "用户"
                    binding.userNameTextview.text = "欢迎, $username!"
                    val isTestAccount = state.accountType?.equals("test", ignoreCase = true) == true
                    binding.switchTestSineTone.isVisible = isTestAccount
                    binding.switchTestSineTone.isEnabled = isTestAccount
                    if (!isTestAccount) {
                        suppressToneSwitchChange = true
                        binding.switchTestSineTone.isChecked = false
                        suppressToneSwitchChange = false
                        if (TestToneSettings.sineToneEnabled.value) {
                            TestToneSettings.setSineToneEnabled(false)
                        }
                    }
                }
            }
        }
    }

    private fun collectAuthEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.events.collectLatest { event ->
                    when (event) {
                        AuthEvent.NavigateToLogin -> navigateToLogin()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun setupToneSwitch() {
        binding.switchTestSineTone.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToneSwitchChange) return@setOnCheckedChangeListener
            TestToneSettings.setSineToneEnabled(isChecked)
        }
    }

    private fun observeTestToneSetting() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestToneSettings.sineToneEnabled.collectLatest { enabled ->
                    val shouldCheck = enabled && binding.switchTestSineTone.isEnabled
                    if (binding.switchTestSineTone.isChecked != shouldCheck) {
                        suppressToneSwitchChange = true
                        binding.switchTestSineTone.isChecked = shouldCheck
                        suppressToneSwitchChange = false
                    }
                }
            }
        }
    }

    private fun ensureCustomerListFragment() {
        if (childFragmentManager.findFragmentById(R.id.customer_list_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.customer_list_container, CustomerListFragment())
                .commit()
        }
    }

    private fun setupLogoutButton() {
        binding.logoutButton.setOnClickListener {
            customerViewModel.clearSessionState()
            authViewModel.handleIntent(AuthIntent.Logout(LogoutReason.UserInitiated))
        }
    }

    private fun setupHumedsTestButton() {
        binding.btnHumedsTest.setOnClickListener {
            HumedsTestDialogFragment().show(parentFragmentManager, "HumedsTestDialog")
        }
    }

    private fun navigateToLogin() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
