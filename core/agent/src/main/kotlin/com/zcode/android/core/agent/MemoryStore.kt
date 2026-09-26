package com.zcode.android.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

// Per-workspace memory in the ZCode format: a MEMORY.md index plus free form
// fact files, stored under app private storage keyed by the workspace path.
class MemoryStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun memoryDir(workspaceRoot: File): File = File(context.filesDir, "memory/${workspaceRoot.path.hashCode().toHexString()}")

        fun memoryFile(workspaceRoot: File): File = File(memoryDir(workspaceRoot), "MEMORY.md")

        fun read(workspaceRoot: File): String? = memoryFile(workspaceRoot).takeIf { it.isFile }?.readText(Charsets.UTF_8)

        fun write(
            workspaceRoot: File,
            content: String,
        ) {
            memoryDir(workspaceRoot).mkdirs()
            memoryFile(workspaceRoot).writeText(content, Charsets.UTF_8)
        }

        private fun Int.toHexString(): String = "%08x".format(this)
    }
