package com.zcode.android.core.tools

import com.zcode.android.core.terminal.ExecService
import com.zcode.android.core.terminal.ShellSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BashTool : Tool {
    override val name = "Bash"
    override val description =
        "Runs a shell command in the workspace via the built-in PTY (mksh, or the Termux userland when enabled) " +
            "and returns the combined output. Interactive programs are not supported here."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "command",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to run")
                        },
                    )
                    put(
                        "timeout_ms",
                        buildJsonObject {
                            put("type", "integer")
                            put("description", "Optional timeout in milliseconds; default 120000")
                        },
                    )
                },
            )
            put("required", requiredFields("command"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val command = input.stringField("command")
        if (command.isNullOrEmpty()) {
            return toolError("command is required")
        }
        val timeout = input.intField("timeout_ms")?.toLong()?.coerceIn(1_000L, MAX_TIMEOUT_MS)
        val result =
            context.exec.exec(
                command = command,
                workingDirectory = context.workspaceRoot.path,
                environment = context.shellEnvironment,
                timeoutMs = timeout ?: ExecService.DEFAULT_TIMEOUT_MS,
            )
        val prefix =
            if (result.exitCode != 0) {
                "exit code: ${result.exitCode}\n"
            } else {
                ""
            }
        return ToolOutcome(outputForModel = prefix + result.output)
    }

    companion object {
        // Builds the non interactive shell environment for command execution.
        fun buildEnvironment(
            homeDirectory: String,
            tmpDirectory: String,
            nativeLibraryDir: String,
        ): Array<String> =
            ShellSession.buildEnvironment(
                homeDirectory = homeDirectory,
                tmpDirectory = tmpDirectory,
                nativeLibraryDir = nativeLibraryDir,
                term = "dumb",
            )

        private const val MAX_TIMEOUT_MS = 600_000L
    }
}
