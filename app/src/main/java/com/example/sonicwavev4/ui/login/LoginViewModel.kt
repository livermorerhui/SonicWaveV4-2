package com.example.sonicwavev4.ui.login

import android.app.Application
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

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _loginResult = MutableLiveData<Event<Result<LoginResponse>>>()
    val loginResult: LiveData<Event<Result<LoginResponse>>> = _loginResult

    private val _navigationEvent = MutableLiveData<Event<String>>()
    val navigationEvent: LiveData<Event<String>> = _navigationEvent

    private val sessionManager = SessionManager(application)

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                // Step 1: Login with credentials
                val loginApiResponse = RetrofitClient.api.login(LoginRequest(email, password))

                if (loginApiResponse.isSuccessful && loginApiResponse.body() != null) {
                    val loginResponse = loginApiResponse.body()!!
                    
                    // Step 2: Save Access and Refresh tokens immediately
                    sessionManager.saveTokens(loginResponse.accessToken, loginResponse.refreshToken)
                    RetrofitClient.updateToken(loginResponse.accessToken)
                    Log.d("DEBUG_FLOW", "LoginViewModel: Tokens saved.")

                    // Step 3: Record the login event to get a session ID
                    Log.d("DEBUG_FLOW", "LoginViewModel: Recording login event to get session ID...")
                    val loginEventResponse = RetrofitClient.api.recordLoginEvent(LoginEventRequest())

                    if (loginEventResponse.isSuccessful && loginEventResponse.body() != null) {
                        val sessionId = loginEventResponse.body()!!.sessionId
                        sessionManager.saveSessionId(sessionId)
                        Log.d("DEBUG_FLOW", "LoginViewModel: Session ID ($sessionId) saved.")

                        // Step 4: Start background services
                        HeartbeatManager.start(getApplication())
                        Log.d("DEBUG_FLOW", "LoginViewModel: HeartbeatManager started.")

                        // Step 5: Notify UI that all background tasks are done and it can navigate
                        _loginResult.postValue(Event(Result.success(loginResponse)))
                        _navigationEvent.postValue(Event(loginResponse.username))
                        Log.d("DEBUG_FLOW", "LoginViewModel: Navigation event posted.")

                    } else {
                        throw Exception("Failed to record login event after a successful login. Code: ${loginEventResponse.code()}")
                    }
                } else {
                    throw Exception("Login failed: ${loginApiResponse.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("DEBUG_FLOW", "Exception in login flow", e)
                _loginResult.postValue(Event(Result.failure(e)))
            }
        }
    }
}
