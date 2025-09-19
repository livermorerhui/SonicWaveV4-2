package com.example.sonicwavev4.ui.login

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.util.Event
import com.example.sonicwavev4.network.LoginEventRequest
import com.example.sonicwavev4.network.LoginRequest
import com.example.sonicwavev4.network.LoginResponse
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.launch

// [修改] 继承自 AndroidViewModel 以安全地获取 Application Context
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _loginResult = MutableLiveData<Result<LoginResponse>>()
    val loginResult: LiveData<Result<LoginResponse>> = _loginResult

    // [新增] 用于通知UI进行导航的LiveData
    private val _navigationEvent = MutableLiveData<Event<String>>()
    val navigationEvent: LiveData<Event<String>> = _navigationEvent

    private val sessionManager = SessionManager(application)

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    _loginResult.postValue(Result.success(response.body()!!))
                } else {
                    _loginResult.postValue(Result.failure(Exception("Login failed: ${response.errorBody()?.string()}")))
                }
            } catch (e: Exception) {
                _loginResult.postValue(Result.failure(e))
            }
        }
    }

    // [新增] 核心业务逻辑，现在位于ViewModel中
    fun startPostLoginTasks(userName: String) {
        Log.d("DEBUG_FLOW", "LoginViewModel: startPostLoginTasks ENTERED")
        viewModelScope.launch {
            try {
                Log.d("DEBUG_FLOW", "LoginViewModel: coroutine launched in viewModelScope. Preparing to call recordLoginEvent.")
                val request = LoginEventRequest()
                val response = RetrofitClient.api.recordLoginEvent(request)
                Log.d("DEBUG_FLOW", "LoginViewModel: recordLoginEvent call finished.")

                if (response.isSuccessful && response.body() != null) {
                    Log.d("DEBUG_FLOW", "LoginViewModel: recordLoginEvent SUCCESSFUL.")
                    val loginEventResponse = response.body()!!
                    sessionManager.saveSessionId(loginEventResponse.sessionId)
                    Log.d("DEBUG_FLOW", "LoginViewModel: Session ID ${loginEventResponse.sessionId} saved.")

                    // 使用 Application Context 启动心跳
                    HeartbeatManager.start(getApplication()) 
                    Log.d("DEBUG_FLOW", "LoginViewModel: HeartbeatManager started.")

                    // 所有后台任务完成，发送导航通知
                    _navigationEvent.postValue(Event(userName))
                    Log.d("DEBUG_FLOW", "LoginViewModel: Navigation event posted.")

                } else {
                    Log.e("DEBUG_FLOW", "LoginViewModel: recordLoginEvent FAILED. Code: ${response.code()}, Message: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_FLOW", "LoginViewModel: EXCEPTION in startPostLoginTasks.", e)
            }
        }
    }
}
