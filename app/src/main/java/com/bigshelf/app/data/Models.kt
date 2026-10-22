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
    // The backend's guess for an untagged face (closest person, or from the file name)
    @SerialName("suggested_name") val suggestedName: String? = null,
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
    // Detail-only: 0-1 chance the AI detector gives that it is AI; null until it has run
    @SerialName("ai_score") val aiScore: Double? = null,
    // Heat out of 10 from the Grading taste model (detail only); null until trained
    val heat: Double? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_video") val isVideo: Boolean = false,
    // Framer score within a group (duplicate pair, burst); the group's pick is marked best
    val quality: PhotoQuality? = null,
    // Auto tags, most likely first (detail only): scene labels and portrait / group / smiling
    val tags: List<String>? = null,
    // Where it was taken (EXIF GPS) and camera details; null for older uploads
    val latitude: Double? = null,
    val longitude: Double? = null,
    val exif: CameraInfo? = null,
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
/** Mirrors backend AI_MODEL_SOURCE: flagged by the AI detector pipeline, also clearable. */
const val AI_MODEL_SOURCE = "Detected by model"

/** Two random photos to ask "which is hotter?" (Grading). */
@Serializable
data class GradingPair(val id: String, val items: List<Photo>)

@Serializable
data class GradingResponse(val rated: Int = 0, val pairs: List<GradingPair> = emptyList())

@Serializable
data class CompareResponse(val id: String)

@Serializable
data class PhotoPage(
    val items: List<Photo> = emptyList(),
    // Only the first page is counted on cursor lists; later ones send null, read as 0
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 50,
    // Next page's cursor (Home, Bin, Archive); null on the last page and on number-paged lists
    @SerialName("next_cursor") val nextCursor: String? = null,
) {
    val hasNext get() = nextCursor != null || page * size < total
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
    // Detected group this face belongs to; null when it stands alone
    @SerialName("cluster_id") val clusterId: String? = null,
) {
    /** One card per group in the Faces grid and the merge picker. */
    val groupKey get() = clusterId ?: id
}

@Serializable
data class FaceList(val faces: List<FaceItem> = emptyList())

@Serializable
data class FacePhotos(val face: FaceItem, val items: List<Photo> = emptyList(), val total: Int = 0)

/** Camera details the backend read from the upload's EXIF. */
@Serializable
data class CameraInfo(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    @SerialName("f_number") val fNumber: Double? = null,
    // Seconds
    @SerialName("exposure_time") val exposureTime: Double? = null,
    val iso: Int? = null,
    // Millimetres
    @SerialName("focal_length") val focalLength: Double? = null,
)

@Serializable
data class AlbumListItem(
    val id: String,
    val title: String,
    // Someone else's id when the album was shared with you
    @SerialName("owner_id") val ownerId: String? = null,
    val description: String? = null,
    @SerialName("photo_count") val photoCount: Int = 0,
    // Sub-albums: the album this one sits in, and how many sit in it
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("child_count") val childCount: Int = 0,
    @SerialName("cover_photo") val coverPhoto: Photo? = null,
)

@Serializable
data class AlbumList(val items: List<AlbumListItem> = emptyList(), val total: Int = 0)

/**
 * The smart filters on GET /photos (and album creation): capture-date range (ISO), a map
 * area [minLat, minLon, maxLat, maxLon], or photos in no album that aren't hearted.
 */
@Serializable
data class PhotoFilter(
    @SerialName("taken_from") val takenFrom: String? = null,
    @SerialName("taken_to") val takenTo: String? = null,
    val bbox: List<Double>? = null,
    val unorganized: Boolean = false,
)

// ---------- Search tab groupings (backend endpoints/explore.py) ----------

@Serializable
data class PhotoLocation(val id: String, val latitude: Double, val longitude: Double)

@Serializable
data class LocationList(val items: List<PhotoLocation> = emptyList())

/** One ~11 km cell of geotagged photos; named on the device by the Geocoder. */
@Serializable
data class PlaceCell(
    val latitude: Double,
    val longitude: Double,
    val count: Int = 0,
    val bbox: List<Double> = emptyList(),
    val cover: Photo? = null,
)

@Serializable
data class PlaceList(val items: List<PlaceCell> = emptyList())

@Serializable
data class LibraryEvent(
    val id: String,
    // "event" | "trip"
    val kind: String = "event",
    // A fixed-date occasion it falls on (Christmas...)
    val occasion: String? = null,
    val start: String,
    val end: String,
    val count: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cover: Photo? = null,
) {
    val isTrip get() = kind == "trip"
}

@Serializable
data class EventList(val items: List<LibraryEvent> = emptyList(), val total: Int = 0)

/** Shots taken within seconds that look alike; one is marked `quality.best`. */
@Serializable
data class Burst(val id: String, val items: List<Photo> = emptyList())

