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
    // Framer score within a group (duplicate pair); the group's pick is marked best
    val quality: PhotoQuality? = null,
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

@Serializable
data class PhotoQuality(val score: Double = 0.0, val best: Boolean = false)

// ---------- Pipelines (modules/ai/types.ts) ----------

@Serializable
data class Pipeline(val id: String, val name: String = "", val description: String = "")

/** Face detection runs per upload, so it's a snapshot count rather than a Run. */
@Serializable
data class FaceTagging(val total: Int = 0, val done: Int = 0, val failed: Int = 0)

@Serializable
data class RunStage(
    val id: String,
    val name: String = "",
    val done: Int = 0,
    val failed: Int = 0,
    val secs: Double = 0.0,
)

@Serializable
data class RunLog(
    val timestamp: String = "",
    val level: String = "",
    // "select", "run", "refresh", or a RunStage id
    val step: String = "",
    val message: String = "",
)

@Serializable
data class Run(
    val id: String,
    val pipeline: String = "",
    // queued | running | success | failed | cancelled
    val status: String = "queued",
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    // 0 = executing now, n = waiting behind n runs, null = finished
    @SerialName("queue_position") val queuePosition: Int? = null,
    val stages: List<RunStage> = emptyList(),
    val stage: String? = null,
    val logs: List<RunLog> = emptyList(),
) {
    val isActive get() = status == "queued" || status == "running"

    /** 0-100; a successful run always reads full, even if its total was a placeholder. */
    val percent: Int
        get() = if (status == "success") 100 else if (total > 0) ((done + failed) * 100f / total).toInt() else 0
}

@Serializable
data class PipelinesData(
    val pipelines: List<Pipeline> = emptyList(),
    val runs: List<Run> = emptyList(),
    @SerialName("face_tagging") val faceTagging: FaceTagging? = null,
)

// ---------- Duplicates ----------

@Serializable
data class DuplicateGroup(val id: String, val similarity: Double = 0.0, val items: List<Photo> = emptyList()) {
    /** Order-independent key the backend stores for "Keep both" (sorted ids joined by |). */
    val key get() = items.map { it.id }.sorted().joinToString("|").ifEmpty { id }
}

@Serializable
data class DuplicatesResponse(
    @SerialName("total_groups") val totalGroups: Int = 0,
    @SerialName("auto_removed_count") val autoRemovedCount: Int? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    // Below the requested threshold when nothing matched at that strictness
    @SerialName("threshold_used") val thresholdUsed: Double? = null,
)

@Serializable
data class ClearedResponse(@SerialName("cleared_count") val clearedCount: Int = 0)

// ---------- Organize ----------

@Serializable
data class Bucket(val count: Int = 0, val items: List<Photo> = emptyList())
