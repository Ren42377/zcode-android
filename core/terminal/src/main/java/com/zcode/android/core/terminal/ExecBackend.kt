package com.zcode.android.core.terminal

// Common contract for command execution backends: the internal PTY service and
// the optional Termux bridge share this API so tools can switch between them.
interface ExecBackend {
    suspend fun exec(
        command: String,
        workingDirectory: String?,
        environment: Array<String>,
        timeoutMs: Long,
        maxOutputBytes: Int,
    ): ExecResult
}
