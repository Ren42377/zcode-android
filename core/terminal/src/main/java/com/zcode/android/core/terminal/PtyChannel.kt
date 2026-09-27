package com.zcode.android.core.terminal

import java.io.IOException

// JNI bridge to the PTY implementation in src/main/cpp/pty.c. Every method is
// blocking and must be called from a background dispatcher. Negative return
// values encode errno; read returns -EIO once the child has exited.
internal object PtyChannel {
    init {
        System.loadLibrary("zcodepty")
    }

    // Returns [pid, masterFd]; throws IOException on failure. The working
    // directory may be null to inherit the process default.
    external fun forkExec(
        cmd: Array<String>,
        cwd: String?,
        env: Array<String>,
        rows: Int,
        cols: Int,
    ): IntArray

    external fun read(
        fd: Int,
        buffer: ByteArray,
    ): Int

    external fun write(
        fd: Int,
        bytes: ByteArray,
    ): Int

    external fun resize(
        fd: Int,
        rows: Int,
        cols: Int,
    ): Int

    external fun closeFd(fd: Int): Int

    external fun killProcess(
        pid: Int,
        signal: Int,
    ): Int

    external fun waitFor(pid: Int): Int
}
