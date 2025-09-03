package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.sonicwavev4.R

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_login, container, false)

        val usernameEditText: EditText = root.findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = root.findViewById(R.id.passwordEditText)
        val loginButton: Button = root.findViewById(R.id.loginButton)
        val forgotPasswordTextView: TextView = root.findViewById(R.id.forgotPasswordTextView)
        val registerTextView: TextView = root.findViewById(R.id.registerTextView)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                // 这里可以添加实际的登录逻辑，例如调用ViewModel或API
                Toast.makeText(requireContext(), "尝试登录: $username", Toast.LENGTH_SHORT).show()
                // 登录成功后，可以导航回首页或其他页面
                findNavController().navigate(R.id.navigation_home) // 示例：登录成功后返回首页
            } else {
                Toast.makeText(requireContext(), "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            }
        }

        forgotPasswordTextView.setOnClickListener {
            Toast.makeText(requireContext(), "忘记密码功能待实现", Toast.LENGTH_SHORT).show()
            // 导航到忘记密码页面
            // findNavController().navigate(R.id.navigation_forgot_password)
        }

        registerTextView.setOnClickListener {
            Toast.makeText(requireContext(), "注册功能待实现", Toast.LENGTH_SHORT).show()
            // 导航到注册页面
            // findNavController().navigate(R.id.navigation_register)
        }

        return root
    }
}