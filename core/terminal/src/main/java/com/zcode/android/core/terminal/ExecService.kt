package com.zcode.android.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// Result of a one-shot command execution. exitCode follows shell conventions
// (128 + signal when the watchdog had to kill the command).
data class ExecResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean,
    val truncated: Boolean,
)

// Executes single commands on a PTY so behavior matches the interactive
// terminal. Used by the agent's Bash tool and by hooks.
class ExecService {
    suspend fun exec(
        command: String,
        workingDirectory: String,
        environment: Array<String>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val session = ShellSession.startNonInteractive(command, workingDirectory, environment)
            val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var timedOut = false
            val watchdog =
                watchdogScope.launch {
                    delay(timeoutMs)
                    timedOut = true
                    PtyChannel.killProcess(session.pid, SIGKILL)
                }
            val collected = StringBuilder()
            var truncated = false
            var collectedBytes = 0
            try {
                val buffer = ByteArray(READ_BUFFER_BYTES)
                while (!session.isClosed) {
                    val count =
                        try {
                            session.read(buffer)
                        } catch (_: IOException) {
                            break
                        }
                    if (count <= 0) {
                        break
                    }
                    if (collectedBytes < maxOutputBytes) {
                        val room = maxOutputBytes - collectedBytes
                        val kept = minOf(count, room)
                        collected.append(String(buffer, 0, kept, Charsets.UTF_8))
                        collectedBytes += kept
                        if (kept < count) {
                            truncated = true
                        }
                    } else {
                        truncated = true
                    }
                }
            } finally {
                watchdog.cancel()
                watchdogScope.cancel()
            }
            val exitCode = session.awaitExit()
            // The child is gone; close the PTY and stop the write loop.
            session.close()
            val suffix =
                buildString {
                    if (timedOut) {
                        append("[command timed out after ${timeoutMs}ms]")
                        append('\n')
                    }
                    if (truncated) {
                        append("[output truncated]")
                        append('\n')
                    }
                }
            ExecResult(
                exitCode = exitCode,
                output = collected.toString() + suffix,
                timedOut = timedOut,
                truncated = truncated,
            )
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
        const val DEFAULT_MAX_OUTPUT_BYTES = 256 * 1024
        private const val SIGKILL = 9
        private const val READ_BUFFER_BYTES = 8 * 1024
    }
}
