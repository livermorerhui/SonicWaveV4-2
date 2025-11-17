package com.example.sonicwavev4.ui.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.AppMode
import com.example.sonicwavev4.core.currentAppMode
import com.example.sonicwavev4.databinding.FragmentUserBinding
import com.example.sonicwavev4.network.LogoutEventRequest
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.AddCustomerDialogFragment
import com.example.sonicwavev4.ui.OfflineAddCustomerDialogFragment
import com.example.sonicwavev4.ui.customer.CustomerListFragment
import com.example.sonicwavev4.ui.login.LoginFragment
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.OfflineTestModeManager
import com.example.sonicwavev4.utils.SessionManager
import com.example.sonicwavev4.utils.TestToneSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!
    private val userViewModel: UserViewModel by activityViewModels()
    private var suppressToneSwitchChange = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        setupUsernameDisplay()
        setupLogoutButton()
        setupAddCustomerButton()
        ensureCustomerListFragment()
        observeOfflineMode()
        setupToneSwitch()
        observeAccountType()
        observeGlobalLogout()
    }

    private fun setupUsernameDisplay() {
        val username = sessionManager.fetchUserName()
        binding.userNameTextview.text = "欢迎, ${username ?: "用户"}!"
    }

    private fun setupAddCustomerButton() {
        binding.addCustomerButton.setOnClickListener {
            if (currentAppMode() == AppMode.OFFLINE) {
                OfflineAddCustomerDialogFragment.newInstance().show(childFragmentManager, "OfflineAddCustomerDialog")
            } else {
                AddCustomerDialogFragment.newInstance().show(childFragmentManager, "AddCustomerDialog")
            }
        }
    }

    private fun observeOfflineMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            OfflineTestModeManager.isOfflineTestMode.collectLatest { offline ->
                binding.addCustomerButton.isVisible = true
                binding.customerListContainer.isVisible = true
                binding.offlineModeHint.isVisible = offline
                if (offline) {
                    binding.offlineModeHint.text = getString(R.string.offline_mode_hint)
                }
                ensureCustomerListFragment()
            }
        }
    }

    private fun observeAccountType() {
        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.accountType.collectLatest { accountType ->
                val isTestAccount = accountType?.equals("test", ignoreCase = true) == true
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

    private fun setupToneSwitch() {
        binding.switchTestSineTone.setOnCheckedChangeListener { _, isChecked ->
            if (suppressToneSwitchChange) return@setOnCheckedChangeListener
            TestToneSettings.setSineToneEnabled(isChecked)
        }
        viewLifecycleOwner.lifecycleScope.launch {
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

    private fun observeGlobalLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            GlobalLogoutManager.logoutEvent.collect {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_right_main, LoginFragment())
                    .commitAllowingStateLoss()
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
            Log.d("DEBUG_FLOW", "UserFragment: logoutButton CLICKED.")
            val sessionId = sessionManager.fetchSessionId()
            Log.d("DEBUG_FLOW", "UserFragment: Fetched sessionId for logout: $sessionId")
            if (sessionId == -1L) {
                Log.w("DEBUG_FLOW", "UserFragment: Session ID is -1L. Performing local logout only.")
                performLocalLogout()
                return@setOnClickListener
            }

            Log.d("DEBUG_FLOW", "UserFragment: Launching coroutine for logout network call.")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Log.d("DEBUG_FLOW", "UserFragment: Preparing to call recordLogoutEvent.")
                    val response = RetrofitClient.api.recordLogoutEvent(LogoutEventRequest(sessionId))
                    Log.d("DEBUG_FLOW", "UserFragment: recordLogoutEvent call finished.")
                    if (response.isSuccessful) {
                        Log.d("DEBUG_FLOW", "UserFragment: recordLogoutEvent SUCCESSFUL.")
                    } else {
                        Log.w("DEBUG_FLOW", "UserFragment: recordLogoutEvent FAILED. Code: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("DEBUG_FLOW", "UserFragment: EXCEPTION during recordLogoutEvent.", e)
                } finally {
                    Log.d("DEBUG_FLOW", "UserFragment: Entering finally block. Performing local logout.")
                    performLocalLogout()
                }
            }
        }
    }

    private fun performLocalLogout() {
        // 停止心跳
        HeartbeatManager.stop()
        // 清理本地会话并通知
        sessionManager.initiateLogout(LogoutReason.UserInitiated)
        OfflineTestModeManager.setOfflineTestMode(false)
        userViewModel.clearSessionState()
        // 导航回登录页
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commitAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Removed ARG_USER_NAME as username is now fetched from SessionManager
        // @JvmStatic
        // fun newInstance(userName: String) = UserFragment().apply {
        //     arguments = Bundle().apply {
        //         putString(ARG_USER_NAME, userName)
        //     }
        // }
    }
}
