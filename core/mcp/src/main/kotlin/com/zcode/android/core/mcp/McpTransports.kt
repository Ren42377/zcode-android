package com.zcode.android.core.mcp

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Result of one JSON-RPC round trip: the result object when the call succeeded.
data class JsonRpcOutcome(
    val result: JsonObject?,
    val error: String?,
)

// Transport contract for the MCP client. Connects lazily and multiplexes
// requests by JSON-RPC id.
interface McpTransport {
    suspend fun connect()

    suspend fun request(method: String, params: JsonObject?): JsonRpcOutcome

    fun close()
}

const val MCP_PROTOCOL_VERSION = "2025-03-26"
const val MCP_CALL_TIMEOUT_MS = 30_000L

internal val json = Json { ignoreUnknownKeys = true }

// Builds a JSON-RPC request envelope and tracks the next id.
internal class JsonRpcWriter {
    private val counter = AtomicLong(1)

    fun envelope(method: String, params: JsonObject?): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", counter.getAndIncrement())
            put("method", method)
            if (params != null) {
                put("params", params)
            }
        }
}

// Parses one JSON-RPC response into result or error text.
internal fun parseResponse(root: JsonObject): JsonRpcOutcome {
    val error = root["error"] as? JsonObject
    if (error != null) {
        val message = (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "unknown error"
        return JsonRpcOutcome(result = null, error = message)
    }
    val result = root["result"] as? JsonObject ?: return JsonRpcOutcome(result = null, error = "missing result")
    return JsonRpcOutcome(result = result, error = null)
}

// stdio transport: the server runs as a child of the app shell and speaks
// newline delimited JSON-RPC over its standard streams.
class StdioMcpTransport(
    private val command: String,
    private val workingDirectory: File?,
) : McpTransport {
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private val pending = mutableMapOf<Long, CompletableDeferred<JsonObject>>()
    private val writer = JsonRpcWriter()

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val builder =
                ProcessBuilder("/system/bin/sh", "-c", command)
            workingDirectory?.let { builder.directory(it) }
            builder.redirectErrorStream(false)
            val started = builder.start()
            process = started
            stdin = started.outputStream
            startReader(started.inputStream)
        }
    }

    private fun startReader(stream: InputStream) {
        Thread {
            try {
                stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    handleLine(line.trim())
                }
            } catch (_: Exception) {
                // Stream ended; pending calls time out on their own.
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handleLine(line: String) {
        if (line.isEmpty()) {
            return
        }
        val root = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return
        val id = (root["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: return
        pending.remove(id)?.let { deferred ->
            (root["result"] as? JsonObject)?.let { deferred.complete(it) }
                ?: deferred.complete(JsonObject(emptyMap()))
        }
    }

    override suspend fun request(
        method: String,
        params: JsonObject?,
    ): JsonRpcOutcome =
        withContext(Dispatchers.IO) {
            val envelope = writer.envelope(method, params)
            val id = (envelope["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0
            val deferred = CompletableDeferred<JsonObject>()
            pending[id] = deferred
            stdin?.write((envelope.toString() + "\n").toByteArray(Charsets.UTF_8))
            stdin?.flush()
            val result = withTimeoutOrNull(MCP_CALL_TIMEOUT_MS) { deferred.await() }
            if (result == null) {
                pending.remove(id)
                JsonRpcOutcome(result = null, error = "MCP call timed out")
            } else {
                parseResponse(result)
            }
        }

    override fun close() {
        process?.destroy()
        process = null
        stdin = null
    }
}

// Streamable HTTP transport: JSON-RPC over POST with optional session header.
class HttpMcpTransport(
    private val url: String,
    private val headers: Map<String, String>,
    private val httpClient: OkHttpClient,
) : McpTransport {
    private val writer = JsonRpcWriter()
    private var sessionId: String? = null

    override suspend fun connect() {
        val outcome = request("initialize", initializeParams())
        if (outcome.error != null) {
            return
        }
        sessionId = lastSessionHeader
        request("notifications/initialized", null)
    }

    @Volatile
    private var lastSessionHeader: String? = null

    override suspend fun request(
        method: String,
        params: JsonObject?,
    ): JsonRpcOutcome =
        withContext(Dispatchers.IO) {
            val body = writer.envelope(method, params).toString().toRequestBody(JSON_MEDIA)
            val builder =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .post(body)
            headers.forEach { (name, value) -> builder.header(name, value) }
            sessionId?.let { builder.header("Mcp-Session-Id", it) }
            httpClient.newCall(builder.build()).execute().use { response ->
                lastSessionHeader = response.header("Mcp-Session-Id") ?: lastSessionHeader
                if (!response.isSuccessful) {
                    return@use JsonRpcOutcome(result = null, error = "HTTP ${response.code}")
                }
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                if (root == null) {
                    JsonRpcOutcome(result = null, error = "unreadable MCP response")
                } else {
                    parseResponse(root)
                }
            }
        }

    override fun close() {}

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

// Legacy HTTP plus SSE transport: an SSE stream carries responses while requests
// are POSTed to the endpoint announced by the server.
class SseMcpTransport(
    private val url: String,
    private val headers: Map<String, String>,
    private val httpClient: OkHttpClient,
) : McpTransport {
    private val writer = JsonRpcWriter()
    private val pending = mutableMapOf<Long, CompletableDeferred<JsonObject>>()
    private var endpointUrl: String? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("SSE connect failed with HTTP ${response.code}")
                }
                val source = response.body.source()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data.isNotEmpty() && endpointUrl == null) {
                            endpointUrl = resolveEndpoint(data)
                            break
                        }
                    }
                }
            }
            endpointUrl ?: throw IOException("no endpoint event received")
        }
    }

    private fun resolveEndpoint(data: String): String {
        if (data.startsWith("http://") || data.startsWith("https://")) {
            return data
        }
        return url.trimEnd('/').substringBeforeLast('/') + "/" + data.removePrefix("/")
    }

    override suspend fun request(
        method: String,
        params: JsonObject?,
    ): JsonRpcOutcome =
        withContext(Dispatchers.IO) {
            val target = endpointUrl ?: return@withContext JsonRpcOutcome(result = null, error = "not connected")
            val envelope = writer.envelope(method, params)
            val id = (envelope["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0
            val deferred = CompletableDeferred<JsonObject>()
            pending[id] = deferred
            startResponseReader()
            val body = envelope.toString().toRequestBody("application/json".toMediaType())
            val builder = Request.Builder().url(target).post(body)
            headers.forEach { (name, value) -> builder.header(name, value) }
            httpClient.newCall(builder.build()).execute().close()
            val result = withTimeoutOrNull(MCP_CALL_TIMEOUT_MS) { deferred.await() }
            if (result == null) {
                pending.remove(id)
                JsonRpcOutcome(result = null, error = "MCP call timed out")
            } else {
                parseResponse(result)
            }
        }

    // Responses for posted requests arrive on a separate SSE stream.
    private fun startResponseReader() {
        val target = endpointUrl ?: return
        Thread {
            try {
                val builder = Request.Builder().url(target).header("Accept", "text/event-stream").get()
                headers.forEach { (name, value) -> builder.header(name, value) }
                httpClient.newCall(builder.build()).execute().use { response ->
                    val source = response.body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) {
                            continue
                        }
                        val root = runCatching { json.parseToJsonElement(line.removePrefix("data:").trim()) }.getOrNull() as? JsonObject
                        val id = (root?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: continue
                        val result = root["result"] as? JsonObject
                        pending.remove(id)?.let { deferred ->
                            result?.let { deferred.complete(it) }
                        }
                    }
                }
            } catch (_: Exception) {
                // Reader ended; pending calls time out on their own.
            }
        }.apply { isDaemon = true }.start()
    }

    override fun close() {}
}

private fun initializeParams(): JsonObject =
    buildJsonObject {
        put("protocolVersion", MCP_PROTOCOL_VERSION)
        put(
            "capabilities",
            buildJsonObject {},
        )
        put(
            "clientInfo",
            buildJsonObject {
                put("name", "zcode-android")
                put("version", "1.0")
            },
        )
    }
