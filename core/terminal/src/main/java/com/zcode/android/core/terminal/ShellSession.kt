package com.zcode.android.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// One interactive shell process attached to a PTY. Writes are queued so keyboard
// callbacks never block; reads block on Dispatchers.IO until output arrives.
class ShellSession internal constructor(
    val pid: Int,
    private val masterFd: Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile
    private var closed = false

    val isClosed: Boolean
        get() = closed

    init {
        scope.launch {
            for (bytes in writes) {
                PtyChannel.write(masterFd, bytes)
            }
        }
    }

    // Reads the next chunk of PTY output into the buffer. Returns the number of
    // bytes read, or -1 once the child has exited (EIO on the master is the normal
    // end of a session). Other failures throw.
    suspend fun read(buffer: ByteArray): Int =
        withContext(Dispatchers.IO) {
            val count = PtyChannel.read(masterFd, buffer)
            when {
                count > 0 -> count
                count == 0 -> 0
                count == -EIO -> -1
                else -> throw IOException("pty read failed with errno ${-count}")
            }
        }

    // Sends bytes to the shell without blocking the caller.
    fun write(bytes: ByteArray) {
        if (!closed) {
            writes.trySend(bytes)
        }
    }

    fun resize(
        rows: Int,
        columns: Int,
    ) {
        if (!closed) {
            scope.launch {
                PtyChannel.resize(masterFd, rows, columns)
            }
        }
    }

    // Kills the shell and releases the PTY. Pending reads fail afterwards.
    fun close() {
        if (closed) {
            return
        }
        closed = true
        PtyChannel.killProcess(pid, SIGKILL)
        PtyChannel.closeFd(masterFd)
        writes.close()
        scope.cancel()
    }

    // Blocks until the child exits and returns its exit code.
    suspend fun awaitExit(): Int =
        withContext(Dispatchers.IO) {
            PtyChannel.waitFor(pid)
        }

    companion object {
        private const val EIO = 5
        private const val SIGKILL = 9

        // Starts an interactive mksh session in the given directory.
        fun start(
            workingDirectory: String,
            homeDirectory: String,
            tmpDirectory: String,
            nativeLibraryDir: String,
            rows: Int = 24,
            columns: Int = 80,
        ): ShellSession {
            val result =
                PtyChannel.forkExec(
                    cmd = arrayOf("/system/bin/sh", "-i"),
                    cwd = workingDirectory,
                    env = buildEnvironment(homeDirectory, tmpDirectory, nativeLibraryDir, term = "xterm-256color"),
                    rows = rows,
                    cols = columns,
                )
            return ShellSession(pid = result[0], masterFd = result[1])
        }

        // Starts a one-shot shell command; the caller reads the combined output.
        fun startNonInteractive(
            command: String,
            workingDirectory: String,
            environment: Array<String>,
        ): ShellSession {
            val result =
                PtyChannel.forkExec(
                    cmd = arrayOf("/system/bin/sh", "-c", command),
                    cwd = workingDirectory,
                    env = environment,
                    rows = 24,
                    cols = 80,
                )
            return ShellSession(pid = result[0], masterFd = result[1])
        }

        fun buildEnvironment(
            homeDirectory: String,
            tmpDirectory: String,
            nativeLibraryDir: String,
            term: String,
        ): Array<String> =
            arrayOf(
                "PATH=/system/bin:/system/xbin:$nativeLibraryDir",
                "HOME=$homeDirectory",
                "TMPDIR=$tmpDirectory",
                "TERM=$term",
            )
    }
}
