package com.bigshelf.app

import com.bigshelf.app.data.FaceList
import com.bigshelf.app.data.Person
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WebParityTest {
    @Test fun renameResponseCanIdentifyTheSurvivingProfile() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<Person>(
            """{"id":"survivor","name":"Sam","photo_count":24,"merged":true}""",
        )
        assertEquals("survivor", response.id)
        assertEquals("Sam", response.name)
    }

    @Test fun faceGroupsRetainPhotoContextForSuggestions() {
        val response = Json.decodeFromString<FaceList>(
            """{"faces":[{"id":"face","photo_id":"photo","cluster_id":"group","face_count":3}]}""",
        )
        assertEquals("photo", response.faces.single().photoId)
        assertEquals("group", response.faces.single().groupKey)
    }
}
