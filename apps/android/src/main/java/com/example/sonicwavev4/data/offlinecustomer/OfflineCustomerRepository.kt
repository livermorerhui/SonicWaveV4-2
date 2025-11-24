package com.example.sonicwavev4.data.offlinecustomer

import android.content.Context
import com.example.sonicwavev4.data.offlinecustomer.db.OfflineCustomerDatabase
import com.example.sonicwavev4.data.offlinecustomer.db.OfflineCustomerEntity
import com.example.sonicwavev4.network.Customer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineCustomerRepository private constructor(
    private val db: OfflineCustomerDatabase,
    private val dispatcher: CoroutineDispatcher
) {

    suspend fun listCustomers(searchQuery: String? = null): List<Customer> = withContext(dispatcher) {
        val sanitizedQuery = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        db.offlineCustomerDao().searchByName(sanitizedQuery).map { it.toNetworkModel() }
    }

    suspend fun addCustomer(name: String): Customer = withContext(dispatcher) {
        val entity = OfflineCustomerEntity(name = name.trim())
        val id = db.offlineCustomerDao().upsert(entity)
        entity.copy(id = id).toNetworkModel()
    }

    private fun OfflineCustomerEntity.toNetworkModel(): Customer =
        Customer(
            id = id.toInt(),
            name = name,
            dateOfBirth = dateOfBirth,
            gender = gender,
            phone = phone,
            email = email,
            height = height,
            weight = weight
        )

    companion object {
        @Volatile
        private var instance: OfflineCustomerRepository? = null

        fun getInstance(context: Context): OfflineCustomerRepository {
            return instance ?: synchronized(this) {
                instance ?: OfflineCustomerRepository(
                    db = OfflineCustomerDatabase.getInstance(context),
                    dispatcher = Dispatchers.IO
                ).also { instance = it }
            }
        }
    }
}
