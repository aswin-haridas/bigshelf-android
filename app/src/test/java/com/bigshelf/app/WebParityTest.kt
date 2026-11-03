package com.bigshelf.app

import com.bigshelf.app.data.FaceList
import com.bigshelf.app.data.Person
import com.bigshelf.app.data.Photo
import com.bigshelf.app.ui.screens.tools.tierForHeat
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WebParityTest {
    @Test fun heatTiersMatchWebBoundariesAndHandleUnratedPhotos() {
        val cases = mapOf(
            null to "Unrated", Double.NaN to "Unrated", Double.POSITIVE_INFINITY to "Unrated",
            -1.0 to "F", 0.0 to "F", 1.99 to "F", 2.0 to "D", 3.99 to "D",
            4.0 to "C", 5.99 to "C", 6.0 to "B", 7.99 to "B", 8.0 to "A",
            8.99 to "A", 9.0 to "S", 10.0 to "S",
        )
        cases.forEach { (heat, tier) -> assertEquals("Heat $heat", tier, tierForHeat(heat)) }
    }

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

    @Test fun ratedGridPhotosDecodeForTierLists() {
        val photo = Json.decodeFromString<Photo>("""{"id":"photo","heat":9.2}""")
        assertEquals("S", tierForHeat(photo.heat))
        assertEquals("Unrated", tierForHeat(Json.decodeFromString<Photo>("""{"id":"new"}""").heat))
    }
}
