package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentLoginBinding
import com.example.sonicwavev4.network.LoginEventRequest
import com.example.sonicwavev4.network.LoginResponse
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.register.RegisterFragment
import com.example.sonicwavev4.ui.user.UserFragment
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var sessionManager: SessionManager
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
        sessionManager = SessionManager(requireContext())
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.usernameEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
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

        binding.forgotPasswordTextView.setOnClickListener {
            Toast.makeText(requireContext(), "“忘记密码”功能尚未实现", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        loginViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { loginResponse ->
                handleLoginSuccess(loginResponse)
            }.onFailure { error ->
                handleLoginFailure(error)
            }
        }

        loginViewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { userName ->
                navigateToUserFragment(userName)
            }
        }
    }

    private fun handleLoginSuccess(loginResponse: LoginResponse) {
        val userSessionData = processAndSaveSession(loginResponse)
        if (userSessionData == null) {
            handleLoginFailure(IllegalStateException("无法从Token中解析有效的用户ID。"))
            return
        }
        loginViewModel.startPostLoginTasks(userSessionData.userName)
    }

    /**
     * 最佳实践] 专门负责解析Token和保存所有会话相关的数据。
     * 它返回一个包含关键信息的数据类实例，或在失败时返回null。
     */
    private fun processAndSaveSession(loginResponse: LoginResponse): UserSessionData? {
        val token = loginResponse.token
        val userName = loginResponse.username
        val email = binding.usernameEditText.text.toString() // 从View Binding获取

        val userId = decodeUserIdFromJwt(token) ?: return null // 如果userId为空，则直接返回null

        // 使用Kotlin KTX扩展函数，代码更简洁安全
        sessionManager.saveUserSession(
            token = token,
            userId = userId,
            userName = userName,
            email = email
        )

        // [修复竞态条件] 立即更新内存中的Token
        RetrofitClient.updateToken(token)

        Log.d("LoginFragment", "用户会话已保存，用户ID: $userId")
        return UserSessionData(userId, userName, token)
    }

    /**
     * 最佳实践] 启动所有与用户会话相关的后台服务。
     */
    private fun startBackendServices(token: String) {
        Log.d("DEBUG_FLOW", "LoginFragment: startBackendServices ENTERED")
        lifecycleScope.launch {
            try {
                Log.d("DEBUG_FLOW", "LoginFragment: coroutine launched. Preparing to call recordLoginEvent.")
                val request = LoginEventRequest()
                val response = RetrofitClient.api.recordLoginEvent(request)
                Log.d("DEBUG_FLOW", "LoginFragment: recordLoginEvent call finished.")

                if (response.isSuccessful && response.body() != null) {
                    Log.d("DEBUG_FLOW", "LoginFragment: recordLoginEvent SUCCESSFUL.")
                    val loginEventResponse = response.body()!!
                    sessionManager.saveSessionId(loginEventResponse.sessionId)
                    Log.d("DEBUG_FLOW", "LoginFragment: Session ID ${loginEventResponse.sessionId} saved. Preparing to start HeartbeatManager.")
                    HeartbeatManager.start(requireContext())
                } else {
                    Log.e("DEBUG_FLOW", "LoginFragment: recordLoginEvent FAILED. Code: ${response.code()}, Message: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_FLOW", "LoginFragment: EXCEPTION in startBackendServices.", e)
            }
        }
    }

    /**
     * 最佳实践] 专门处理UI导航。
     */
    private fun navigateToUserFragment(userName: String) {
        Toast.makeText(requireContext(), "登录成功！", Toast.LENGTH_SHORT).show()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_right_main, UserFragment.newInstance(userName))
            .commit()
    }

    /**
     * 最佳实践] 专门处理所有失败情况，并向用户显示统一的错误信息。
     */
    private fun handleLoginFailure(error: Throwable) {
        Log.e("LoginFragment", "登录失败", error)
        Toast.makeText(requireContext(), "登录失败: ${error.message}", Toast.LENGTH_LONG).show()
    }

    /**
     * 一个简单的数据类，用于在函数间传递处理过的用户会话信息。
     */
    private data class UserSessionData(val userId: String, val userName: String, val token: String)

// ... decodeUserIdFromJwt 和其他函数 ...

    private fun decodeUserIdFromJwt(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            val jsonObject = JSONObject(payload)
            return jsonObject.getString("userId")
        } catch (e: Exception) {
            Log.e("LoginFragment", "解码JWT时出错", e)
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // [最佳实践] 避免内存泄漏
    }
}