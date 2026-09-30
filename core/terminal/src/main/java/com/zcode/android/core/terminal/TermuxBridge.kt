package com.zcode.android.core.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Optional backend that runs commands in the Termux userland (bash, git, node)
// through the RUN_COMMAND intent. Requires the com.termux.permission.RUN_COMMAND
// permission granted at runtime and allow-external-apps=true in the Termux
// properties on the user side.
class TermuxBridge(
    private val context: Context,
) : ExecBackend {
    fun isInstalled(): Boolean = runCatching { context.packageManager.getPackageInfo(PACKAGE_NAME, 0) }.isSuccess

    fun isReady(): Boolean = isInstalled() && isPermissionGranted()

    fun isPermissionGranted(): Boolean = context.checkSelfPermission(PERMISSION_NAME) == PackageManager.PERMISSION_GRANTED

    // Runs one command with the Termux userland. The command string is executed
    // by the Termux shell; the combined output follows the ExecResult contract
    // with truncation to the limits documented for RUN_COMMAND results.
    override suspend fun exec(
        command: String,
        workingDirectory: String?,
        environment: Array<String>,
        timeoutMs: Long,
        maxOutputBytes: Int,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            if (!isReady()) {
                return@withContext ExecResult(
                    exitCode = -1,
                    output = "Termux is not ready: install the app, grant the RUN_COMMAND permission, and enable allow-external-apps.",
                    timedOut = false,
                    truncated = false,
                )
            }
            val resultDeferred = CompletableDeferred<Bundle>()
            val action = "zcode.android.TERMUX_RESULT." + System.currentTimeMillis()
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        receiverContext: Context,
                        intent: Intent,
                    ) {
                        val bundle = intent.getBundleExtra(RESULT_EXTRA)
                        if (bundle != null) {
                            resultDeferred.complete(bundle)
                        } else {
                            resultDeferred.complete(Bundle())
                        }
                    }
                }
            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            try {
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        action.hashCode(),
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_MUTABLE,
                    )
                val intent =
                    Intent().also {
                        it.setClassName(PACKAGE_NAME, SERVICE_NAME)
                        it.setAction(RUN_COMMAND_ACTION)
                        it.putExtra(EXTRA_EXECUTABLE, "$PREFIX/bin/sh")
                        it.putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
                        workingDirectory?.let { directory -> it.putExtra(EXTRA_WORKDIR, directory) }
                        it.putExtra(EXTRA_BACKGROUND, true)
                        it.putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                    }
                context.startService(intent)
                val bundle = withTimeoutOrNull(timeoutMs) { resultDeferred.await() }
                if (bundle == null) {
                    return@withContext ExecResult(
                        exitCode = -1,
                        output = "[command timed out after ${timeoutMs}ms]",
                        timedOut = true,
                        truncated = false,
                    )
                }
                val stdout = bundle.getString(EXTRA_RESULT_STDOUT).orEmpty()
                val stderr = bundle.getString(EXTRA_RESULT_STDERR).orEmpty()
                val exitCode = bundle.getInt(EXTRA_RESULT_EXIT_CODE, -1)
                val combined = (stdout + stderr).take(maxOutputBytes)
                ExecResult(
                    exitCode = exitCode,
                    output = combined,
                    timedOut = false,
                    truncated = stdout.length + stderr.length > combined.length,
                )
            } catch (t: Throwable) {
                ExecResult(exitCode = -1, output = "Termux execution failed: ${t.message}", timedOut = false, truncated = false)
            } finally {
                context.unregisterReceiver(receiver)
            }
        }

    private companion object {
        const val PACKAGE_NAME = "com.termux"
        const val SERVICE_NAME = "com.termux.app.RunCommandService"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val PERMISSION_NAME = "com.termux.permission.RUN_COMMAND"
        const val PREFIX = "/data/data/com.termux/files/usr"
        const val EXTRA_EXECUTABLE = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        const val RESULT_EXTRA = "termux.run_command.result"
        const val EXTRA_RESULT_STDOUT = "stdout"
        const val EXTRA_RESULT_STDERR = "stderr"
        const val EXTRA_RESULT_EXIT_CODE = "exit_code"
    }
}
