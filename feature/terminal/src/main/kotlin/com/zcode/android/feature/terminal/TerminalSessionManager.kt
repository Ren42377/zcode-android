package com.zcode.android.feature.terminal

import android.content.Context
import com.zcode.android.core.designsystem.ZcodeColors
import com.zcode.android.core.terminal.ShellSession
import dagger.hilt.android.qualifiers.ApplicationContext
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Holds the app-scoped terminal session so it survives screen changes. The
// session starts on first use and keeps running until the process dies.
@Singleton
class TerminalSessionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class ActiveTerminal(
            val session: ShellSession,
            val emulator: TerminalEmulator,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var active: ActiveTerminal? = null

        @Synchronized
        fun acquire(): ActiveTerminal {
            active?.let {
                return it
            }

            val home = context.filesDir
            val workspace =
                File(home, "workspace").apply { mkdirs() }
            val session =
                ShellSession.start(
                    workingDirectory = workspace.path,
                    homeDirectory = home.path,
                    tmpDirectory = context.cacheDir.path,
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                )
            val emulator =
                TerminalEmulatorFactory.create(
                    defaultForeground = ZcodeColors.text,
                    defaultBackground = ZcodeColors.bg,
                    onKeyboardInput = { bytes -> session.write(bytes) },
                    onResize = { dimensions -> session.resize(dimensions.rows, dimensions.columns) },
                )
            val terminal = ActiveTerminal(session = session, emulator = emulator)
            active = terminal

            scope.launch {
                val buffer = ByteArray(READ_BUFFER_BYTES)
                try {
                    while (!session.isClosed) {
                        val count = session.read(buffer)
                        if (count <= 0) {
                            break
                        }
                        emulator.writeInput(buffer, 0, count)
                    }
                } catch (_: IOException) {
                    // The session ended.
                }
            }
            return terminal
        }

        private companion object {
            const val READ_BUFFER_BYTES = 8 * 1024
        }
    }
