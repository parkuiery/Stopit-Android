package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPlacementContractTest {

    @Test
    fun `ad placements have unique non-empty snake case analytics names and ad unit ids`() {
        val placements = AdPlacement.entries

        assertEquals(
            listOf(
                "block_top",
                "home_bottom",
                "lock_bottom",
                "menu_bottom",
                "routine_list_bottom",
                "routine_empty_bottom",
            ),
            placements.map { it.analyticsPlacement },
        )
        assertEquals(
            placements.size,
            placements.map { it.analyticsPlacement }.toSet().size,
        )

        placements.forEach { placement ->
            assertTrue(
                "${placement.name} analytics placement should be lowercase snake_case",
                placement.analyticsPlacement.matches(Regex("[a-z]+(_[a-z]+)*")),
            )
            assertFalse(
                "${placement.name} ad unit id should not be blank",
                placement.adUnitId.isBlank(),
            )
        }
    }

    @Test
    fun `ad placement builds metadata from a single enum source of truth`() {
        val metadata = AdPlacement.RoutineEmptyBottom.toMetadata(
            screenName = "RoutineScreen",
            screenContext = "empty_state",
        )

        assertEquals("RoutineScreen", metadata.screenName)
        assertEquals("empty_state", metadata.screenContext)
        assertEquals("routine_empty_bottom", metadata.placement)
        assertEquals(AdPlacement.RoutineEmptyBottom.adUnitId, metadata.adUnitId)
        assertEquals("banner", metadata.adFormat)
    }
}
