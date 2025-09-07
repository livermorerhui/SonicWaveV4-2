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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.sonicwavev4.R

class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            result.onSuccess {
                // Handle successful login
                Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_SHORT).show()
                // Here you would typically save the token and navigate
                findNavController().navigate(R.id.navigation_home)
            }.onFailure {
                // Handle failed login
                Toast.makeText(requireContext(), "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        forgotPasswordTextView.setOnClickListener {
            Toast.makeText(requireContext(), "Forgot password not implemented", Toast.LENGTH_SHORT).show()
            // findNavController().navigate(R.id.navigation_forgot_password)
        }

        registerTextView.setOnClickListener {
            Toast.makeText(requireContext(), "Register not implemented", Toast.LENGTH_SHORT).show()
            // findNavController().navigate(R.id.navigation_register)
        }
    }
}
