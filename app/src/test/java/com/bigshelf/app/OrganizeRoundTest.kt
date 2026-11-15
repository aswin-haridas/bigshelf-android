package com.bigshelf.app

import com.bigshelf.app.ui.screens.tools.OrganizeKind
import com.bigshelf.app.ui.screens.tools.OrganizeRound
import org.junit.Assert.assertEquals
import org.junit.Test

class OrganizeRoundTest {
    @Test fun advancesThroughAllQuestionKindsInRoundsOfFive() {
        var round = OrganizeRound()
        val expected = listOf("outfit", "person", "scene", "human", "collage", "bin", "duplicate", "face")
        for (kind in expected) {
            assertEquals(kind, round.kind.apiName)
            assertEquals(null, round.skip)
            repeat(5) { count ->
                assertEquals(count, round.count)
                round = round.advance("last-photo")
            }
        }
        assertEquals(OrganizeRound(), round)
    }

    @Test fun emptyKindFallsBackToFirstQuestionOfAnotherKind() {
        val round = OrganizeRound().advance("last-photo")
        assertEquals("last-photo", round.forKind(OrganizeKind.OUTFIT).skip)
        assertEquals(OrganizeRound(OrganizeKind.PERSON), round.forKind(OrganizeKind.PERSON))
    }
}
