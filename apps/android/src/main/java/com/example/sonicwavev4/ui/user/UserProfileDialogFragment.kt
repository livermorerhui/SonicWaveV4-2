package com.example.sonicwavev4.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.databinding.DialogUserProfileBinding
import com.example.sonicwavev4.ui.login.LoginViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class UserProfileDialogFragment : DialogFragment() {

    private var _binding: DialogUserProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserProfileViewModel by viewModels()
    private val loginViewModel: LoginViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        val currentUsername = loginViewModel.uiState.value.username
        viewModel.initUsername(currentUsername)
        binding.etUsername.setText(currentUsername ?: "")

        setupListeners()
        collectUiState()
        collectEvents()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * 0.5f).toInt()
        val height = (metrics.heightPixels * 0.8f).toInt()
        dialog?.window?.setLayout(width, height)
        dialog?.setCanceledOnTouchOutside(false)
    }

    private fun setupListeners() {
        binding.etUsername.doOnTextChanged { text, _, _, _ ->
            viewModel.onUsernameChanged(text?.toString().orEmpty())
        }
        binding.etOldPassword.doOnTextChanged { text, _, _, _ ->
            viewModel.onOldPasswordChanged(text?.toString().orEmpty())
        }
        binding.etNewPassword.doOnTextChanged { text, _, _, _ ->
            viewModel.onNewPasswordChanged(text?.toString().orEmpty())
        }
        binding.etConfirmNewPassword.doOnTextChanged { text, _, _, _ ->
            viewModel.onConfirmNewPasswordChanged(text?.toString().orEmpty())
        }
        binding.btnChangePassword.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isPasswordMode) {
                viewModel.enterPasswordMode()
            }
        }
        binding.btnBack.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isPasswordMode) {
                viewModel.backToProfileMode()
            } else {
                dismissAllowingStateLoss()
            }
        }
        binding.btnSave.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.isPasswordMode) {
                viewModel.saveProfile()
            }
        }
        binding.btnPasswordBack.setOnClickListener {
            viewModel.backToProfileMode()
        }
        binding.btnPasswordConfirm.setOnClickListener {
            viewModel.submitPasswordChange()
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.profileContentLayout.isVisible = !state.isPasswordMode
                    binding.passwordContentLayout.isVisible = state.isPasswordMode
                    binding.bottomButtonBar.isVisible = !state.isPasswordMode
                    binding.passwordActionBar.isVisible = state.isPasswordMode
                    binding.tvTitle.text = if (state.isPasswordMode) "修改密码" else "用户信息"

                    binding.btnSave.isEnabled = if (state.isPasswordMode) {
                        state.isPasswordSaveEnabled && !state.isPasswordSaving
                    } else {
                        state.isProfileSaveEnabled && !state.isProfileSaving
                    }
                    binding.btnPasswordConfirm.isEnabled = state.isPasswordSaveEnabled && !state.isPasswordSaving

                    binding.tvError.isVisible = state.errorMessage != null
                    binding.tvError.text = state.errorMessage ?: ""
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UserProfileEvent.ProfileUpdated -> {
                            loginViewModel.overrideUsername(event.newUsername)
                            Toast.makeText(requireContext(), "资料已保存", Toast.LENGTH_SHORT).show()
                        }
                        is UserProfileEvent.PasswordChanged -> {
                            Toast.makeText(requireContext(), "密码修改成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
