package com.zcode.android.feature.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcode.android.core.storage.MessageDao
import com.zcode.android.core.storage.SessionDao
import com.zcode.android.core.storage.ToolEventDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val changed: Boolean,
)

data class FileView(
    val content: String,
    val truncated: Boolean,
)

// Workspace file explorer. Reads the app private workspace, marks files that the
// agent touched during the current session, and shows read only file contents.
@HiltViewModel
class WorkspaceViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val toolEventDao: ToolEventDao,
        private val messageDao: MessageDao,
        private val sessionDao: SessionDao,
    ) : ViewModel() {
        private val _entries = MutableStateFlow<List<FileNode>>(emptyList())
        val entries: StateFlow<List<FileNode>> = _entries.asStateFlow()

        private val _currentPath = MutableStateFlow("")
        val currentPath: StateFlow<String> = _currentPath.asStateFlow()

        private val _openFile = MutableStateFlow<Pair<String, FileView>?>(null)
        val openFile: StateFlow<Pair<String, FileView>?> = _openFile.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val workspace: File
            get() = File(appContext.filesDir, "workspace")

        fun load() {
            viewModelScope.launch {
                _entries.value = withContext(Dispatchers.IO) { listDirectory(_currentPath.value) }
            }
        }

        fun enter(path: String) {
            _currentPath.value = path
            load()
        }

        fun up() {
            val parent = File(_currentPath.value).parent.orEmpty()
            _currentPath.value = parent
            load()
        }

        fun open(path: String) {
            viewModelScope.launch {
                val file = File(workspace, path)
                if (!file.isFile) {
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
                _openFile.value = path to FileView(content = text.take(MAX_PREVIEW_CHARS), truncated = text.length > MAX_PREVIEW_CHARS)
            }
        }

        fun closeFile() {
            _openFile.value = null
        }

        fun delete(path: String) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val target = File(workspace, path)
                    if (target.isDirectory) target.deleteRecursively() else target.delete()
                }
                load()
            }
        }

        fun createDirectory(name: String) {
            viewModelScope.launch {
                val target = File(File(workspace, _currentPath.value), name)
                if (name.isBlank() || target.exists()) {
                    _error.value = "Name is empty or already exists"
                    return@launch
                }
                withContext(Dispatchers.IO) { target.mkdirs() }
                load()
            }
        }

        fun dismissError() {
            _error.value = null
        }

        private suspend fun listDirectory(relative: String): List<FileNode> {
            val directory = File(workspace, relative)
            if (!directory.isDirectory) {
                return emptyList()
            }
            val changedPaths = changedFilePaths()
            return directory
                .listFiles()
                .orEmpty()
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { file ->
                    val path = file.relativeTo(workspace).invariantSeparatorsPath
                    FileNode(
                        path = path,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        changed = changedPaths.any { it == path || it.startsWith("$path/") },
                    )
                }
        }

        // A file counts as changed when the agent read, wrote, or edited it during
        // the most recent session, which mirrors the ZCode change markers.
        private suspend fun changedFilePaths(): Set<String> {
            val sessionId = sessionDao.observeAll().first().firstOrNull()?.id ?: return emptySet()
            val fromTools =
                toolEventDao
                    .observe(sessionId)
                    .first()
                    .flatMap { event -> PATH_REGEX.findAll(event.output).map { it.value }.toList() }
            val fromMessages =
                messageDao
                    .list(sessionId)
                    .flatMap { message -> PATH_REGEX.findAll(message.content).map { it.value }.toList() }
            return (fromTools + fromMessages).toSet()
        }

        private companion object {
            const val MAX_PREVIEW_CHARS = 20_000
            val PATH_REGEX = Regex("[A-Za-z0-9_./-]+\\.(kt|kts|md|json|txt|gradle|xml|yml|yaml|toml|pro|properties)")
        }
    }
