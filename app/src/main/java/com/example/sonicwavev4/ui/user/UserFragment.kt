package com.example.sonicwavev4.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sonicwavev4.R
import com.example.sonicwavev4.ui.login.LoginFragment
import android.widget.ImageButton

class UserFragment : Fragment() {

    private var userName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userName = it.getString(ARG_USER_NAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userNameTextView: TextView = view.findViewById(R.id.user_name_textview)
        userNameTextView.text = "Welcome, $userName!"

        val logoutButton: ImageButton = view.findViewById(R.id.logout_button)
        logoutButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_right_main, LoginFragment())
                .commit()
        }
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
