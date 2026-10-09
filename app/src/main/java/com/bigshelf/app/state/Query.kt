package com.bigshelf.app.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.PhotoPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Minimal react-query stand-in: loads once, keeps the last data while refetching, and
 * refetches whenever one of [keys] is invalidated through [PhotoEvents].
 */
class QueryState<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    /** Settable so a mutation's response can stand in for a refetch (web `setQueryData`). */
    var data by mutableStateOf<T?>(null)
    var isLoading by mutableStateOf(true)
        private set
    var isError by mutableStateOf(false)
        private set
    private var job: Job? = null

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
                isLoading = false
            }
        }
    }
}

@Composable
fun <T> rememberQuery(vararg keys: Any?, invalidatedBy: Set<String>, fetch: suspend () -> T): QueryState<T> {
    val scope = rememberCoroutineScope()
    val query = remember(*keys) { QueryState(scope, fetch) }
    LaunchedEffect(query) {
        query.refetch()
        PhotoEvents.invalidations.collect { if (it.any(invalidatedBy::contains)) query.refetch() }
    }
    return query
}

/** Infinite photo feed (web `usePhotoPages`): page/size params, 50 per page. */
class PagedPhotos(private val scope: CoroutineScope, private val load: suspend (page: Int) -> PhotoPage) {
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var total by mutableStateOf<Int?>(null)
        private set
    var hasNextPage by mutableStateOf(false)
        private set
    var isFetching by mutableStateOf(true)
        private set
    private var page = 0
    private var job: Job? = null

    /** Reloads from page 1 (an invalidation); the old photos stay up until it lands. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            isFetching = true
            try {
                val first = load(1)
                photos = first.items
                total = first.total
                hasNextPage = first.hasNext
                page = 1
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                isFetching = false
            }
        }
    }

    fun fetchNextPage() {
        if (isFetching || !hasNextPage) return
        job = scope.launch {
            isFetching = true
            try {
                val next = load(page + 1)
                photos = photos + next.items.filter { n -> photos.none { it.id == n.id } }
                total = next.total
                hasNextPage = next.hasNext
                page += 1
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                isFetching = false
            }
        }
    }

    /** Optimistic removal after a bulk action (web `dropFromCachedLists`). */
    fun drop(ids: Set<String>) {
        photos = photos.filterNot { it.id in ids }
    }
}

@Composable
fun rememberPagedPhotos(vararg keys: Any?, invalidatedBy: Set<String>, load: suspend (page: Int) -> PhotoPage): PagedPhotos {
    val scope = rememberCoroutineScope()
    val paged = remember(*keys) { PagedPhotos(scope, load) }
    LaunchedEffect(paged) {
        paged.refresh()
        PhotoEvents.invalidations.collect { if (it.any(invalidatedBy::contains)) paged.refresh() }
    }
    return paged
}
