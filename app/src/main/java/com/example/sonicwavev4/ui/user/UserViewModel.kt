package com.example.sonicwavev4.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.repository.CustomerRepository
import com.example.sonicwavev4.utils.OfflineTestModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {

    private val customerRepository = CustomerRepository()

    private val _accountType = MutableStateFlow<String?>(null)
    val accountType: StateFlow<String?> = _accountType.asStateFlow()
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // For Add Customer operation
    private val _addCustomerResult = MutableStateFlow<Result<Unit>?>(null)
    val addCustomerResult: StateFlow<Result<Unit>?> = _addCustomerResult

    // For Update Customer operation
    private val _updateCustomerResult = MutableStateFlow<Result<Unit>?>(null)
    val updateCustomerResult: StateFlow<Result<Unit>?> = _updateCustomerResult

    // For Customer List (unfiltered from server)
    private val _customers = MutableStateFlow<List<Customer>?>(null)

    // For filtered Customer List (displayed in UI)
    private val _filteredCustomers = MutableStateFlow<List<Customer>?>(null)
    val filteredCustomers: StateFlow<List<Customer>?> = _filteredCustomers.asStateFlow()

    // For search query
    private val _searchQuery = MutableStateFlow("")

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // For Selected Customer
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    init {
        // Combine customers and searchQuery to produce filteredCustomers
        viewModelScope.launch {
            _customers.combine(_searchQuery) { customers, query ->
                if (customers == null) {
                    null
                } else if (query.isBlank()) {
                    customers
                } else {
                    customers.filter { it.name.contains(query, ignoreCase = true) }
                }
            }.collect { _filteredCustomers.value = it }
        }
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }
    fun clearSelectedCustomer() {
        _selectedCustomer.value = null
    }
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchCustomers() {
        viewModelScope.launch {
            if (OfflineTestModeManager.isOfflineMode()) {
                _customers.value = emptyList()
                _filteredCustomers.value = emptyList()
                _loading.value = false
                return@launch
            }
            _loading.value = true
            try {
                val response = customerRepository.getCustomers()
                if (response.isSuccessful) {
                    _customers.value = response.body()
                } else {
                    // Handle error
                    _customers.value = emptyList()
                }
            } catch (e: Exception) {
                // Handle exception
                _customers.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun addCustomer(customer: Customer) {
        viewModelScope.launch {
            if (OfflineTestModeManager.isOfflineMode()) {
                _addCustomerResult.value = Result.failure(IllegalStateException("离线测试模式下无法添加客户"))
                return@launch
            }
            try {
                val response = customerRepository.addCustomer(customer)
                if (response.isSuccessful) {
                    _addCustomerResult.value = Result.success(Unit)
                    fetchCustomers() // Refresh list after adding
                } else {
                    _addCustomerResult.value = Result.failure(Exception(response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                _addCustomerResult.value = Result.failure(e)
            }
        }
    }

    fun updateCustomer(customerId: Int, customer: Customer) {
        viewModelScope.launch {
            if (OfflineTestModeManager.isOfflineMode()) {
                _updateCustomerResult.value = Result.failure(IllegalStateException("离线测试模式下无法更新客户"))
                return@launch
            }
            try {
                val response = customerRepository.updateCustomer(customerId, customer)
                if (response.isSuccessful) {
                    _updateCustomerResult.value = Result.success(Unit)
                    fetchCustomers() // Refresh list after updating
                } else {
                    _updateCustomerResult.value = Result.failure(Exception(response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                _updateCustomerResult.value = Result.failure(e)
            }
        }
    }

    fun resetAddCustomerResult() {
        _addCustomerResult.value = null
    }

    fun resetUpdateCustomerResult() {
        _updateCustomerResult.value = null
    }

    fun onLoginSuccess(accountType: String?) {
        _accountType.value = accountType
        _isLoggedIn.value = true
    }

    fun clearSessionState() {
        _selectedCustomer.value = null
        _accountType.value = null
        _isLoggedIn.value = false
    }
}
