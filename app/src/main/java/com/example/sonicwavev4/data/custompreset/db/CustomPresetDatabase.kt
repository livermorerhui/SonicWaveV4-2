package com.example.sonicwavev4.data.custompreset.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CustomPresetEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(CustomPresetConverters::class)
abstract class CustomPresetDatabase : RoomDatabase() {
    abstract fun customPresetDao(): CustomPresetDao

    companion object {
        @Volatile
        private var instance: CustomPresetDatabase? = null

        fun getInstance(context: Context): CustomPresetDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): CustomPresetDatabase =
            Room.databaseBuilder(
                context,
                CustomPresetDatabase::class.java,
                "custom_presets.db"
            ).build()
    }
}
