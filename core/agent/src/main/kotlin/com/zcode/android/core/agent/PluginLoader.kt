package com.zcode.android.core.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject

// Manifest of an installed plugin, read from .zcode-plugin/plugin.json.
data class PluginManifest(
    val name: String,
    val version: String,
    val description: String,
)

// Installs and manages plugins in the ZCode format. Components (commands,
// skills, agents) are copied into the user config directories, where the
// existing loaders pick them up. Sources in v1: GitHub repositories (zip
// download) and local directories; the official CDN with sha256 verification
// stays on the roadmap.
class PluginLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val httpClient: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        private val pluginsDir: File
            get() = File(context.filesDir, "plugins")

        fun installed(): List<PluginManifest> =
            pluginsDir
                .listFiles { file -> file.isDirectory }
                .orEmpty()
                .mapNotNull { folder -> readManifest(folder) }

        fun pluginDir(name: String): File = File(pluginsDir, name)

        // Downloads the repository zip archive, locates the plugin root (the
        // first directory containing a plugin manifest), and installs it.
        fun installFromGitHub(
            repo: String,
            ref: String = "main",
        ): PluginManifest {
            val url = "https://codeload.github.com/$repo/zip/refs/heads/$ref"
            val request = Request.Builder().url(url).build()
            val archive =
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("plugin download failed with HTTP ${response.code}")
                    }
                    response.body?.bytes() ?: throw IOException("empty plugin archive")
                }
            val staging = File(context.cacheDir, "plugin-" + System.currentTimeMillis())
            staging.mkdirs()
            try {
                extractZip(archive, staging)
                val root = findPluginRoot(staging) ?: throw IOException("no plugin manifest found in $repo")
                return installFromDirectory(root)
            } finally {
                staging.deleteRecursively()
            }
        }

        fun installFromDirectory(source: File): PluginManifest {
            val manifest = readManifest(source) ?: throw IOException("missing plugin manifest in ${source.path}")
            val target = pluginDir(manifest.name)
            target.deleteRecursively()
            source.copyRecursively(target, overwrite = true)
            copyComponents(manifest.name)
            return manifest
        }

        fun uninstall(name: String) {
            pluginDir(name).deleteRecursively()
        }

        // Resolves plugin variables in hook commands and similar values.
        fun resolveVariables(
            value: String,
            pluginName: String,
        ): String =
            value
                .replace("\${ZCODE_PLUGIN_ROOT}", pluginDir(pluginName).path)
                .replace("\${ZCODE_PLUGIN_DATA}", File(context.filesDir, "plugin-data/$pluginName").path)

        // Copies command, skill, and agent files into the user config scopes so
        // the existing loaders discover them.
        private fun copyComponents(pluginName: String) {
            val root = pluginDir(pluginName)
            val componentMap =
                mapOf(
                    "commands" to File(context.filesDir, "config/commands/$pluginName"),
                    "skills" to File(context.filesDir, "config/skills/$pluginName"),
                    "agents" to File(context.filesDir, "config/agents/$pluginName"),
                )
            componentMap.forEach { (component, target) ->
                val source = File(root, component)
                if (source.isDirectory && !target.exists()) {
                    source.copyRecursively(target, overwrite = false)
                }
            }
        }

        private fun readManifest(pluginRoot: File): PluginManifest? {
            val manifestFile =
                sequenceOf(File(pluginRoot, ".zcode-plugin/plugin.json"), File(pluginRoot, ".claude-plugin/plugin.json"))
                    .firstOrNull { it.isFile }
                    ?: return null
            val root =
                runCatching { json.parseToJsonElement(manifestFile.readText(Charsets.UTF_8)) }.getOrNull() as? JsonObject
                    ?: return null
            val name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: return null
            return PluginManifest(
                name = name,
                version = (root["version"] as? JsonPrimitive)?.contentOrNull ?: "0.0.0",
                description = (root["description"] as? JsonPrimitive)?.contentOrNull ?: "",
            )
        }

        private fun findPluginRoot(staging: File): File? {
            if (readManifest(staging) != null) {
                return staging
            }
            return staging
                .walkTopDown()
                .firstOrNull { file -> file.isDirectory && readManifest(file) != null }
        }

        private fun extractZip(
            archive: ByteArray,
            target: File,
        ) {
            ZipInputStream(archive.inputStream()).use { stream ->
                while (true) {
                    val entry = stream.nextEntry ?: break
                    val file = File(target, entry.name)
                    if (!file.canonicalPath.startsWith(target.canonicalPath)) {
                        throw IOException("zip entry escapes the staging directory")
                    }
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output -> stream.copyTo(output) }
                    }
                }
            }
        }
    }
