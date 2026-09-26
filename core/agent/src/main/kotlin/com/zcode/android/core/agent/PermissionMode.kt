package com.zcode.android.core.agent

// The four ZCode permission modes. PLAN only reads, BUILD asks before changes,
// EDIT runs file edits automatically, YOLO runs everything without confirmation.
enum class PermissionMode(
    val label: String,
    val description: String,
) {
    PLAN("Plan", "Read only; the agent plans instead of changing anything"),
    BUILD("Build", "Ask before edits and commands"),
    EDIT("Edit", "Edits run automatically; commands still ask"),
    YOLO("Full access", "Everything runs without confirmation"),
}
