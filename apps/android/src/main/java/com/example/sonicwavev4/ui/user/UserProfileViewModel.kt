package com.example.sonicwavev4.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.UserProfileRepository
import com.example.sonicwavev4.utils.PasswordValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val initialUsername: String = "",
    val editedUsername: String = "",
    val isProfileSaving: Boolean = false,
    val isProfileSaveEnabled: Boolean = false,
    val isPasswordMode: Boolean = false,
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val isPasswordSaving: Boolean = false,
    val isPasswordSaveEnabled: Boolean = false,
    val errorMessage: String? = null,
)

sealed class UserProfileEvent {
    data class ProfileUpdated(val newUsername: String) : UserProfileEvent()
    object PasswordChanged : UserProfileEvent()
}

class UserProfileViewModel(
    private val repository: UserProfileRepository = UserProfileRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UserProfileEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UserProfileEvent> = _events.asSharedFlow()

    fun initUsername(username: String?) {
        val safe = username ?: ""
        _uiState.value = UserProfileUiState(
            initialUsername = safe,
            editedUsername = safe,
            isProfileSaving = false,
            isProfileSaveEnabled = false,
            isPasswordMode = false,
        )
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { current ->
            val trimmed = value.trim()
            val isDirty = trimmed.isNotEmpty() && trimmed != current.initialUsername
            current.copy(
                editedUsername = value,
                isProfileSaveEnabled = isDirty,
                errorMessage = null
            )
        }
    }

    fun enterPasswordMode() {
        _uiState.update {
            it.copy(
                isPasswordMode = true,
                errorMessage = null,
            )
        }
    }

    fun backToProfileMode() {
        _uiState.update {
            it.copy(
                isPasswordMode = false,
                oldPassword = "",
                newPassword = "",
                confirmNewPassword = "",
                isPasswordSaving = false,
                isPasswordSaveEnabled = false,
                errorMessage = null,
            )
        }
    }

    fun onOldPasswordChanged(value: String) {
        _uiState.update {
            it.copy(oldPassword = value).recalculatePasswordEnabled()
        }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update {
            it.copy(newPassword = value).recalculatePasswordEnabled()
        }
    }

    fun onConfirmNewPasswordChanged(value: String) {
        _uiState.update {
            it.copy(confirmNewPassword = value).recalculatePasswordEnabled()
        }
    }

    private fun UserProfileUiState.recalculatePasswordEnabled(): UserProfileUiState {
        val oldPwd = oldPassword.trim()
        val newPwd = newPassword.trim()
        val confirmPwd = confirmNewPassword.trim()

        if (oldPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
            return copy(isPasswordSaveEnabled = false, errorMessage = null)
        }

        val validation = PasswordValidator.validate(newPwd)
        if (!validation.isValid) {
            return copy(
                isPasswordSaveEnabled = false,
                errorMessage = validation.message
            )
        }

        if (newPwd != confirmPwd) {
            return copy(
                isPasswordSaveEnabled = false,
                errorMessage = "两次输入的新密码不一致"
            )
        }

        return copy(
            isPasswordSaveEnabled = true,
            errorMessage = null
        )
    }

    fun saveProfile() {
        val current = _uiState.value
        val trimmed = current.editedUsername.trim()
        if (!current.isProfileSaveEnabled || trimmed.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProfileSaving = true, errorMessage = null) }
            val result = repository.updateProfile(trimmed)
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isProfileSaving = false,
                            initialUsername = trimmed,
                            editedUsername = trimmed,
                            isProfileSaveEnabled = false,
                        )
                    }
                    _events.emit(UserProfileEvent.ProfileUpdated(trimmed))
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isProfileSaving = false,
                            errorMessage = e.message ?: "保存失败，请稍后重试"
                        )
                    }
                }
        }
    }

    fun submitPasswordChange() {
        val current = _uiState.value
        val oldPwd = current.oldPassword.trim()
        val newPwd = current.newPassword.trim()
        val confirmPwd = current.confirmNewPassword.trim()
        if (!current.isPasswordSaveEnabled || oldPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPasswordSaving = true, errorMessage = null) }
            val result = repository.changePassword(oldPwd, newPwd)
            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isPasswordSaving = false,
                            oldPassword = "",
                            newPassword = "",
                            confirmNewPassword = "",
                            isPasswordSaveEnabled = false,
                            isPasswordMode = false,
                        )
                    }
                    _events.emit(UserProfileEvent.PasswordChanged)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isPasswordSaving = false,
                            errorMessage = e.message ?: "修改密码失败，请稍后重试"
                        )
                    }
                }
        }
    }
}
