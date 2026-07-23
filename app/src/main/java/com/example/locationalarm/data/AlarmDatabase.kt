package com.example.locationalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Alarm::class, AlarmHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        /**
         * v1 -> v2: 添加 repeatInterval 和 lastTriggeredAt 字段
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN repeatInterval INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN lastTriggeredAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "location_alarm_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
