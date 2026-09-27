package com.zcode.android.core.agent

import android.content.Context
import com.zcode.android.core.terminal.ExecService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject

// One configured hook: a shell command triggered by an event, optionally filtered
// by a matcher over the event subject (usually the tool name).
data class HookDefinition(
    val event: String,
    val matcher: String,
    val command: String,
)

data class HookConfig(
    val enabled: Boolean = true,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    val definitions: List<HookDefinition> = emptyList(),
) {
    fun forEvent(event: String): List<HookDefinition> = definitions.filter { it.event == event }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val DEFAULT_MAX_OUTPUT_BYTES = 32_768
    }
}

// Result of running every hook for one event. blocked means the action must be
// refused and reason should reach the model.
data class HookOutcome(
    val blocked: Boolean = false,
    val reason: String? = null,
    val additionalContext: String? = null,
)

// Loads hook configuration from the user and workspace config files and runs the
// hooks for an event through the terminal subsystem. Wire protocol per event:
// one JSON line on stdin, exit code 0 for ok, 2 for block; optional JSON output
// with block, reason, and additionalContext fields.
class HookRunner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exec: ExecService,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun loadConfig(workspaceRoot: File?): HookConfig {
            val userConfig = File(context.filesDir, "config/cli/config.json")
            val workspaceConfig = workspaceRoot?.let { File(it, ".zcode/config.json") }
            var config = HookConfig()
            listOf(userConfig, workspaceConfig).filterNotNull().forEach { file ->
                if (file.isFile) {
                    config = merge(config, parse(file))
                }
            }
            return config
        }

        suspend fun run(
            event: String,
            subject: String,
            payload: JsonObject,
            config: HookConfig,
            shellEnvironment: Array<String>,
            workingDirectory: String,
        ): HookOutcome {
            if (!config.enabled) {
                return HookOutcome()
            }
            var outcome = HookOutcome()
            for (definition in config.forEvent(event)) {
                if (!matches(definition.matcher, subject)) {
                    continue
                }
                val stdin = buildStdin(event, subject, payload)
                val script = PIPE_PREFIX + stdin + PIPE_SUFFIX + shellQuote(definition.command)
                val result =
                    exec.exec(
                        command = script,
                        workingDirectory = workingDirectory,
                        environment = shellEnvironment,
                        timeoutMs = config.timeoutMs,
                        maxOutputBytes = config.maxOutputBytes,
                    )
                val stdout = result.output
                when (result.exitCode) {
                    EXIT_OK -> {
                        val parsed = parseOutput(stdout)
                        outcome =
                            if (parsed.additionalContext != null && outcome.additionalContext == null) {
                                outcome.copy(additionalContext = parsed.additionalContext)
                            } else {
                                outcome
                            }
                    }

                    EXIT_BLOCK -> {
                        val parsed = parseOutput(stdout)
                        return HookOutcome(
                            blocked = true,
                            reason = parsed.reason ?: stdout.take(REASON_CHARS).ifBlank { "blocked by $event hook" },
                            additionalContext = outcome.additionalContext,
                        )
                    }

                    else -> {}
                }
            }
            return outcome
        }

        private fun parseOutput(stdout: String): HookOutcome {
            val trimmed = stdout.trim()
            if (!trimmed.startsWith("{")) {
                return HookOutcome()
            }
            val root =
                runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
                    ?: return HookOutcome()
            val blocked = (root["block"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            val reason = (root["reason"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val context = (root["additionalContext"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            return HookOutcome(blocked = blocked, reason = if (blocked) reason else null, additionalContext = context)
        }

        private fun matches(
            matcher: String,
            subject: String,
        ): Boolean =
            when {
                matcher.isEmpty() || matcher == "*" -> true
                else -> matcher.split("|").any { it == subject }
            }

        private fun buildStdin(
            event: String,
            subject: String,
            payload: JsonObject,
        ): String =
            buildJsonObject {
                put("event", event)
                put("subject", subject)
                put("payload", payload)
            }.toString() + "\n"

        private fun parse(file: File): HookConfig {
            val root =
                runCatching { json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject }.getOrNull()
                    ?: return HookConfig()
            val section = (root["hooks"] as? JsonObject) ?: return HookConfig()
            val enabled = (section["enabled"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true
            val timeoutMs =
                (section["timeoutMs"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                    ?: HookConfig.DEFAULT_TIMEOUT_MS
            val maxOutputBytes =
                (section["maxOutputBytes"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                    ?: HookConfig.DEFAULT_MAX_OUTPUT_BYTES
            val events = section["events"] as? JsonObject ?: JsonObject(emptyMap())
            val definitions =
                events.entries.flatMap { (event, value) ->
                    (value as? kotlinx.serialization.json.JsonArray)
                        ?.flatMap { entry ->
                            val entryObject = entry as? JsonObject ?: return@flatMap emptyList()
                            val matcher = (entryObject["matcher"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
                            val hooks = entryObject["hooks"] as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
                            hooks.mapNotNull { hook ->
                                val hookObject = hook as? JsonObject ?: return@mapNotNull null
                                val command =
                                    (hookObject["command"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                                        ?: return@mapNotNull null
                                HookDefinition(event = event, matcher = matcher, command = command)
                            }
                        }.orEmpty()
                }
            return HookConfig(enabled = enabled, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes, definitions = definitions)
        }

        private fun merge(
            base: HookConfig,
            next: HookConfig,
        ): HookConfig =
            HookConfig(
                enabled = base.enabled && next.enabled,
                timeoutMs = next.timeoutMs,
                maxOutputBytes = next.maxOutputBytes,
                definitions = base.definitions + next.definitions,
            )

        private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        private companion object {
            const val EXIT_OK = 0
            const val EXIT_BLOCK = 2
            const val REASON_CHARS = 300

            // The hook reads its payload from stdin via a heredoc, which keeps the
            // one-shot PTY execution non interactive.
            const val PIPE_PREFIX = "cat <<'ZCODE_HOOK_EOF'\n"
            const val PIPE_SUFFIX = "\nZCODE_HOOK_EOF\n| sh -c "
        }
    }
