package com.example.sonicwavev4.core.account

import com.example.sonicwavev4.core.CustomerSource
import com.example.sonicwavev4.network.Customer

/**
 * UI snapshot for customer listing and selection flows.
 */
data class CustomerListUiState(
    val customers: List<Customer> = emptyList(),
    val filteredCustomers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val source: CustomerSource = CustomerSource.ONLINE
)

/**
 * One-off customer UI signals.
 */
sealed class CustomerEvent {
    data class CustomerSaved(val message: String) : CustomerEvent()
    data class Error(val message: String) : CustomerEvent()
}
