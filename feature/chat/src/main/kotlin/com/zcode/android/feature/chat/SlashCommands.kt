package com.zcode.android.feature.chat

// Built-in slash commands. Names match ZCode; commands whose subsystems land in
// later milestones appear here together with those subsystems.
data class SlashCommand(
    val name: String,
    val description: String,
)

object SlashCommands {
    val all: List<SlashCommand> =
        listOf(
            SlashCommand("help", "Show the available commands"),
            SlashCommand("model", "Open the model picker"),
            SlashCommand("mode", "Cycle the permission mode"),
            SlashCommand("effort", "Cycle the thinking effort"),
            SlashCommand("clear", "Start a fresh session"),
            SlashCommand("compact", "Summarize the conversation to reclaim context"),
            SlashCommand("goal", "Store a goal that stays in the agent context"),
            SlashCommand("init", "Create an AGENTS.md in the workspace"),
            SlashCommand("memory", "Show the project memory"),
            SlashCommand("mcp", "Show MCP server status"),
            SlashCommand("skill", "List the available skills"),
            SlashCommand("resume", "Open the session list"),
        )

    fun matching(prefix: String): List<SlashCommand> =
        if (prefix.isEmpty()) {
            all
        } else {
            all.filter { it.name.startsWith(prefix) }
        }
}
