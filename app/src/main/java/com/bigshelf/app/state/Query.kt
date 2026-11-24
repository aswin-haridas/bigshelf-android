package com.bigshelf.app.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.PhotoPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Minimal react-query stand-in: loads once, keeps the last data while refetching, and
 * refetches whenever one of its keys is invalidated through [PhotoEvents].
 */
class QueryState<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    /** Settable so a mutation's response can stand in for a refetch (web `setQueryData`). */
    var data by mutableStateOf<T?>(null)
    var isLoading by mutableStateOf(true)
        private set
    var isError by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun cancel() { job?.cancel() }

    fun refetch() {
        job?.cancel()
        job = scope.launch {
            try {
                data = fetch()
                isError = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                isError = true
            } finally {
                if (currentCoroutineContext().isActive) isLoading = false
            }
        }
    }
}

@Composable
fun <T> rememberQuery(
    vararg keys: Any?,
    invalidatedBy: Set<String>,
    active: Boolean = true,
    fetch: suspend () -> T,
): QueryState<T> {
    val scope = rememberCoroutineScope()
    val query = remember(*keys) { QueryState(scope, fetch) }
    val isActive by rememberUpdatedState(active)
    DisposableEffect(query) { onDispose { query.cancel() } }
    LaunchedEffect(query) {
        var stale = true
        launch {
            snapshotFlow { isActive }.filter { it }.collect {
                if (stale || query.isError) {
                    stale = false
                    query.refetch()
                }
            }
        }
        PhotoEvents.invalidations.filter { it.any(invalidatedBy::contains) }.collect {
            stale = !isActive
            if (!stale) query.refetch()
        }
    }
    return query
}

/**
 * Infinite photo feed (web `usePhotoPages`), 50 per page. [load] gets the page number and,
 * after the first page of a list that returns one, the next page's cursor (send that
 * instead: deep pages then skip the server's count and offset scan).
 */
class PagedPhotos(private val scope: CoroutineScope, private val load: suspend (page: Int, cursor: String?) -> PhotoPage) {
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var total by mutableStateOf<Int?>(null)
        private set
    var hasNextPage by mutableStateOf(false)
        private set
    var isFetching by mutableStateOf(true)
        private set
    var isError by mutableStateOf(false)
        private set
    private var page = 0
    private var cursor: String? = null
    private var job: Job? = null

    fun cancel() { job?.cancel() }

    private var retryNextPage = false

    /** Reloads from page 1 (an invalidation); the old photos stay up until it lands. */
    fun refresh() {
        retryNextPage = false
        job?.cancel()
        isFetching = true
        isError = false
        job = scope.launch {
            try {
                replaceWith(load(1, null))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                isError = true
            } finally {
                if (currentCoroutineContext().isActive) isFetching = false
            }
        }
    }

    /**
     * Photos new at the top since the list loaded, held back by [sync] when [holdNew] is on so
     * they don't appear above someone reading; [showNew] puts them in.
     */
    var newPhotos by mutableStateOf<List<Photo>>(emptyList())
        private set
    /** True when so much changed that page 1 no longer overlaps the list: [showNew] reloads instead. */
    var newNeedsReload by mutableStateOf(false)
        private set
    var holdNew = false

    /**
     * An invalidation: refetches page 1 and merges it in place, keeping every deeper page
     * already loaded (and so the scroll position). Falls back to [refresh] for an empty list.
     */
    fun sync() {
        if (photos.isEmpty()) return refresh()
        retryNextPage = false
        job?.cancel()
        isFetching = true
        isError = false
        job = scope.launch {
            try {
                val first = load(1, null)
                val merged = mergeFirstPage(photos, first.items, holdNew)
                if (merged == null) {
                    // No overlap: everything visible is stale or pushed far down
                    if (holdNew) newNeedsReload = true else replaceWith(first)
                } else {
                    photos = merged.first
                    newPhotos = merged.second
                    newNeedsReload = false
                    total = first.total
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                isError = true
            } finally {
                if (currentCoroutineContext().isActive) isFetching = false
            }
        }
    }

    /** Puts the held [newPhotos] at the top. */
    fun showNew() {
        if (newNeedsReload) return refresh()
        val seen = newPhotos.mapTo(HashSet()) { it.id }
        photos = newPhotos + photos.filterNot { it.id in seen }
        newPhotos = emptyList()
    }

    private fun replaceWith(first: PhotoPage) {
        photos = first.items
        total = first.total
        hasNextPage = first.hasNext
        cursor = first.nextCursor
        page = 1
        newPhotos = emptyList()
        newNeedsReload = false
    }

    fun fetchNextPage() {
        if (isFetching || !hasNextPage) return
        retryNextPage = true
        isFetching = true
        isError = false
        job = scope.launch {
            try {
                val next = load(page + 1, cursor)
                val seen = photos.mapTo(HashSet()) { it.id }
                photos = photos + next.items.filter { seen.add(it.id) }
                // Cursor pages aren't counted: keep the first page's total
                if (next.nextCursor == null && cursor == null) total = next.total
                hasNextPage = next.hasNext
                cursor = next.nextCursor
                page += 1
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                isError = true
            } finally {
                if (currentCoroutineContext().isActive) isFetching = false
            }
        }
    }

    fun retry() {
        if (retryNextPage) fetchNextPage() else refresh()
    }
}

@Composable
fun rememberPagedPhotos(
    vararg keys: Any?,
    invalidatedBy: Set<String>,
    // Hold photos that arrive at the top for a "New photos" button (see PagedPhotos.newPhotos)
    holdNew: Boolean = false,
    load: suspend (page: Int, cursor: String?) -> PhotoPage,
): PagedPhotos {
    val scope = rememberCoroutineScope()
    val paged = remember(*keys) { PagedPhotos(scope, load).also { it.holdNew = holdNew } }
    DisposableEffect(paged) { onDispose { paged.cancel() } }
    LaunchedEffect(paged) {
        paged.refresh()
        PhotoEvents.invalidations.collect { if (it.any(invalidatedBy::contains)) paged.sync() }
    }
    return paged
}

/**
 * Merges a fresh page 1 into a longer loaded list. Page 1 is the truth for the stretch it
 * covers (edits, removals, moves); old photos after the last one page 1 still has stay as
 * they are. With [holdNew], unseen photos above the first known one are returned separately
 * instead of shown. Null when page 1 shares no photo with the list.
 *
 * ponytail: offset-paged lists (albums, colors, tags) can skip a photo on their next page
 * after a removal; cursor paging (Home, Archive, Bin) can't.
 */
internal fun mergeFirstPage(old: List<Photo>, first: List<Photo>, holdNew: Boolean): Pair<List<Photo>, List<Photo>>? {
    val oldIndex = old.withIndex().associate { it.value.id to it.index }
    val firstKnown = first.indexOfFirst { it.id in oldIndex }
    if (firstKnown == -1) return null
    val lastKnownOld = first.mapNotNull { oldIndex[it.id] }.max()
    val inFirst = first.mapTo(HashSet()) { it.id }
    val tail = old.drop(lastKnownOld + 1).filterNot { it.id in inFirst }
    return if (holdNew) (first.drop(firstKnown) + tail) to first.take(firstKnown)
    else (first + tail) to emptyList()
}
