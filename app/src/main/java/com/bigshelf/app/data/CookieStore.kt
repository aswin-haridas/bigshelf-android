package com.bigshelf.app.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Cookie jar persisted to SharedPreferences. The web app keeps its session in the
 * `refresh_token` HTTP-only cookie (7 days, path /api/v1/auth); persisting it here gives
 * the app the same "stay signed in" behaviour across restarts.
 */
class PersistentCookieJar(context: Context, private val baseUrl: String) : CookieJar {
    private val prefs = context.getSharedPreferences("bigshelf-cookies", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()

    init {
        val origin = baseUrl.toHttpUrl()
        prefs.getStringSet(KEY, emptySet())!!
            .mapNotNull { Cookie.parse(origin, it) }
            .filter { it.expiresAt > System.currentTimeMillis() }
            .let(cookies::addAll)
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            // An already-expired cookie is how the server deletes one (logout)
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += cookie
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persist()
    }

    private fun persist() {
        prefs.edit().putStringSet(KEY, cookies.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}
