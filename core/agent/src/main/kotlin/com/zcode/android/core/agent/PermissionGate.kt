package com.zcode.android.core.agent

import com.zcode.android.core.storage.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface PermissionDecision {
    data object Allow : PermissionDecision

    data object Ask : PermissionDecision

    data object Deny : PermissionDecision
}

// Tools that never modify the workspace and never need approval.
private val READ_ONLY_TOOLS = setOf("Read", "Glob", "Grep", "TodoWrite", "WebFetch", "WebSearch")

// Maps the permission mode plus stored Always decisions to a decision per call.
class PermissionGate
    @Inject
    constructor(
        private val preferences: UserPreferences,
    ) {
        suspend fun decide(
            toolName: String,
            mode: PermissionMode,
        ): PermissionDecision {
            if (toolName in READ_ONLY_TOOLS) {
                return PermissionDecision.Allow
            }
            if (mode == PermissionMode.YOLO) {
                return PermissionDecision.Allow
            }
            if (mode == PermissionMode.PLAN) {
                return PermissionDecision.Deny
            }
            if (toolName in preferences.alwaysAllowedTools.first()) {
                return PermissionDecision.Allow
            }
            return when (mode) {
                PermissionMode.EDIT -> if (toolName == "Bash") PermissionDecision.Ask else PermissionDecision.Allow
                else -> PermissionDecision.Ask
            }
        }

        suspend fun allowAlways(toolName: String) {
            preferences.addAlwaysAllowedTool(toolName)
        }

        suspend fun denyAlways(toolName: String) {
            preferences.removeAlwaysAllowedTool(toolName)
        }
    }