@Serializable
data class BurstList(val groups: List<Burst> = emptyList(), val total: Int = 0)

/** One auto tag ("Things"): how many photos carry it and the newest as cover. */
@Serializable
data class TagSummary(val tag: String, val count: Int = 0, val cover: Photo? = null)

@Serializable
data class TagList(val items: List<TagSummary> = emptyList())

@Serializable
data class FaceTagResult(
    @SerialName("person_name") val personName: String = "",
    @SerialName("auto_tagged_count") val autoTaggedCount: Int = 0,
)

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

/** Organize's Clean up: photos scored under the cut-off; [nextBefore] is null once the library runs out. */
@Serializable
data class UglyPage(
    val items: List<Photo> = emptyList(),
    @SerialName("next_before") val nextBefore: String? = null,
)

// ---------- Face review (web useFaceConfirmations) ----------

@Serializable
data class FaceReviewItem(
    @SerialName("face_id") val faceId: String,
    @SerialName("photo_id") val photoId: String,
    @SerialName("face_thumbnail") val faceThumbnail: String? = null,
)

/** One grid screen: "Are these all [personName]?" */
@Serializable
data class FaceReviewBatch(
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String,
    val reference: FaceReviewItem,
    val faces: List<FaceReviewItem> = emptyList(),
)

@Serializable
data class MergeSuggestionPerson(
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String,
    @SerialName("face_thumbnail") val faceThumbnail: String? = null,
    @SerialName("face_count") val faceCount: Int = 0,
)

/** Two people who look nearly identical: [merge] folds into [keep] on "Same person". */
@Serializable
data class MergeSuggestion(val keep: MergeSuggestionPerson, val merge: MergeSuggestionPerson)

/** The current review screen; [merge] is asked before any face grid. */
@Serializable
data class FaceReview(val batch: FaceReviewBatch? = null, val merge: MergeSuggestion? = null, val total: Int = 0)

/** A public link (/s/{token}) to an album or a selection of photos. */
@Serializable
data class ShareLink(
    val id: String,
    val token: String,
    val title: String,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** Someone in a shared album; role is "owner" or "member". */
@Serializable
data class AlbumMember(val id: String, val username: String, val role: String)

// ---------- Face actions ----------

/** faceIndex that tags the photo itself, when the person's face wasn't detected. */
const val WHOLE_PHOTO_FACE_INDEX = -1

@Serializable
data class PhotoFaceResult(
    val photo: Photo,
    @SerialName("auto_tagged_count") val autoTaggedCount: Int = 0,
)

@Serializable
data class SuggestedName(@SerialName("suggested_name") val suggestedName: String? = null)

@Serializable
data class ClusterResult(@SerialName("auto_assigned_to_people") val autoAssignedToPeople: Int = 0)

/** [invalidate]: the query keys the backend says went stale with this merge. */
@Serializable
data class MergeGroupResult(
    @SerialName("merged_count") val mergedCount: Int = 0,
    val invalidate: List<String> = emptyList(),
)

// ---------- Triptych / Triplets / Composer ----------

/** One set of photos, and the single person it shows (when it does) so they can be skipped. */
data class PhotoSet(val photos: List<Photo>, val personId: String?, val personName: String?)

// ---------- Framer ----------

@Serializable
data class FrameBreakdown(
    val sharpness: Double = 0.0,
    val framing: Double = 0.0,
    val portrait: Double = 0.0,
    val lighting: Double = 0.0,
)

/** One extracted frame; [imageData] is a `data:image/jpeg;base64,...` URL. */
@Serializable
data class Frame(
    @SerialName("frame_index") val frameIndex: Int,
    @SerialName("timestamp_formatted") val timestampFormatted: String = "",
    val score: Double = 0.0,
    val breakdown: FrameBreakdown = FrameBreakdown(),
    val highlights: List<String> = emptyList(),
    @SerialName("image_data") val imageData: String,
)

@Serializable
data class FramerResult(
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerialName("analyzed_count") val analyzedCount: Int = 0,
    val frames: List<Frame> = emptyList(),
)

@Serializable
data class FramesSaved(@SerialName("saved_count") val savedCount: Int = 0)

// ---------- Editing ----------

/** A crop window in image pixels. */
data class CropBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    companion object {
        /**
         * Web `crop3x4Box`: the largest 3:4 (portrait) box in a [width] x [height] image. Wider
         * than 3:4 trims the sides, taller trims top and bottom; [pos] (0..1) slides it along.
         */
        fun portrait(width: Int, height: Int, pos: Float = 0.5f): CropBox {
            val w = minOf(width, height * 3 / 4)
            val h = minOf(height, width * 4 / 3)
            return CropBox(Math.round((width - w) * pos), Math.round((height - h) * pos), w, h)
        }
    }
}

@Serializable
data class SplitResult(@SerialName("created_photos") val createdPhotos: List<Photo> = emptyList())
