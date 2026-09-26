package com.zcode.android.core.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val FETCH_BODY_LIMIT_BYTES = 512 * 1024
private const val FETCH_OUTPUT_LIMIT_CHARS = 8_000

class WebFetchTool : Tool {
    override val name = "WebFetch"
    override val description =
        "Downloads a web page over HTTPS, converts it to plain text, and returns an excerpt for reading in place."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "url",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute https URL to fetch")
                        },
                    )
                },
            )
            put("required", requiredFields("url"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val url = input.stringField("url")
        if (url.isNullOrEmpty() || !url.startsWith("https://")) {
            return toolError("an absolute https URL is required")
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                context.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use toolError("HTTP ${response.code} for $url")
                    }
                    val body =
                        response.body
                            ?.byteStream()
                            ?.use { stream ->
                                val buffer = ByteArray(FETCH_BODY_LIMIT_BYTES)
                                val read = stream.read(buffer)
                                String(buffer, 0, if (read > 0) read else 0, Charsets.UTF_8)
                            }.orEmpty()
                    val contentType = response.header("Content-Type").orEmpty()
                    val text =
                        if (contentType.contains("html")) {
                            htmlToText(body)
                        } else {
                            body
                        }
                    val suffix =
                        if (text.length > FETCH_OUTPUT_LIMIT_CHARS) {
                            "\n[content truncated]"
                        } else {
                            ""
                        }
                    ToolOutcome(outputForModel = text.take(FETCH_OUTPUT_LIMIT_CHARS) + suffix)
                }
            } catch (e: IOException) {
                toolError("fetch failed: ${e.message}")
            }
        }
    }

    private fun htmlToText(html: String): String =
        html
            .replace(Regex("(?is)<(script|style).*?</(script|style)>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n\\s*\n+"), "\n\n")
            .trim()
}

data class WebSearchConfig(
    val baseUrl: String,
    val apiKey: String,
)

class WebSearchTool : Tool {
    override val name = "WebSearch"
    override val description =
        "Searches the web through the provider search API and returns the top results with titles, links, and snippets."
    override val parameters =
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "query",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Search query")
                        },
                    )
                },
            )
            put("required", requiredFields("query"))
        }

    override suspend fun execute(
        input: JsonObject,
        context: ToolContext,
    ): ToolOutcome {
        val query = input.stringField("query")
        if (query.isNullOrEmpty()) {
            return toolError("query is required")
        }
        val baseUrl = context.webSearchBaseUrl
        val apiKey = context.webSearchApiKey
        if (baseUrl.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            return toolError("web search is not configured")
        }
        return withContext(Dispatchers.IO) {
            try {
                val body =
                    buildJsonObject {
                        put("search_engine", SEARCH_ENGINE)
                        put("search_query", query)
                    }
                val request =
                    Request
                        .Builder()
                        .url(baseUrl.trimEnd('/') + "/web_search")
                        .header("Authorization", "Bearer $apiKey")
                        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                context.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use toolError("search failed with HTTP ${response.code}")
                    }
                    val text = response.body?.string().orEmpty()
                    ToolOutcome(outputForModel = formatResults(text))
                }
            } catch (e: IOException) {
                toolError("search failed: ${e.message}")
            }
        }
    }

    // The provider response shape varies between deployments; collect title, link,
    // and content fields from the first known array of results.
    private fun formatResults(payload: String): String {
        val root =
            runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull()
                ?: return "Search returned an unreadable response"
        val results =
            (root["search_result"] ?: root["data"]) as? JsonArray
                ?: return "Search returned no results"
        val lines =
            results
                .take(8)
                .mapIndexed { index, element ->
                    val item = element as? JsonObject ?: return@mapIndexed null
                    val title = (item["title"] as? JsonPrimitive)?.contentOrNull ?: ""
                    val link = (item["link"] ?: item["url"])?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
                    val snippet = (item["content"] as? JsonPrimitive)?.contentOrNull ?: ""
                    "${index + 1}. $title\n$link\n${snippet.take(300)}"
                }.filterNotNull()
        if (lines.isEmpty()) {
            return "Search returned no results"
        }
        return lines.joinToString(separator = "\n\n")
    }

    private companion object {
        const val SEARCH_ENGINE = "search-prime"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
