package com.zcode.android.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

data class Skill(
    val name: String,
    val description: String,
    val path: File,
)

// Loads skills in the ZCode format: a folder with a SKILL.md whose frontmatter
// carries name and description. User scope lives in app storage; workspace scope
// is read from .zcode/skills inside the workspace. A folder copied from desktop
// ZCode works unchanged (see PRD parity requirements).
class SkillLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun load(workspaceRoot: File?): List<Skill> {
            val roots =
                buildList {
                    add(File(context.filesDir, "config/skills"))
                    if (workspaceRoot != null) {
                        add(File(workspaceRoot, ".zcode/skills"))
                    }
                }
            return roots.flatMap { root -> scan(root) }.distinctBy { it.name }
        }

        fun readBody(skill: Skill): String? = skill.path.takeIf { it.isFile }?.readText(Charsets.UTF_8)

        private fun scan(root: File): List<Skill> {
            if (!root.isDirectory) {
                return emptyList()
            }
            return root
                .listFiles { file -> file.isDirectory }
                .orEmpty()
                .mapNotNull { folder ->
                    val skillFile = File(folder, "SKILL.md")
                    if (!skillFile.isFile) {
                        return@mapNotNull null
                    }
                    parse(skillFile)
                }
        }

        private fun parse(file: File): Skill? {
            val content = file.readText(Charsets.UTF_8)
            if (!content.startsWith(FRONT_MATTER_DELIMITER)) {
                return null
            }
            val end = content.indexOf(FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length)
            if (end < 0) {
                return null
            }
            val frontMatter = content.substring(FRONT_MATTER_DELIMITER.length, end)
            var name: String? = null
            var description: String? = null
            frontMatter.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("name:") -> name = trimmed.removePrefix("name:").trim().trim('"')
                    trimmed.startsWith("description:") -> description = trimmed.removePrefix("description:").trim().trim('"')
                }
            }
            name ?: return null
            return Skill(
                name = name.orEmpty(),
                description = description.orEmpty().take(MAX_DESCRIPTION_CHARS),
                path = file,
            )
        }

        private companion object {
            const val FRONT_MATTER_DELIMITER = "---"
            const val MAX_DESCRIPTION_CHARS = 1024
        }
    }
