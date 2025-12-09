package com.example.sonicwavev4.ui.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.core.CustomerSource
import com.example.sonicwavev4.core.account.CustomerEvent
import com.example.sonicwavev4.core.account.CustomerListUiState
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.repository.CustomerRepository
import com.example.sonicwavev4.repository.HumedsLaunchRepository
import com.example.sonicwavev4.data.offlinecustomer.OfflineCustomerRepository
import com.example.sonicwavev4.utils.OfflineTestModeManager
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerViewModel(application: Application) : AndroidViewModel(application) {

    internal var customerRepository: CustomerRepository = CustomerRepository()
    internal var offlineCustomerRepository: OfflineCustomerRepository =
        OfflineCustomerRepository.getInstance(application)
    private val humedsLaunchRepository = HumedsLaunchRepository(
        SessionManager(application.applicationContext),
        RetrofitClient.api
    )

    private val _uiState = MutableStateFlow(CustomerListUiState())
    val uiState: StateFlow<CustomerListUiState> = _uiState.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _events = MutableSharedFlow<CustomerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CustomerEvent> = _events.asSharedFlow()

    private val _humedsLaunchState = MutableStateFlow<HumedsLaunchUiState>(HumedsLaunchUiState.Idle)
    val humedsLaunchState: StateFlow<HumedsLaunchUiState> = _humedsLaunchState.asStateFlow()

    fun loadCustomers() {
        viewModelScope.launch {
            val offlineMode = OfflineTestModeManager.isOfflineMode()
            val currentQuery = _uiState.value.searchQuery
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    source = if (offlineMode) CustomerSource.OFFLINE else CustomerSource.ONLINE
                )
            }
            try {
                val customers = withContext(Dispatchers.IO) {
                    if (offlineMode) {
                        offlineCustomerRepository.listCustomers(currentQuery)
                    } else {
                        val response = customerRepository.getCustomers()
                        if (response.isSuccessful) {
                            response.body().orEmpty()
                        } else {
                            throw Exception(response.errorBody()?.string() ?: "获取客户列表失败")
                        }
                    }
                }
                updateCustomers(customers, offlineMode, currentQuery)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                _events.emit(CustomerEvent.Error(e.message ?: "获取客户列表失败"))
            }
        }
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun clearSelectedCustomer() {
        _selectedCustomer.value = null
        _uiState.update { it.copy(selectedCustomer = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (OfflineTestModeManager.isOfflineMode()) {
            viewModelScope.launch {
                val filtered = withContext(Dispatchers.IO) {
                    offlineCustomerRepository.listCustomers(query)
                }
                updateCustomers(filtered, offlineMode = true, searchQuery = query)
            }
        } else {
            _uiState.update {
                it.copy(
                    filteredCustomers = filterCustomers(it.customers, query)
                )
            }
        }
    }

    fun addCustomer(customer: Customer) {
        if (OfflineTestModeManager.isOfflineMode()) {
            emitError("离线测试模式下无法添加客户")
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    customerRepository.addCustomer(customer)
                }
                if (response.isSuccessful) {
                    _events.emit(CustomerEvent.CustomerSaved("客户添加成功"))
                    loadCustomers()
                } else {
                    throw Exception(response.errorBody()?.string() ?: "添加客户失败")
                }
            } catch (e: Exception) {
                emitError(e.message ?: "添加客户失败")
            }
        }
    }

    fun updateCustomer(customerId: Int, customer: Customer) {
        if (OfflineTestModeManager.isOfflineMode()) {
            emitError("离线测试模式下无法更新客户")
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    customerRepository.updateCustomer(customerId, customer)
                }
                if (response.isSuccessful) {
                    _events.emit(CustomerEvent.CustomerSaved("客户更新成功"))
                    loadCustomers()
                } else {
                    throw Exception(response.errorBody()?.string() ?: "更新客户失败")
                }
            } catch (e: Exception) {
                emitError(e.message ?: "更新客户失败")
            }
        }
    }

    fun addOfflineCustomer(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                offlineCustomerRepository.addCustomer(trimmedName)
            }
            loadCustomers()
            _events.emit(CustomerEvent.CustomerSaved("已添加本地客户"))
        }
    }

    fun clearSessionState() {
        _selectedCustomer.value = null
        _uiState.value = CustomerListUiState()
    }

    fun onLaunchHumedsClicked() {
        _humedsLaunchState.value = HumedsLaunchUiState.Loading
        viewModelScope.launch {
            runCatching { humedsLaunchRepository.getHumedsTokenForCurrentUser() }
                .onSuccess { token ->
                    _humedsLaunchState.value = HumedsLaunchUiState.Success(token)
                }
                .onFailure { e ->
                    _humedsLaunchState.value =
                        HumedsLaunchUiState.Error(e.message ?: "获取 Humeds token 失败，请稍后重试")
                }
        }
    }

    fun resetHumedsLaunchState() {
        _humedsLaunchState.value = HumedsLaunchUiState.Idle
    }

    private fun updateCustomers(customers: List<Customer>, offlineMode: Boolean, searchQuery: String) {
        val filtered = if (offlineMode) customers else filterCustomers(customers, searchQuery)
        _uiState.update {
            it.copy(
                customers = customers,
                filteredCustomers = filtered,
                selectedCustomer = _selectedCustomer.value,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    private fun filterCustomers(customers: List<Customer>, query: String): List<Customer> {
        if (query.isBlank()) return customers
        return customers.filter { it.name.contains(query, ignoreCase = true) }
    }

    private fun emitError(message: String) {
        viewModelScope.launch { _events.emit(CustomerEvent.Error(message)) }
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
    }
}

sealed class HumedsLaunchUiState {
    object Idle : HumedsLaunchUiState()
    object Loading : HumedsLaunchUiState()
    data class Success(val tokenJwt: String) : HumedsLaunchUiState()
    data class Error(val message: String) : HumedsLaunchUiState()
}
