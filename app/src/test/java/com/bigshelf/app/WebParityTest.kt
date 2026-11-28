package com.bigshelf.app

import com.bigshelf.app.data.CropBox
import com.bigshelf.app.data.CropCorner
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

    @Test fun cropCornersResizeIndependentlyAndStayInBounds() {
        val box = CropBox(10, 20, 30, 40)
        assertEquals(CropBox(0, 25, 40, 35), box.adjust(100, 100, -50, 5, CropCorner.TopLeft))
        assertEquals(CropBox(10, 20, 90, 1), box.adjust(100, 100, 100, -100, CropCorner.BottomRight))
        assertEquals(CropBox(70, 0, 30, 40), box.adjust(100, 100, 100, -100))
    }
}
