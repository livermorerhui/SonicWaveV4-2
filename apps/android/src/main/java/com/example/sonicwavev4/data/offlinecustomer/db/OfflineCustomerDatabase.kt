package com.example.sonicwavev4.data.offlinecustomer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineCustomerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineCustomerDatabase : RoomDatabase() {
    abstract fun offlineCustomerDao(): OfflineCustomerDao

    companion object {
        @Volatile
        private var instance: OfflineCustomerDatabase? = null

        fun getInstance(context: Context): OfflineCustomerDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): OfflineCustomerDatabase {
            return Room.databaseBuilder(
                context,
                OfflineCustomerDatabase::class.java,
                "offline_customers.db"
            ).build()
        }
    }
}
