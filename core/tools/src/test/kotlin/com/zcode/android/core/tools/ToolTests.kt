package com.zcode.android.core.tools

import com.zcode.android.core.terminal.ExecService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

// Shared fixture for tool tests: a temporary workspace with plain file helpers.
abstract class ToolTestBase {
    lateinit var root: File
    lateinit var context: ToolContext

    @Before
    fun setUpWorkspace() {
        root = Files.createTempDirectory("zcode-tools").toFile()
        context =
            ToolContext(
                workspaceRoot = root,
                exec = ExecService(),
                shellEnvironment = emptyArray(),
                httpClient = OkHttpClient(),
                webSearchBaseUrl = null,
                webSearchApiKey = null,
            )
    }

    @After
    fun tearDownWorkspace() {
        root.deleteRecursively()
    }

    fun writeFile(
        relative: String,
        content: String,
    ): File {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    protected fun jsonObject(builder: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(builder)

    protected fun executeBlocking(
        tool: Tool,
        input: JsonObject,
    ): ToolOutcome =
        runBlocking {
            tool.execute(input = input, context = context)
        }
}

class GlobToolTest : ToolTestBase() {
    private val tool = GlobTool()

    @Test
    fun matchesNestedPatterns() {
        writeFile("a.kt", "x")
        writeFile("src/main/b.kt", "y")
        writeFile("src/c.txt", "z")
        val outcome =
            executeBlocking(
                tool,
                jsonObject { put("pattern", "**/*.kt") },
            )
        assertFalse(outcome.isError)
        assertEquals("a.kt\nsrc/main/b.kt", outcome.outputForModel)
    }

    @Test
    fun escapesLiteralDots() {
        writeFile("a.txt", "x")
        writeFile("atxt.bak", "y")
        val outcome =
            executeBlocking(
                tool,
                jsonObject { put("pattern", "a.txt") },
            )
        assertEquals("a.txt", outcome.outputForModel)
    }
}

class EditToolTest : ToolTestBase() {
    private val tool = EditTool()

    @Test
    fun replacesUniqueOccurrence() {
        writeFile("notes.md", "hello world\n")
        val outcome =
            executeBlocking(
                tool,
                jsonObject {
                    put("path", "notes.md")
                    put("old_string", "world")
                    put("new_string", "there")
                },
            )
        assertFalse(outcome.isError)
        assertEquals("hello there\n", File(root, "notes.md").readText())
    }

    @Test
    fun rejectsAmbiguousMatch() {
        writeFile("dup.txt", "ab ab")
        val outcome =
            executeBlocking(
                tool,
                jsonObject {
                    put("path", "dup.txt")
                    put("old_string", "ab")
                    put("new_string", "cd")
                },
            )
        assertTrue(outcome.isError)
    }

    @Test
    fun rejectsEscapeFromWorkspace() {
        val outcome =
            executeBlocking(
                tool,
                jsonObject {
                    put("path", "../outside.txt")
                    put("old_string", "a")
                    put("new_string", "b")
                },
            )
        assertTrue(outcome.isError)
    }
}

class ReadToolTest : ToolTestBase() {
    private val tool = ReadTool()

    @Test
    fun rendersLineNumbersAndOffsets() {
        writeFile("lines.txt", (1..15).joinToString(separator = "\n") { "line $it" })
        val outcome =
            executeBlocking(
                tool,
                jsonObject {
                    put("path", "lines.txt")
                    put("offset", 10)
                    put("limit", 3)
                },
            )
        val lines = outcome.outputForModel.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].trimStart().startsWith("10\tline 10"))
        assertTrue(outcome.outputForModel.contains("omitted"))
    }
}

class GrepToolTest : ToolTestBase() {
    private val tool = GrepTool()

    @Test
    fun findsMatchesAcrossFiles() {
        writeFile("one.txt", "alpha\nbeta\n")
        writeFile("sub/two.txt", "gamma alpha\n")
        val outcome =
            executeBlocking(
                tool,
                jsonObject { put("pattern", "alpha") },
            )
        assertFalse(outcome.isError)
        assertTrue(outcome.outputForModel.contains("one.txt:1: alpha"))
        assertTrue(outcome.outputForModel.contains("sub/two.txt:1: gamma alpha"))
    }
}
