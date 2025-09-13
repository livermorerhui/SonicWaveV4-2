package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.example.sonicwavev4.ui.login.LoginDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.example.sonicwavev4.ui.register.RegisterViewModel // Correct import

class RegisterDialogFragment : DialogFragment() {

    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usernameEditText: TextInputEditText = view.findViewById(R.id.usernameEditText)
        val emailEditText: TextInputEditText = view.findViewById(R.id.emailEditText)
        val passwordEditText: TextInputEditText = view.findViewById(R.id.passwordEditText)
        val confirmPasswordEditText: TextInputEditText = view.findViewById(R.id.confirmPasswordEditText)
        val registerButton: Button = view.findViewById(R.id.registerButton)
        val loginLinkTextView: TextView = view.findViewById(R.id.loginLinkTextView)

        registerButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (password == confirmPassword) {
                    registerViewModel.register(username, email, password)
                } else {
                    Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        registerViewModel.registerResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
                dismiss() // Dismiss the registration dialog
                LoginDialogFragment().show(parentFragmentManager, "LoginDialog")
            }.onFailure {
                Toast.makeText(requireContext(), "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        loginLinkTextView.setOnClickListener {
            dismiss()
            LoginDialogFragment().show(parentFragmentManager, "LoginDialog")
        }
    }
}
