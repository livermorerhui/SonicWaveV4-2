package com.example.sonicwavev4.data.offlinecustomer.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["email"])
    ]
)
data class OfflineCustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val dateOfBirth: String? = null,
    val gender: String = "",
    val phone: String = "",
    val email: String = "",
    val height: Double = 0.0,
    val weight: Double = 0.0
)
