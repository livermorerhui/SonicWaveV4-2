package com.example.sonicwavev4.ui.login

import android.app.Application
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthGateway
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.core.account.AuthResult
import com.example.sonicwavev4.utils.LogoutReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        viewModel = LoginViewModel(Application()).apply {
            authGateway = FakeAuthGateway()
        }
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun loginSuccess_updatesStateAndEmitsNavigation() = scope.runTest {
        val events = mutableListOf<AuthEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.handleIntent(AuthIntent.Login("user@test.com", "password"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals("test", viewModel.uiState.value.accountType)
        assertTrue(events.any { it is AuthEvent.NavigateToUser })

        job.cancel()
    }

    private class FakeAuthGateway : AuthGateway {
        override suspend fun login(email: String, password: String): Result<AuthResult> {
            return Result.success(AuthResult(username = "tester", accountType = "test", isOfflineMode = false))
        }

        override suspend fun registerAndLogin(username: String, email: String, password: String): Result<AuthResult> {
            return login(email, password)
        }

        override suspend fun enterOfflineMode(): Result<AuthResult> {
            return Result.success(AuthResult(username = "offline", accountType = "test", isOfflineMode = true))
        }

        override suspend fun logout(reason: LogoutReason): Result<Unit> = Result.success(Unit)
    }
}
