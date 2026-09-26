package com.zcode.android.core.engine

// Minimal Server Sent Events helpers for the streaming clients. Each protocol
// sends one JSON payload per data line; [DONE] closes OpenAI compatible streams.

object Sse {
    private const val DATA_PREFIX = "data:"

    // Returns the JSON payload carried by a data line, or null when the line is a
    // comment, a field we ignore, or an empty separator.
    fun dataPayload(line: String): String? {
        if (!line.startsWith(DATA_PREFIX)) {
            return null
        }
        val payload = line.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == "[DONE]") {
            return null
        }
        return payload
    }

    fun isDone(line: String): Boolean = line.startsWith(DATA_PREFIX) && line.removePrefix(DATA_PREFIX).trim() == "[DONE]"
}
