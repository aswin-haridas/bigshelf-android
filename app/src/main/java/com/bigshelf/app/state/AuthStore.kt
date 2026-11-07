package com.bigshelf.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.ApiException
import com.bigshelf.app.data.User
import com.bigshelf.app.data.userMessage
import kotlinx.coroutines.CancellationException

/** Port of stores/authStore.ts. */
object AuthStore {
    enum class Status { Idle, Loading, RestoreFailed, Authenticated }

    var status by mutableStateOf(Status.Idle)
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    private val api get() = BigShelfApp.api

    suspend fun login(identifier: String, password: String): Boolean {
        status = Status.Loading
        error = null
        return try {
            api.login(identifier, password)
            // Screens tell your albums from ones shared with you by this id
            user = api.me()
            status = Status.Authenticated
            true
        } catch (e: Exception) {
            status = Status.Idle
            error = e.userMessage()
            false
        }
    }

    suspend fun register(email: String, username: String, password: String): Boolean {
        status = Status.Loading
        error = null
        return try {
            api.signup(email, username, password)
            status = Status.Idle
            true
        } catch (e: Exception) {
            status = Status.Idle
            error = e.userMessage()
            false
        }
    }

    /** Restores the signed-in user from the persisted refresh cookie. */
    suspend fun fetchMe() {
        status = Status.Loading
        status = try {
            user = api.me()
            Status.Authenticated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiException && e.code == 401) Status.Idle else Status.RestoreFailed
        }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
        } finally {
            // Not on session expiry: signing in again picks backup up where it stopped
            BigShelfApp.backup.updateEnabled(false)
            onSessionEnded()
        }
    }

    fun onSessionEnded() {
        user = null
        status = Status.Idle
        error = null
        SelectionStore.clear()
    }

    fun clearError() {
        error = null
    }
}
