package com.bigshelf.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// API shapes - mirror the TypeScript types in frontend/src/modules/**.
// Every field the UI doesn't strictly need is nullable/defaulted so backend additions never break parsing.

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String,
)

@Serializable
data class TokenResponse(@SerialName("access_token") val accessToken: String)

@Serializable
data class ProcessingStatus(
    val face: String? = null,
    val alignment: String? = null,
    val accidentaltake: String? = null,
) {
    val values get() = listOfNotNull(face, alignment, accidentaltake)
}

@Serializable
data class FaceBox(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    @SerialName("face_id") val faceId: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("person_id") val personId: String? = null,
    @SerialName("person_name") val personName: String? = null,
    @SerialName("is_ignored") val isIgnored: Boolean = false,
)

@Serializable
data class Photo(
    val id: String,
    val filename: String = "",
    val title: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val url: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Capture date (EXIF), else upload date; the timeline groups by this
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("processing_status") val processingStatus: ProcessingStatus? = null,
    @SerialName("face_detection_status") val faceDetectionStatus: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("face_count") val faceCount: Int? = null,
    val faces: List<FaceBox>? = null,
    @SerialName("photo_person") val photoPerson: FaceBox? = null,
    val isAi: Boolean = false,
    @SerialName("ai_source") val aiSource: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_video") val isVideo: Boolean = false,
) {
    /** Same fallback as web `withIsAi`: server flag, else the ChatGPT filename rule. */
    val isAiImage get() = isAi || filename.lowercase().startsWith("chatgpt")

    /** Web `isProcessing`: any pipeline stage currently running. */
    val isProcessing: Boolean
        get() = processingStatus?.values?.any { it == "processing" }
            ?: (faceDetectionStatus == "processing")

    /** Web `isAwaitingProcessing`: drives detail polling. */
    val isAwaitingProcessing: Boolean
        get() = processingStatus?.values?.any { it == "pending" || it == "processing" }
            ?: (faceDetectionStatus == "pending" || faceDetectionStatus == "processing")

    /** Web `imageSrc`: full resolution for photos, poster frame for videos. */
    val imageSrc get() = if (isVideo) thumbnailUrl else url ?: thumbnailUrl
}

/** Mirrors backend USER_MARKED_AI: the only ai_source the user can clear. */
const val USER_MARKED_AI = "Marked by user"

@Serializable
data class PhotoPage(
    val items: List<Photo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 50,
) {
    val hasNext get() = page * size < total
}

@Serializable
data class FaceCover(
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("face_thumbnail") val faceThumbnail: String? = null,
    val box: FaceBox? = null,
)

@Serializable
data class Person(
    val id: String,
    val name: String,
    @SerialName("photo_count") val photoCount: Int = 0,
    val cover: FaceCover? = null,
    @SerialName("is_skipped") val isSkipped: Boolean = false,
)

@Serializable
data class PersonList(val items: List<Person> = emptyList(), val total: Int = 0)

@Serializable
data class PersonPhotos(val person: Person, val items: List<Photo> = emptyList(), val total: Int = 0)

@Serializable
data class FaceItem(
    val id: String,
    @SerialName("photo_id") val photoId: String,
    @SerialName("face_name") val faceName: String? = null,
    @SerialName("face_thumbnail") val faceThumbnail: String? = null,
    @SerialName("face_count") val faceCount: Int? = null,
    @SerialName("is_ignored") val isIgnored: Boolean = false,
)

@Serializable
data class FaceList(val faces: List<FaceItem> = emptyList())

@Serializable
data class FacePhotos(val face: FaceItem, val items: List<Photo> = emptyList(), val total: Int = 0)

@Serializable
data class AlbumListItem(
    val id: String,
    val title: String,
    @SerialName("photo_count") val photoCount: Int = 0,
)

@Serializable
data class AlbumList(val items: List<AlbumListItem> = emptyList())

/** One download batch in Archive; id is the batch's shared archived_at. */
@Serializable
data class ArchiveFolder(val id: String, val count: Int = 0, val cover: Photo? = null)

@Serializable
data class StorageUsage(@SerialName("used_bytes") val usedBytes: Long = 0)

@Serializable
data class UploadInit(
    @SerialName("job_id") val jobId: String,
    val key: String,
    val url: String,
)

@Serializable
data class CountResponse(
    @SerialName("deleted_count") val deletedCount: Int? = null,
    @SerialName("flagged_count") val flaggedCount: Int? = null,
)

@Serializable
data class UntagResponse(@SerialName("person_deleted") val personDeleted: Boolean = false)

@Serializable
data class SystemStats(
    @SerialName("cpu_percent") val cpuPercent: Float = 0f,
    @SerialName("memory_percent") val memoryPercent: Float = 0f,
)

@Serializable
data class JobStatus(val status: String = "")
