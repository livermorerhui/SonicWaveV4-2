package com.example.sonicwavev4.ui.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentUserBinding
import com.example.sonicwavev4.network.LogoutEventRequest
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.login.LoginFragment
import com.example.sonicwavev4.ui.AddCustomerDialogFragment
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.launch

class UserFragment : Fragment() {

    private var userName: String? = null
    private lateinit var sessionManager: SessionManager
    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userName = it.getString(ARG_USER_NAME)
        }
    }

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
        setupUI()
        setupLogoutButton()
        setupAddCustomerButton()
    }

    private fun setupAddCustomerButton() {
        binding.addCustomerButton.setOnClickListener {
            AddCustomerDialogFragment().show(parentFragmentManager, "AddCustomerDialog")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        binding.userNameTextview.text = "欢迎, $userName!"
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
        // 导航回登录页
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, LoginFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_USER_NAME = "user_name"
        @JvmStatic
        fun newInstance(userName: String) = UserFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_USER_NAME, userName)
            }
        }
    }
}