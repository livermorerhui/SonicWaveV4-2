package com.example.sonicwavev4.ui.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.network.RegisterRequest
import com.example.sonicwavev4.network.RegisterResponse
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val _registerResult = MutableLiveData<Result<RegisterResponse>>()
    val registerResult: LiveData<Result<RegisterResponse>> = _registerResult

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.register(RegisterRequest(username, email, password))
                if (response.isSuccessful && response.body() != null) {
                    _registerResult.postValue(Result.success(response.body()!!))
                } else {
                    val message = ErrorMessageResolver.fromResponse(response.errorBody(), response.code())
                    _registerResult.postValue(Result.failure(Exception(message)))
                }
            } catch (e: Exception) {
                val message = if (e is java.io.IOException) {
                    ErrorMessageResolver.networkFailure(e)
                } else {
                    ErrorMessageResolver.unexpectedFailure(e)
                }
                _registerResult.postValue(Result.failure(Exception(message)))
            }
        }
    }
}
