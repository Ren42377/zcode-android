package com.zcode.android.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(
        id: String,
        title: String,
    )

    @Query("UPDATE sessions SET updatedAt = :time WHERE id = :id")
    suspend fun touch(
        id: String,
        time: Long,
    )

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observe(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun list(sessionId: String): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clear(sessionId: String)
}

data class SessionUsage(
    val inputTokens: Int,
    val outputTokens: Int,
)

@Dao
interface ToolEventDao {
    @Query("SELECT * FROM tool_events WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observe(sessionId: String): Flow<List<ToolEventEntity>>

    @Insert
    suspend fun insert(event: ToolEventEntity)

    @Query(
        "SELECT IFNULL(SUM(inputTokens), 0) AS inputTokens, IFNULL(SUM(outputTokens), 0) AS outputTokens FROM messages WHERE sessionId = :sessionId",
    )
    fun observeUsage(sessionId: String): Flow<SessionUsage>
}
