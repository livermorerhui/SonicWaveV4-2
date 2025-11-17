package com.example.sonicwavev4.data.offlinecustomer.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineCustomerDao {
    @Query("SELECT * FROM offline_customers ORDER BY id DESC")
    suspend fun listAll(): List<OfflineCustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineCustomerEntity): Long
}
