package com.bigshelf.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A failed API call; [message] is already human readable (FastAPI `detail`). */
class ApiException(val code: Int, message: String) : IOException(message)

/** Web `getErrorMessage`: the error's message, or [fallback] for non-API failures. */
fun Throwable.userMessage(fallback: String = "Request failed"): String =
    if (this is ApiException) message ?: fallback else "Something went wrong"

/**
 * HTTP client for the BigShelf backend - the Android port of frontend/src/lib/api.ts.
 *
 * Same auth model as the web app: a short-lived access token held in memory and sent as
 * `Authorization: Bearer`, renewed through the `refresh_token` cookie on a 401.
 */
class Api(context: Context, val baseUrl: String) {
    private val appContext = context.applicationContext
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val jsonType = "application/json".toMediaType()
    private val apiHost = baseUrl.toHttpUrl().host

    val cookieJar = PersistentCookieJar(appContext, baseUrl)

    @Volatile
    var accessToken: String? = null

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when a refresh fails mid-session, so the UI can return to Login. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    // No auth header: refresh calls and presigned S3 URLs (our header would break the signature)
    val bareClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Authenticated client; also used by Coil so face thumbnails served by the API load. */
    val client: OkHttpClient = bareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val token = accessToken
            if (token != null && request.url.host == apiHost && request.header("Authorization") == null) {
                chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
            } else {
                chain.proceed(request)
            }
        }
        .authenticator { _, response ->
            val request = response.request
            val path = request.url.encodedPath
            // Only our API, never login/signup/refresh themselves, and only one retry
            val authCall = path.startsWith("/api/v1/auth/") && !path.endsWith("/me")
            if (request.url.host != apiHost || authCall || response.priorResponse != null) {
                return@authenticator null
            }
            val token = refreshBlocking(request.header("Authorization")) ?: run {
                accessToken = null
                _sessionExpired.tryEmit(Unit)
                return@authenticator null
            }
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        .build()

    // One refresh at a time: parallel 401s wait and reuse the token the first one fetched
    @Synchronized
    private fun refreshBlocking(failedAuthHeader: String?): String? {
        val current = accessToken
        if (current != null && failedAuthHeader != "Bearer $current") return current
        val request = Request.Builder()
            .url(url("/api/v1/auth/refresh"))
            .post(ByteArray(0).toRequestBody(null))
            .build()
        return runCatching {
            bareClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                json.decodeFromString(TokenResponse.serializer(), res.body!!.string()).accessToken
            }
        }.getOrNull()?.also { accessToken = it }
    }

    /** Absolute URL for an API path or a relative media URL (e.g. `/api/v1/faces/{id}/thumbnail`). */
    fun url(path: String): String = if (path.startsWith("http")) path else baseUrl + path

    fun mediaUrl(path: String?): String? = path?.let(::url)

    // ---------- Request plumbing ----------

    private suspend fun call(
        method: String,
        path: String,
        params: Map<String, Any?> = emptyMap(),
        body: RequestBody? = null,
    ): String = withContext(Dispatchers.IO) {
        val httpUrl = url(path).toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
        }.build()
        val requestBody = body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(null)
        val request = Request.Builder().url(httpUrl).method(method, requestBody).build()
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, errorDetail(text) ?: "Request failed")
            text
        }
    }

    // FastAPI sends a string `detail` for HTTPException, an array of {msg} for 422s
    private fun errorDetail(text: String): String? = runCatching {
        val detail = json.parseToJsonElement(text).jsonObject["detail"] ?: return null
        if (detail is JsonArray) {
            detail.jsonArray.mapNotNull { it.jsonObject["msg"]?.jsonPrimitive?.content }.joinToString("; ")
        } else {
            detail.jsonPrimitive.content
        }
    }.getOrNull()?.ifBlank { null }

    private fun JsonElement.body() = json.encodeToString(JsonElement.serializer(), this).toRequestBody(jsonType)

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, params: Map<String, Any?> = emptyMap()): T =
        json.decodeFromString(serializer, call("GET", path, params))

    private suspend fun <T> send(
        method: String,
        path: String,
        serializer: KSerializer<T>,
        body: JsonElement? = null,
        params: Map<String, Any?> = emptyMap(),
    ): T = json.decodeFromString(serializer, call(method, path, params, body?.body()))

    private suspend fun sendUnit(method: String, path: String, body: JsonElement? = null, params: Map<String, Any?> = emptyMap()) {
        call(method, path, params, body?.body())
    }

    private fun ids(photoIds: Collection<String>) = JsonArray(photoIds.map(::JsonPrimitive))

    // ---------- Auth ----------

    suspend fun login(identifier: String, password: String) {
        val token = send("POST", "/api/v1/auth/login", TokenResponse.serializer(), buildJsonObject {
            put("identifier", identifier)
            put("password", password)
        })
        accessToken = token.accessToken
    }

    suspend fun signup(email: String, username: String, password: String) = sendUnit(
        "POST", "/api/v1/auth/signup",
        buildJsonObject {
            put("email", email)
            put("username", username)
            put("password", password)
        },
    )

    suspend fun me(): User = get("/api/v1/auth/me", User.serializer())

    suspend fun logout() {
        try {
            sendUnit("POST", "/api/v1/auth/logout")
        } finally {
            accessToken = null
            cookieJar.clear()
        }
    }

    // ---------- Photos ----------

    suspend fun photos(page: Int, favorite: Boolean? = null) =
        get("/api/v1/photos", PhotoPage.serializer(), mapOf("page" to page, "size" to PAGE_SIZE, "favorite" to favorite))

    suspend fun searchPhotos(q: String) =
        get("/api/v1/photos/search", PhotoPage.serializer(), mapOf("q" to q)).items

    suspend fun photo(id: String) = get("/api/v1/photos/$id", Photo.serializer())

    suspend fun bin(page: Int) = get("/api/v1/photos/bin", PhotoPage.serializer(), mapOf("page" to page, "size" to PAGE_SIZE))

    suspend fun emptyBin() = send("POST", "/api/v1/photos/bin/empty", CountResponse.serializer())

    suspend fun archiveFolders() = get("/api/v1/photos/archive/folders", ListSerializer(ArchiveFolder.serializer()))

    suspend fun archivePhotos(folder: String, page: Int) =
        get("/api/v1/photos/archive", PhotoPage.serializer(), mapOf("folder" to folder, "page" to page, "size" to PAGE_SIZE))

    suspend fun folderAction(folder: String, delete: Boolean) = send(
        "POST",
        if (delete) "/api/v1/photos/archive/folders/delete" else "/api/v1/photos/archive/folders/unarchive",
        CountResponse.serializer(),
        params = mapOf("folder" to folder),
    )

    suspend fun emptyArchive() = sendUnit("POST", "/api/v1/photos/archive/empty")

    suspend fun storage() = get("/api/v1/photos/storage", StorageUsage.serializer())

    suspend fun setFavorite(id: String, favorite: Boolean) =
        send("PATCH", "/api/v1/photos/$id", Photo.serializer(), buildJsonObject { put("is_favorite", favorite) })

    suspend fun markAi(id: String, isAi: Boolean) =
        send("PATCH", "/api/v1/photos/$id/ai", Photo.serializer(), params = mapOf("is_ai" to isAi))

    suspend fun rotate(id: String) =
        send("POST", "/api/v1/photos/$id/rotate", Photo.serializer(), params = mapOf("degrees" to 90))

    suspend fun deletePhoto(id: String) = sendUnit("DELETE", "/api/v1/photos/$id")

    suspend fun flagDeleted(photoIds: Collection<String>, deleted: Boolean) = sendUnit(
        "POST", "/api/v1/photos/flag-deleted",
        buildJsonObject {
            put("photo_ids", ids(photoIds))
            put("is_deleted", deleted)
        },
    )

    suspend fun flagArchived(photoIds: Collection<String>, archived: Boolean) = sendUnit(
        "POST", "/api/v1/photos/flag-archived",
        buildJsonObject {
            put("photo_ids", ids(photoIds))
            put("is_archived", archived)
        },
    )

    suspend fun permanentDelete(photoIds: Collection<String>) = sendUnit(
        "POST", "/api/v1/photos/permanent-delete",
        buildJsonObject { put("photo_ids", ids(photoIds)) },
    )

    // ---------- People & faces ----------

    suspend fun people() = get("/api/v1/people", PersonList.serializer())

    suspend fun personPhotos(id: String) = get("/api/v1/people/$id/photos", PersonPhotos.serializer())

    suspend fun renamePerson(id: String, name: String) =
        sendUnit("PATCH", "/api/v1/people/$id", buildJsonObject { put("name", name) })

    suspend fun untagPhotos(personId: String, photoIds: Collection<String>) = send(
        "POST", "/api/v1/people/$personId/untag-photos", UntagResponse.serializer(),
        buildJsonObject { put("photo_ids", ids(photoIds)) },
    )

    suspend fun untaggedPhotos() = get("/api/v1/people/untagged-photos", PhotoPage.serializer())

    suspend fun faces() = get("/api/v1/faces", FaceList.serializer()).faces

    suspend fun facePhotos(id: String) = get("/api/v1/faces/$id/photos", FacePhotos.serializer())

    suspend fun faceReview() = get("/api/v1/faces/confirmations", FaceReview.serializer())

    /** Submit one grid screen; the response is the next one. */
    suspend fun resolveFaceReview(personId: String, confirmed: Collection<String>, rejected: Collection<String>) = send(
        "POST", "/api/v1/faces/confirmations", FaceReview.serializer(),
        buildJsonObject {
            put("person_id", personId)
            put("confirmed_face_ids", ids(confirmed))
            put("rejected_face_ids", ids(rejected))
        },
    )

    /** "Different people": never suggest this pair again. */
    suspend fun markDistinct(personIds: Collection<String>) = send(
        "POST", "/api/v1/faces/confirmations/distinct", FaceReview.serializer(),
        buildJsonObject { put("person_ids", ids(personIds)) },
    )

    suspend fun mergePeople(sourceId: String, targetId: String) =
        sendUnit("POST", "/api/v1/people/$sourceId/merge", buildJsonObject { put("target_person_id", targetId) })

    // ---------- Albums ----------

    suspend fun albums() = get("/api/v1/albums", AlbumList.serializer(), mapOf("size" to 50)).items

    suspend fun addToAlbum(albumId: String, photoIds: Collection<String>) = sendUnit(
        "POST", "/api/v1/albums/$albumId/photos",
        buildJsonObject { put("photo_ids", ids(photoIds)) },
    )

    suspend fun createAlbum(title: String, photoIds: Collection<String>) = sendUnit(
        "POST", "/api/v1/albums",
        buildJsonObject {
            put("title", title)
            put("photo_ids", ids(photoIds))
        },
    )

    suspend fun prioritize(id: String) = send("POST", "/api/v1/photos/$id/priority", Photo.serializer())

    // ---------- Pipelines ----------

    suspend fun pipelines() = get("/api/v1/pipelines", PipelinesData.serializer())

    suspend fun startRun(pipelineId: String) = send("POST", "/api/v1/pipelines/$pipelineId/runs", Run.serializer())

    suspend fun run(runId: String) = get("/api/v1/pipelines/runs/$runId", Run.serializer())

    suspend fun cancelRun(runId: String) = sendUnit("POST", "/api/v1/pipelines/runs/$runId/cancel")

    // ---------- Duplicates & organize ----------

    suspend fun duplicates(limit: Int, threshold: Double) =
        get("/api/v1/photos/duplicates", DuplicatesResponse.serializer(), mapOf("limit" to limit, "threshold" to threshold))

    /** "Keep both": the backend stops pairing these photos. */
    suspend fun dismissDuplicate(group: DuplicateGroup) = sendUnit(
        "POST", "/api/v1/photos/duplicates/dismiss",
        buildJsonObject {
            put("photo_ids", ids(group.items.map { it.id }))
            put("group_key", group.key)
        },
    )

    suspend fun undismissDuplicate(groupKey: String) =
        sendUnit("DELETE", "/api/v1/photos/duplicates/dismiss", params = mapOf("group_key" to groupKey))

    suspend fun clearDismissed() = send("DELETE", "/api/v1/photos/duplicates/dismiss", ClearedResponse.serializer())

    suspend fun organize(limit: Int) =
        get("/api/v1/photos/organize", MapSerializer(String.serializer(), Bucket.serializer()), mapOf("limit" to limit))

    // ---------- System ----------

    suspend fun systemStats() = get("/api/v1/system/stats", SystemStats.serializer())

    suspend fun regenerateThumbnails() = send("POST", "/api/v1/system/regenerate-thumbnails", JobStatus.serializer())

    // ---------- Upload ----------

    /**
     * Same three steps as the web `useUpload`: reserve a job, PUT the file straight to
     * storage with the presigned URL, then tell the API it landed so Celery processes it.
     * [onProgress] gets bytes sent so far.
     */
    suspend fun upload(uri: Uri, onProgress: (Long) -> Unit) {
        val resolver = appContext.contentResolver
        var name = "upload"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        val init = send("POST", "/api/v1/photos/uploads", UploadInit.serializer(), buildJsonObject {
            put("filename", name)
            put("size", size.coerceAtLeast(0))
            put("content_type", contentType)
        })
        withContext(Dispatchers.IO) {
            val body = object : RequestBody() {
                override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()
                override fun contentLength() = size
                override fun writeTo(sink: BufferedSink) {
                    resolver.openInputStream(uri)!!.source().use { source ->
                        var sent = 0L
                        while (true) {
                            val read = source.read(sink.buffer, 64 * 1024L)
                            if (read == -1L) break
                            sink.emit()
                            sent += read
                            onProgress(sent)
                        }
                    }
                }
            }
            val put = Request.Builder().url(init.url).put(body).build()
            bareClient.newCall(put).execute().use { res: Response ->
                if (!res.isSuccessful) throw ApiException(res.code, "Upload failed")
            }
        }
        sendUnit("POST", "/api/v1/photos/uploads/${init.jobId}/complete", buildJsonObject {
            put("key", init.key)
            put("filename", name)
        })
    }

    fun fileSize(uri: Uri): Long =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 1L
        } ?: 1L

    companion object {
        const val PAGE_SIZE = 50
    }
}

