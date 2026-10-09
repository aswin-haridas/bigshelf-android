package com.bigshelf.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.User
import com.bigshelf.app.data.userMessage

/** Port of stores/authStore.ts. */
object AuthStore {
    enum class Status { Idle, Loading, Authenticated }

    var status by mutableStateOf(Status.Idle)
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val api get() = BigShelfApp.api

    suspend fun login(identifier: String, password: String): Boolean {
        status = Status.Loading
        error = null
        return try {
            api.login(identifier, password)
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

    /** Restores the session from the persisted refresh cookie (7 days), like the web's fetchMe. */
    suspend fun fetchMe() {
        status = Status.Loading
        status = try {
            user = api.me()
            Status.Authenticated
        } catch (e: Exception) {
            Status.Idle
        }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
        } finally {
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
