package com.zcode.android.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val workspacePath: String,
    val providerId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val thinking: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val createdAt: Long,
)

// One executed tool call, persisted so transcripts keep the tool cards on resume.
@Entity(
    tableName = "tool_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ToolEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val summary: String,
    val output: String,
    val isError: Boolean,
    val createdAt: Long,
)
