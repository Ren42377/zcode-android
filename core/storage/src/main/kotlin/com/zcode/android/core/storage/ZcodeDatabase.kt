package com.zcode.android.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, MessageEntity::class, ToolEventEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ZcodeDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun messageDao(): MessageDao

    abstract fun toolEventDao(): ToolEventDao

    companion object {
        const val NAME = "zcode.db"
    }
}
