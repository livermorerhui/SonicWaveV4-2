package com.example.sonicwavev4.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.network.LoginRequest
import com.example.sonicwavev4.network.LoginResponse
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val _loginResult = MutableLiveData<Result<LoginResponse>>()
    val loginResult: LiveData<Result<LoginResponse>> = _loginResult

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    _loginResult.postValue(Result.success(response.body()!!))
                } else {
                    // You can parse the error body here for a more specific message
                    _loginResult.postValue(Result.failure(Exception("Login failed: ${response.errorBody()?.string()}")))
                }
            } catch (e: Exception) {
                _loginResult.postValue(Result.failure(e))
            }
        }
    }
}
