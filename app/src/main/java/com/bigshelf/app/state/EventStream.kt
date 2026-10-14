package com.bigshelf.app.state

import com.bigshelf.app.BigShelfApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Port of hooks/useEventStream.ts: keeps every screen in sync with server-side changes by
 * reading the backend's server-sent events (GET /api/v1/events) and invalidating the keys
 * each event names - e.g. photos refresh once an upload finishes processing.
 */
object EventStream {
    // Backend keys name the list a mutation touched; these views derive from the same rows
    private val RELATED_KEYS = mapOf(
        "photos" to listOf("photo", "organize", "triptych"),
        "faces" to listOf("photo", "face-confirmations"),
        "people" to listOf("photo", "face-confirmations"),
    )

    // Workers emit one invalidation per processed photo, so a batch upload fires dozens a
    // second. Keys are collected and flushed together once per window instead.
    private const val FLUSH_DELAY_MS = 250L
    private const val MAX_RECONNECT_DELAY_MS = 30_000L

    // A reconnect after the first means events were missed while away: refetch everything
    private var everConnected = false

    /** Listens until cancelled (the app leaving the foreground), reconnecting with backoff. */
    suspend fun listen() = coroutineScope {
        val api = BigShelfApp.api
        val pending = mutableSetOf<String>()
        var flushing = false

        fun add(keys: List<String>) {
            synchronized(pending) {
                keys.forEach { key -> pending += key; pending += RELATED_KEYS[key].orEmpty() }
                if (flushing) return
                flushing = true
            }
            launch {
                delay(FLUSH_DELAY_MS)
                val batch = synchronized(pending) { flushing = false; pending.toSet().also { pending.clear() } }
                PhotoEvents.invalidate(*batch.toTypedArray())
            }
        }

        fun handle(type: String, data: String) {
            when (type) {
                "connected" -> {
                    if (everConnected) PhotoEvents.invalidateAll()
                    everConnected = true
                }
                // Server dropped our backlog (we fell too far behind)
                "resync" -> PhotoEvents.invalidateAll()
                "invalidate" -> runCatching {
                    api.json.parseToJsonElement(data).jsonObject["keys"]!!.jsonArray.map { it.jsonPrimitive.content }
                }.getOrNull()?.let(::add)
            }
        }

        var attempt = 0
        while (isActive) {
            val token = api.accessToken
            if (token != null) {
                try {
                    read(token) { type, data ->
                        attempt = 0
                        handle(type, data)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Dropped, or a 401 from an expired token: refreshed below before retrying
                }
            }
            delay(minOf(MAX_RECONNECT_DELAY_MS, 1000L shl attempt.coerceAtMost(5)))
            attempt++
            // Any authed request renews an expired access token (or ends the session)
            runCatching { api.me() }
        }
    }

    // One connection: dispatches each `event:` / `data:` block until the stream ends
    private suspend fun read(token: String, onEvent: (type: String, data: String) -> Unit) = withContext(Dispatchers.IO) {
        val api = BigShelfApp.api
        // EventSource can't send headers, so the access token rides in ?token= (same as the web)
        val request = Request.Builder().url(api.url("/api/v1/events?token=$token")).build()
        val call = api.bareClient.newCall(request)
        // A blocking read ignores coroutine cancellation; cancelling the call unblocks it
        val cancel = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { res ->
                if (!res.isSuccessful) return@use
                val source = res.body!!.source()
                var type = "message"
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) withContext(Dispatchers.Main) { onEvent(type, data.toString()) }
                            type = "message"
                            data.clear()
                        }
                        line.startsWith("event:") -> type = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                        // ": keepalive" comments and "retry:" need nothing
                    }
                }
            }
        } finally {
            cancel.dispose()
        }
    }
}
