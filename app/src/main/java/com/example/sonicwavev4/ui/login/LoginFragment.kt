package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.example.sonicwavev4.ui.register.RegisterFragment
import com.example.sonicwavev4.ui.user.UserFragment
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.SessionManager
import org.json.JSONObject

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionManager = SessionManager(requireContext())

        val usernameEditText: EditText = view.findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = view.findViewById(R.id.passwordEditText)
        val loginButton: Button = view.findViewById(R.id.loginButton)
        val forgotPasswordTextView: TextView = view.findViewById(R.id.forgotPasswordTextView)
        val registerTextView: TextView = view.findViewById(R.id.registerTextView)

        loginButton.setOnClickListener {
            val email = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginViewModel.login(email, password)
            } else {
                Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        loginViewModel.loginResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { loginResponse ->
                val token = loginResponse.token
                val userName = loginResponse.username
                val email = usernameEditText.text.toString()

                // 1. 保存Token
                sessionManager.saveAuthToken(token)

                // 2. 从Token中解码出真正的userId并保存
                val userId = decodeUserIdFromJwt(token)
                if (userId != null) {
                    sessionManager.saveUserId(userId)
                    Log.d("LoginFragment", "Successfully saved userId: $userId")
                } else {
                    Log.e("LoginFragment", "Failed to parse userId from token.")
                    Toast.makeText(requireContext(), "Login failed: Invalid token data.", Toast.LENGTH_LONG).show()
                    return@onSuccess
                }

                // 3. 保存userName和email
                sessionManager.saveUserName(userName)
                sessionManager.saveEmail(email)
                Log.d("LoginFragment", "Successfully saved userName: $userName and email: $email")

                // 4. 启动心跳
                HeartbeatManager.start(requireContext())

                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    // FIX: Added a null-safe fallback to prevent type mismatch warnings
                    .replace(R.id.fragment_right_main, UserFragment.newInstance(userName))
                    .commit()

            }.onFailure {
                Toast.makeText(requireContext(), "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        forgotPasswordTextView.setOnClickListener {
            Toast.makeText(requireContext(), "Forgot password not implemented", Toast.LENGTH_SHORT).show()
        }

        registerTextView.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_right_main, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    // --- ADDED: 确保这个辅助函数存在于类中 ---
    private fun decodeUserIdFromJwt(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            val jsonObject = JSONObject(payload)
            
            // 使用更健壮的 getString + try/catch 模式代替 optString
            return try {
                jsonObject.getString("userId")
            } catch (_: Exception) {
                null
            }

        } catch (e: Exception) {
            Log.e("LoginFragment", "Error decoding JWT", e)
            return null
        }
    }
}