package com.example.sonicwavev4.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment // 导入 DialogFragment

import com.example.sonicwavev4.R

// LoginDialogFragment 不需要 Navigation Graph 中的目的地，它是一个对话框
class LoginDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment (dialog_login.xml)
        val view = inflater.inflate(R.layout.dialog_login, container, false)

        val usernameEditText: EditText = view.findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = view.findViewById(R.id.passwordEditText)
        val loginButton: Button = view.findViewById(R.id.loginButton)
        val forgotPasswordTextView: TextView = view.findViewById(R.id.forgotPasswordTextView)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                // 实际的登录逻辑
                Toast.makeText(requireContext(), "尝试登录: $username", Toast.LENGTH_SHORT).show()
                // 假设登录成功，关闭对话框
                dismiss() // 关闭对话框

                // 如果需要，可以在这里通知 MainActivity 登录成功
                // (例如通过接口回调或 ViewModel 共享数据)
                // val activity = activity as? MainActivity
                // activity?.onLoginSuccess()
            } else {
                Toast.makeText(requireContext(), "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            }
        }

        forgotPasswordTextView.setOnClickListener {
            Toast.makeText(requireContext(), "忘记密码功能待实现", Toast.LENGTH_SHORT).show()
            // 如果忘记密码也是一个弹窗，可以在这里显示另一个 DialogFragment
            // 或者导航到一个新的 Fragment（如果是一个全屏页面）
            dismiss() // 关闭当前登录对话框
            // findNavController().navigate(R.id.navigation_forgot_password_dialog)
        }

        return view
    }

    // 可选：设置对话框的样式，例如全屏或自定义尺寸
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 可以设置对话框的主题，使其有圆角或无标题栏等
        // setStyle(STYLE_NO_TITLE, R.style.YourDialogTheme)
    }
}