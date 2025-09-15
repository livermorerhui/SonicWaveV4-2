package com.example.sonicwavev4.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.sonicwavev4.R
import com.google.android.material.textfield.TextInputEditText

class RegisterFragment : Fragment() {

    private val registerViewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
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
                parentFragmentManager.popBackStack()
            }.onFailure {
                Toast.makeText(requireContext(), "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        loginLinkTextView.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}
