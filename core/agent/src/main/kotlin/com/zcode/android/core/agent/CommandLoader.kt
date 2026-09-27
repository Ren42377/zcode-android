package com.zcode.android.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

// One custom slash command in the ZCode markdown format: a file with a
// description frontmatter and a body template supporting $ARGUMENTS, $1 and $2.
data class CustomCommand(
    val name: String,
    val description: String,
    val argumentHint: String?,
    val template: String,
    val model: String?,
    val allowedTools: Set<String>?,
)

// Loads custom commands from the user scope (app storage) and the workspace
// scope (.zcode/commands). Subfolders become ": " namespaces, for example
// review/code.md becomes /review:code. Workspace commands win over user
// commands with the same name, matching ZCode precedence.
class CommandLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun load(workspaceRoot: File?): Map<String, CustomCommand> {
            val roots =
                buildList {
                    add(File(context.filesDir, "config/commands"))
                    if (workspaceRoot != null) {
                        add(File(workspaceRoot, ".zcode/commands"))
                    }
                }
            val merged = LinkedHashMap<String, CustomCommand>()
            roots.forEach { root ->
                scan(root, namespace = null).forEach { command ->
                    merged[command.name] = command
                }
            }
            return merged
        }

        private fun scan(
            root: File,
            namespace: String?,
        ): List<CustomCommand> {
            if (!root.isDirectory) {
                return emptyList()
            }
            val commands = mutableListOf<CustomCommand>()
            root.listFiles()?.forEach { entry ->
                when {
                    entry.isFile && entry.name.endsWith(".md") -> {
                        val baseName = entry.name.removeSuffix(".md")
                        val fullName = if (namespace.isNullOrEmpty()) baseName else "$namespace:$baseName"
                        parse(fullName, entry)?.let { commands.add(it) }
                    }

                    entry.isDirectory -> {
                        val childNamespace =
                            if (namespace.isNullOrEmpty()) {
                                entry.name
                            } else {
                                "$namespace:${entry.name}"
                            }
                        commands.addAll(scan(entry, childNamespace))
                    }
                }
            }
            return commands
        }

        private fun parse(
            name: String,
            file: File,
        ): CustomCommand? {
            if (!name.matches(Regex("^[a-z0-9][a-z0-9_:-]{0,63}$"))) {
                return null
            }
            val content = file.readText(Charsets.UTF_8)
            if (!content.startsWith("---")) {
                return null
            }
            val end = content.indexOf("---", 3)
            if (end < 0) {
                return null
            }
            var description: String? = null
            var argumentHint: String? = null
            var model: String? = null
            var allowedTools: Set<String>? = null
            content.substring(3, end).lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("description:") ->
                        description = trimmed.removePrefix("description:").trim().trim('"')

                    trimmed.startsWith("argument-hint:") ->
                        argumentHint = trimmed.removePrefix("argument-hint:").trim().trim('"')

                    trimmed.startsWith("model:") ->
                        model = trimmed.removePrefix("model:").trim()

                    trimmed.startsWith("allowed-tools:") ->
                        allowedTools =
                            trimmed.removePrefix("allowed-tools:")
                                .trim()
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()
                }
            }
            description ?: return null
            val template = content.substring(end + 3).trim()
            return CustomCommand(
                name = name,
                description = description.orEmpty(),
                argumentHint = argumentHint,
                template = template,
                model = model,
                allowedTools = allowedTools,
            )
        }
    }
