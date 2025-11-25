package com.example.sonicwavev4.data.offlinecustomer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OfflineCustomerEntity::class],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_customers_name ON offline_customers(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_customers_phone ON offline_customers(phone)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_customers_email ON offline_customers(email)")
            }
        }
    }
}
