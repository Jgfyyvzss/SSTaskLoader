package com.soaringscoring.taskloader.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client for https://soaringscoring.com/api/v1/public
 *
 * Contests / classes / entrants need no key.
 * Tasks endpoints (list + file download) need a `tasks:read` API key,
 * sent as `Authorization: Bearer <key>`.
 */
class SoaringScoringApi(
    private val baseUrl: String = "https://soaringscoring.com/api/v1/public"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // The task-list endpoint is explicitly documented as slow on large
        // contests (walks every day/class/handicap), so give it more room.
        .readTimeout(45, TimeUnit.SECONDS)
        .apply {
            if (com.soaringscoring.taskloader.BuildConfig.DEBUG) {
                // Debug builds only — prints request/response headers (including
                // the Authorization header) to Logcat under the "OkHttp" tag, so
                // we can see exactly what's being sent while troubleshooting.
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
        }
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getContests(apiKey: String? = null): ApiResult<List<Contest>> =
        get("$baseUrl/contests", apiKey) { body ->
            json.decodeFromString(ContestsResponse.serializer(), body).contests
        }

    suspend fun getClasses(contestId: String, apiKey: String? = null): ApiResult<List<ContestClass>> =
        get("$baseUrl/contests/$contestId/classes", apiKey) { body ->
            json.decodeFromString(ClassesResponse.serializer(), body).classes
        }

    suspend fun getTasks(contestId: String, apiKey: String): ApiResult<TasksResponse> =
        get("$baseUrl/contests/$contestId/tasks", apiKey) { body ->
            json.decodeFromString(TasksResponse.serializer(), body)
        }

    /** [relativeOrAbsoluteUrl] is one of the `files.*` URLs returned by getTasks(). */
    suspend fun downloadTaskFile(relativeOrAbsoluteUrl: String, apiKey: String): ApiResult<ByteArray> {
        val url = if (relativeOrAbsoluteUrl.startsWith("http")) {
            relativeOrAbsoluteUrl
        } else {
            "https://soaringscoring.com$relativeOrAbsoluteUrl"
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@use failureFrom(resp.code, resp.body?.string())
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes == null) {
                        ApiResult.Failure("Empty response body", resp.code)
                    } else {
                        ApiResult.Success(bytes)
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure("Network error: ${e.message}")
            }
        }
    }

    /**
     * Uploads an IGC flight log. [localPart] is the pilot's own
     * {competitionNumber}-{contestKey} entry address; [apiKey] is the pilot's own
     * personal key with the `flights:write` scope - both distinct from the app's
     * built-in `tasks:read` key used everywhere else in this client.
     */
    suspend fun uploadFlight(
        localPart: String,
        apiKey: String,
        igcBytes: ByteArray,
        filename: String
    ): ApiResult<UploadResult> = withContext(Dispatchers.IO) {
        try {
            val body = igcBytes.toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/entries/$localPart/igc")
                .header("Authorization", "Bearer $apiKey")
                .header("X-Igc-Filename", filename)
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val bodyString = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use failureFrom(resp.code, bodyString)
                }
                try {
                    ApiResult.Success(json.decodeFromString(UploadResponse.serializer(), bodyString).upload)
                } catch (e: Exception) {
                    ApiResult.Failure("Could not parse response: ${e.message}", resp.code)
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure("Network error: ${e.message}")
        }
    }

    private suspend fun <T> get(
        url: String,
        apiKey: String? = null,
        parse: (String) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url)
            if (apiKey != null) {
                builder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(builder.build()).execute().use { resp ->
                val bodyString = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use failureFrom(resp.code, bodyString)
                }
                try {
                    ApiResult.Success(parse(bodyString))
                } catch (e: Exception) {
                    ApiResult.Failure("Could not parse response: ${e.message}", resp.code)
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure("Network error: ${e.message}")
        }
    }

    private fun failureFrom(httpCode: Int, body: String?): ApiResult.Failure {
        if (body.isNullOrBlank()) {
            return ApiResult.Failure("HTTP $httpCode", httpCode)
        }
        return try {
            val envelope = json.decodeFromString(ApiErrorEnvelope.serializer(), body)
            val element = envelope.error
            when {
                element == null -> ApiResult.Failure("HTTP $httpCode", httpCode)
                // {"error": "Contest not found."}
                element.jsonPrimitive.isString -> ApiResult.Failure(element.jsonPrimitive.content, httpCode)
                else -> {
                    // {"error": {"code": "...", "message": "..."}}
                    val obj = element.jsonObject
                    val message = obj["message"]?.jsonPrimitive?.content ?: "HTTP $httpCode"
                    val code = obj["code"]?.jsonPrimitive?.content
                    ApiResult.Failure(message, httpCode, code)
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure("HTTP $httpCode", httpCode)
        }
    }
}
