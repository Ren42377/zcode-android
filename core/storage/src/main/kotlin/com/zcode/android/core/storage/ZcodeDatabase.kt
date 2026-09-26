package com.zcode.android.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ZcodeDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "zcode.db"
    }
}
