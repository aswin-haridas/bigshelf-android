package com.bigshelf.app

import com.bigshelf.app.data.Photo
import com.bigshelf.app.data.PhotoPage
import com.bigshelf.app.data.PhotoPersonTag
import com.bigshelf.app.state.PagedPhotos
import com.bigshelf.app.state.QueryState
import com.bigshelf.app.ui.screens.groupIntoPosts
import com.bigshelf.app.ui.screens.tools.swipeHeatPoints
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class FeedAndSwipeTest {
    private fun photo(id: String, vararg people: String) = Photo(
        id = id, people = people.map { PhotoPersonTag(it, it) },
    )

    @Test fun postsOnlyCombineTheSamePersonInTheSameBurst() {
        val photos = listOf(photo("a", "Sam"), photo("b", "Sam"), photo("c", "Jo"), photo("d", "Jo"), photo("e", "Sam"))
        val bursts = mapOf("a" to "one", "b" to "one", "c" to "one", "d" to "two", "e" to "two")
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d"), listOf("e")),
            groupIntoPosts(photos, bursts).map { it.photos.map(Photo::id) })
        assertEquals(photos.size, groupIntoPosts(photos).size)
    }

    @Test fun groupPhotosStaySeparateFromPortraitsAndEachOther() {
        val photos = listOf(photo("a", "Sam"), photo("b", "Sam", "Jo"), photo("c", "Sam", "Jo"), photo("d", "Sam"))
        assertTrue(groupIntoPosts(photos, photos.associate { it.id to "burst" }).all { it.photos.size == 1 })
    }

    @Test fun untaggedBurstsStaySeparateFromTaggedPeopleAndPostsAreCapped() {
        val photos = listOf(photo("unknown")) + (1..22).map { photo("$it", "Sam") }
        val posts = groupIntoPosts(photos, photos.associate { it.id to "burst" })
        assertEquals(listOf(1, 20, 2), posts.map { it.photos.size })
        assertEquals(photos.map { it.id }, posts.flatMap { it.photos }.map { it.id })
    }

    @Test fun quickKeepsGainMoreHeatAndReworkLosesHeat() {
        assertEquals(2.0, swipeHeatPoints(1499), 0.0)
        assertEquals(0.25, swipeHeatPoints(1500), 0.0)
        assertEquals(0.25, swipeHeatPoints(10000), 0.0)
        assertEquals(-1.0, swipeHeatPoints(0, rework = true), 0.0)
    }

    @Test fun failedPagesExposeAnErrorAndRetryTheSamePage() = runBlocking {
        val calls = mutableListOf<Int>()
        var fail = true
        val feed = PagedPhotos(this) { page, _ ->
            calls += page
            if (fail) error("offline")
            PhotoPage(items = listOf(photo("$page")), total = 2, page = page, size = 1)
        }
        feed.refresh(); yield()
        assertTrue(feed.isError)
        assertFalse(feed.isFetching)
        fail = false
        feed.retry(); yield()
        assertFalse(feed.isError)
        fail = true
        feed.fetchNextPage(); yield()
        assertTrue(feed.isError)
        assertEquals(listOf("1"), feed.photos.map { it.id })
        fail = false
        feed.retry(); yield()
        assertEquals(listOf("1", "2"), feed.photos.map { it.id })
        assertEquals(listOf(1, 1, 2, 2), calls)
        fail = true
        feed.refresh(); yield()
        fail = false
        feed.retry(); yield()
        assertEquals(listOf(1, 1, 2, 2, 1, 1), calls)
    }

    @Test fun overlappingPagesKeepPhotoIdsUniqueAndInOrder() = runBlocking {
        var calls = 0
        val feed = PagedPhotos(this) { page, _ ->
            calls++
            PhotoPage(
                items = if (page == 1) listOf(photo("a"), photo("b"))
                    else listOf(photo("b"), photo("c"), photo("c"), photo("d")),
                nextCursor = if (page == 1) "next" else null,
            )
        }
        feed.refresh(); yield()
        feed.fetchNextPage(); feed.fetchNextPage(); yield()
        assertEquals(2, calls)
        assertEquals(listOf("a", "b", "c", "d"), feed.photos.map { it.id })
        assertFalse(feed.hasNextPage)
    }

    @Test fun replacingARefreshKeepsLoadingUntilTheReplacementFinishes() = runBlocking {
        var calls = 0
        val replacement = CompletableDeferred<PhotoPage>()
        val feed = PagedPhotos(this) { _, _ ->
            if (++calls == 1) awaitCancellation() else replacement.await()
        }
        feed.refresh(); yield()
        feed.refresh(); yield()
        assertTrue(feed.isFetching)
        replacement.complete(PhotoPage(items = listOf(photo("new"))))
        yield()
        assertFalse(feed.isFetching)
        assertEquals(listOf("new"), feed.photos.map { it.id })
    }

    @Test fun queriesAndPagesCancelTheirRequestsWhenDisposed() = runBlocking {
        var queryCancelled = false
        var pageCancelled = false
        val query = QueryState<String>(this) {
            try { awaitCancellation() } finally { queryCancelled = true }
        }
        val feed = PagedPhotos(this) { _, _ ->
            try { awaitCancellation() } finally { pageCancelled = true }
        }
        query.refetch(); feed.refresh(); yield()
        query.cancel(); feed.cancel(); yield()
        assertTrue(queryCancelled)
        assertTrue(pageCancelled)
        assertFalse(query.isError)
        assertFalse(feed.isError)
    }
}
