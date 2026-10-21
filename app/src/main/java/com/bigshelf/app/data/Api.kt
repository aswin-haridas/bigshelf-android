package com.bigshelf.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runInterruptible
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
open class ApiException(val code: Int, message: String) : IOException(message)

/** The presigned PUT to storage failed (expired URL, timeout, storage down): worth retrying. */
class StorageUploadException(code: Int) : ApiException(code, "Upload failed")

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
    ): String = callWithHeaders(method, path, params, body).first

    // Body text plus response headers (Triptych/Triplets name the set's person in X-Person-*)
    private suspend fun callWithHeaders(
        method: String,
        path: String,
        params: Map<String, Any?> = emptyMap(),
        body: RequestBody? = null,
    ): Pair<String, Headers> = withContext(Dispatchers.IO) {
        val httpUrl = url(path).toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
        }.build()
        val requestBody = body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody(null)
        val request = Request.Builder().url(httpUrl).method(method, requestBody).build()
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, errorDetail(text) ?: "Request failed")
            text to res.headers
        }
    }

    // multipart/form-data with one `file` part read from [uri] (Framer, search by image)
    // [filename]: for file:// URIs, which have no display name (the backend checks the extension)
    private fun filePart(uri: Uri, filename: String? = null, mimeType: String? = null): MultipartBody {
        val resolver = appContext.contentResolver
        var name = filename ?: "upload"
        if (filename == null) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        }
        val type = (mimeType ?: resolver.getType(uri))?.toMediaTypeOrNull()
        val body = object : RequestBody() {
            override fun contentType() = type
            override fun writeTo(sink: BufferedSink) {
                (resolver.openInputStream(uri) ?: throw IOException("Can't read $uri")).source().use { sink.writeAll(it) }
            }
        }
        return MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", name, body).build()
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

    suspend fun photos(page: Int, favorite: Boolean? = null, size: Int = PAGE_SIZE, filter: PhotoFilter? = null) =
        get(
            "/api/v1/photos", PhotoPage.serializer(),
            mapOf("page" to page, "size" to size, "favorite" to favorite) + filterParams(filter),
        )

    private fun filterParams(filter: PhotoFilter?): Map<String, Any?> = mapOf(
        "taken_from" to filter?.takenFrom,
        "taken_to" to filter?.takenTo,
        "bbox" to filter?.bbox?.joinToString(","),
        "unorganized" to filter?.unorganized?.takeIf { it },
    )

    // ---------- Search tab groupings ----------

    suspend fun locations() = get("/api/v1/photos/locations", LocationList.serializer()).items

    suspend fun places() = get("/api/v1/photos/places", PlaceList.serializer()).items

    suspend fun events(offset: Int = 0, limit: Int = 30) =
        get("/api/v1/photos/events", EventList.serializer(), mapOf("offset" to offset, "limit" to limit))

    suspend fun bursts(offset: Int = 0, limit: Int = 10) =
        get("/api/v1/photos/bursts", BurstList.serializer(), mapOf("offset" to offset, "limit" to limit))

    suspend fun photosByColor(color: String, page: Int) =
        get("/api/v1/photos/by-color", PhotoPage.serializer(), mapOf("color" to color, "page" to page, "size" to PAGE_SIZE))

    suspend fun searchPhotos(q: String) =
        get("/api/v1/photos/search", PhotoPage.serializer(), mapOf("q" to q)).items

    suspend fun photo(id: String) = get("/api/v1/photos/$id", Photo.serializer())

    suspend fun bin(page: Int, size: Int = PAGE_SIZE) =
        get("/api/v1/photos/bin", PhotoPage.serializer(), mapOf("page" to page, "size" to size))

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

    /**
     * Every person; [q] filters by name server-side, [faceId] ranks lookalikes of that face
     * first, [photoId] ranks people seen in similar photos first (whole-photo tagging).
     */
    suspend fun people(q: String? = null, faceId: String? = null, photoId: String? = null) = get(
        "/api/v1/people", PersonList.serializer(),
        mapOf("q" to q?.ifBlank { null }, "face_id" to faceId, "photo_id" to photoId),
    )

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

    /** Name a detected face: an existing person by id, or a new (or matching) name. */
    suspend fun tagFace(faceId: String, name: String? = null, personId: String? = null) = send(
        "POST", "/api/v1/faces/$faceId/tag", FaceTagResult.serializer(),
        buildJsonObject {
            if (personId != null) put("person_id", personId) else put("name", name)
        },
    )

    /** Forget a background face: it leaves Faces and its person (if any). */
    suspend fun ignoreFace(faceId: String) = sendUnit("POST", "/api/v1/faces/$faceId/ignore")

    /** Match untagged faces to existing people. */
    suspend fun clusterFaces() = send("POST", "/api/v1/faces/cluster", ClusterResult.serializer())

    /** Move the detected group holding [sourceFaceId] into [targetFaceId]'s group. */
    suspend fun mergeFaceGroup(sourceFaceId: String, targetFaceId: String) = send(
        "POST", "/api/v1/faces/$sourceFaceId/merge-group", MergeGroupResult.serializer(),
        buildJsonObject { put("target_face_id", targetFaceId) },
    )

    /**
     * Tag face number [faceIndex] on a photo (an existing person by id, or a name);
     * [WHOLE_PHOTO_FACE_INDEX] tags the photo itself when no face was detected.
     */
    suspend fun tagPhotoFace(photoId: String, faceIndex: Int, name: String? = null, personId: String? = null) = send(
        "POST", "/api/v1/photos/$photoId/faces/$faceIndex/tag", PhotoFaceResult.serializer(),
        buildJsonObject { if (personId != null) put("person_id", personId) else put("name", name) },
    )

    /** "untag" removes the person from that face, "ignore" forgets it. */
    suspend fun photoFaceAction(photoId: String, faceIndex: Int, action: String) =
        send("POST", "/api/v1/photos/$photoId/faces/$faceIndex/$action", PhotoFaceResult.serializer())

    /** A name guessed from the photo's filename, matched against existing people. */
    suspend fun suggestName(photoId: String) =
        get("/api/v1/photos/$photoId/suggest-name", SuggestedName.serializer()).suggestedName

    // ---------- Triptych, Triplets, Composer ----------

    /**
     * One set from [endpoint] (`/api/v1/triptych`, `/triplets`, `/composer`), with the person it
     * shows from the X-Person-* headers. Each endpoint ignores the params it doesn't use.
     */
    suspend fun photoSet(endpoint: String, offset: Int, options: Map<String, Any?>): PhotoSet {
        // [options] are the endpoint's own query params (setOptions in TriptychScreen)
        val (text, headers) = callWithHeaders("GET", endpoint, options + ("offset" to offset))
        return PhotoSet(
            photos = json.decodeFromString(ListSerializer(Photo.serializer()), text),
            personId = headers["x-person-id"],
            // Percent-encoded by the backend (header values are latin-1 only)
            personName = headers["x-person-name"]?.let { java.net.URLDecoder.decode(it, "UTF-8") },
        )
    }

    /** Never show this person in Triptych or Triplets again (undone from Settings). */
    suspend fun skipPerson(personId: String) = sendUnit("POST", "/api/v1/triplets/skip/$personId")

    suspend fun unskipPerson(personId: String) = sendUnit("DELETE", "/api/v1/triplets/skip/$personId")

    // ---------- Framer ----------

    /** Scores the video's frames and returns the best [topK] as base64 JPEGs. */
    suspend fun extractFrames(video: Uri, topK: Int, filename: String? = null, mimeType: String? = null) = json.decodeFromString(
        FramerResult.serializer(),
        call("POST", "/api/v1/framer/extract", mapOf("top_k" to topK), filePart(video, filename, mimeType)),
    )

    /** Downloads a media URL (a library video for Framer) into [file]. */
    suspend fun fetchToFile(path: String, file: java.io.File) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url(path)).build()).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "Download failed")
            file.outputStream().use { out -> res.body!!.byteStream().copyTo(out) }
        }
    }

    suspend fun saveFrames(frames: List<Frame>) = send(
        "POST", "/api/v1/framer/save", FramesSaved.serializer(),
        buildJsonObject {
            put("frames", JsonArray(frames.map { f ->
                buildJsonObject {
                    put("image_data", f.imageData)
                    put("timestamp_formatted", f.timestampFormatted)
                    put("title", "Frame ${f.timestampFormatted}")
                }
            }))
        },
    )

    // ---------- Editing ----------

    /** Cuts the photo to [box] in place (the original is overwritten and reprocessed). */
    suspend fun crop(photoId: String, box: CropBox) = sendUnit(
        "POST", "/api/v1/photos/$photoId/crop",
        buildJsonObject {
            put("box", buildJsonObject {
                put("x", box.x)
                put("y", box.y)
                put("width", box.width)
                put("height", box.height)
            })
            put("save_as_new", false)
        },
    )

    /** Splits a collage into its photos; the original moves to the Bin. */
    suspend fun split(photoId: String) = send("POST", "/api/v1/splitter/$photoId/split", SplitResult.serializer())

    // ---------- Similar & reverse image search ----------

    suspend fun similar(photoId: String, limit: Int = 24) =
        get("/api/v1/photos/$photoId/similar", PhotoPage.serializer(), mapOf("limit" to limit)).items

    suspend fun searchByImage(image: Uri) = json.decodeFromString(
        PhotoPage.serializer(),
        call("POST", "/api/v1/photos/search-by-image", body = filePart(image)),
    ).items

    // ---------- Albums ----------

    /** Every album by default; [parentId] lists one album's sub-albums, [topLevel] the roots. */
    suspend fun albums(parentId: String? = null, topLevel: Boolean = false) = get(
        "/api/v1/albums", AlbumList.serializer(),
        mapOf("size" to 100, "parent_id" to parentId, "top_level" to topLevel.takeIf { it }),
    ).items

    suspend fun album(id: String) = get("/api/v1/albums/$id", AlbumListItem.serializer())

    suspend fun albumPhotos(id: String, page: Int) =
        get("/api/v1/albums/$id/photos", PhotoPage.serializer(), mapOf("page" to page, "size" to PAGE_SIZE))

    suspend fun addToAlbum(albumId: String, photoIds: Collection<String>) = sendUnit(
        "POST", "/api/v1/albums/$albumId/photos",
        buildJsonObject { put("photo_ids", ids(photoIds)) },
    )

    suspend fun removeFromAlbum(albumId: String, photoIds: Collection<String>) = sendUnit(
        "DELETE", "/api/v1/albums/$albumId/photos",
        buildJsonObject { put("photo_ids", ids(photoIds)) },
    )

    /** New album with [photoIds], inside [parentId], plus every photo [filter] matches (smart album). */
    suspend fun createAlbum(
        title: String,
        photoIds: Collection<String> = emptyList(),
        parentId: String? = null,
        filter: PhotoFilter? = null,
    ) = send(
        "POST", "/api/v1/albums", AlbumListItem.serializer(),
        buildJsonObject {
            put("title", title)
            put("photo_ids", ids(photoIds))
            parentId?.let { put("parent_id", it) }
            filter?.takenFrom?.let { put("taken_from", it) }
            filter?.takenTo?.let { put("taken_to", it) }
            filter?.bbox?.let { box -> put("bbox", JsonArray(box.map(::JsonPrimitive))) }
        },
    )

    suspend fun renameAlbum(id: String, title: String) =
        send("PATCH", "/api/v1/albums/$id", AlbumListItem.serializer(), buildJsonObject { put("title", title) })

    /** Move inside [parentId]; null moves it to the top level. */
    suspend fun moveAlbum(id: String, parentId: String?) =
        send("PATCH", "/api/v1/albums/$id", AlbumListItem.serializer(), buildJsonObject { put("parent_id", parentId) })

    suspend fun deleteAlbum(id: String) = sendUnit("DELETE", "/api/v1/albums/$id")

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

    /** The next page's low framer scores (worst first), photos older than [before]. */
    suspend fun uglyPhotos(before: String?) =
        get("/api/v1/photos/organize/ugly", UglyPage.serializer(), mapOf("before" to before))

    // ---------- Grading ----------

    suspend fun gradingPairs(limit: Int) = get("/api/v1/grading", GradingResponse.serializer(), mapOf("limit" to limit))

    /** [winnerId] is hotter than [loserId]; returns the stored answer's id for Undo. */
    suspend fun compare(winnerId: String, loserId: String) = send(
        "POST", "/api/v1/grading/compare", CompareResponse.serializer(),
        buildJsonObject {
            put("winner_id", winnerId)
            put("loser_id", loserId)
        },
    ).id

    suspend fun undoCompare(id: String) = sendUnit("DELETE", "/api/v1/grading/compare/$id")

    suspend fun resetGrading() = send("DELETE", "/api/v1/grading", ClearedResponse.serializer())

    // ---------- Sharing ----------

    /** Web address of a share link: the web app serves /s/{token} on the API's origin. */
    fun shareUrl(token: String) = "$baseUrl/s/$token"

    suspend fun shares() = get("/api/v1/shares", ListSerializer(ShareLink.serializer()))

    /** A public link to [albumId], or to [photoIds] when no album is given. */
    suspend fun createShare(albumId: String? = null, photoIds: Collection<String> = emptyList()) = send(
        "POST", "/api/v1/shares", ShareLink.serializer(),
        buildJsonObject { if (albumId != null) put("album_id", albumId) else put("photo_ids", ids(photoIds)) },
    )

    suspend fun deleteShare(id: String) = sendUnit("DELETE", "/api/v1/shares/$id")

    /** Albums other people shared with you. */
    suspend fun sharedAlbums() = get("/api/v1/albums/shared", AlbumList.serializer()).items

    suspend fun albumMembers(albumId: String) =
        get("/api/v1/albums/$albumId/members", ListSerializer(AlbumMember.serializer()))

    /** Shares the album with a user, by username or email. Returns everyone in it. */
    suspend fun addAlbumMember(albumId: String, identifier: String) = send(
        "POST", "/api/v1/albums/$albumId/members", ListSerializer(AlbumMember.serializer()),
        buildJsonObject { put("identifier", identifier) },
    )

    /** The owner removes someone; with your own id, you leave. */
    suspend fun removeAlbumMember(albumId: String, userId: String) =
        sendUnit("DELETE", "/api/v1/albums/$albumId/members/$userId")

    // ---------- System ----------

    suspend fun systemStats() = get("/api/v1/system/stats", SystemStats.serializer())

    suspend fun regenerateThumbnails() = send("POST", "/api/v1/system/regenerate-thumbnails", JobStatus.serializer())

    // ---------- Upload ----------

    /**
     * Same three steps as the web `useUpload`: reserve a job, PUT the file straight to
     * storage with the presigned URL, then tell the API it landed so Celery processes it.
     * [onProgress] gets bytes sent so far. With [contentHash] (SHA-256 hex) the API refuses
     * a file already in the library with a 409 before any bytes are sent.
     */
    suspend fun upload(uri: Uri, contentHash: String? = null, albumId: String? = null, onProgress: (Long) -> Unit) {
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
            if (contentHash != null) put("content_hash", contentHash)
        })
        // Interruptible so cancelling the caller (backup paused) aborts the PUT mid-file
        runInterruptible(Dispatchers.IO) {
            val body = object : RequestBody() {
                override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()
                override fun contentLength() = size
                override fun writeTo(sink: BufferedSink) {
                    openOriginal(uri).source().use { source ->
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
                if (!res.isSuccessful) throw StorageUploadException(res.code)
            }
        }
        sendUnit("POST", "/api/v1/photos/uploads/${init.jobId}/complete", buildJsonObject {
            put("key", init.key)
            put("filename", name)
            // Lands in this album once processed (yours, or one shared with you)
            if (albumId != null) put("album_id", albumId)
        })
    }

    /**
     * The file with its EXIF location intact. Android strips GPS from picked and shared photos
     * unless the app holds ACCESS_MEDIA_LOCATION and asks MediaStore for the original; picker
     * URIs that don't allow that (or no permission) fall back to the redacted copy.
     */
    private fun openOriginal(uri: Uri): java.io.InputStream {
        val resolver = appContext.contentResolver
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            uri.authority == MediaStore.AUTHORITY &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (canAsk) runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(uri)) }.getOrNull()?.let { return it }
        return resolver.openInputStream(uri) ?: throw IOException("Can't read $uri")
    }

    fun fileSize(uri: Uri): Long =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 1L
        } ?: 1L

    companion object {
        const val PAGE_SIZE = 50
    }
}

