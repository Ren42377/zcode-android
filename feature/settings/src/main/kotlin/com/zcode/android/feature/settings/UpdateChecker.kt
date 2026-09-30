package com.zcode.android.feature.settings

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateResult(
    val tag: String?,
    val message: String?,
)

// Checks the GitHub Releases API for the newest published release of this app.
@Singleton
class UpdateChecker
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun latestRelease(): UpdateResult =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val request =
                        Request
                            .Builder()
                            .url(RELEASES_URL)
                            .header("Accept", "application/vnd.github+json")
                            .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@use UpdateResult(tag = null, message = "Update check failed with HTTP ${response.code}")
                        }
                        val body = response.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val tag = (root["tag_name"] as? JsonPrimitive)?.contentOrNull
                        UpdateResult(tag = tag, message = if (tag == null) "No releases published yet" else "Latest release: $tag")
                    }
                } catch (e: IOException) {
                    UpdateResult(tag = null, message = "Update check failed: ${e.message}")
                }
            }

        private companion object {
            const val RELEASES_URL = "https://api.github.com/repos/Ren42377/zcode-android/releases/latest"
        }
    }
