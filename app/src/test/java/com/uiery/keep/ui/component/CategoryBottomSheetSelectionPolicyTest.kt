package com.uiery.keep.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBottomSheetSelectionPolicyTest {

    @Test
    fun togglingAllAppsReplacesSelectionWithANewImmutableSet() {
        val initial = mutableSetOf("com.example.one")
        val allApps = listOf("com.example.one", "com.example.two")

        val selected = toggleAllSelectableAppsSelection(
            currentSelection = initial,
            allAppPackages = allApps,
            checked = true,
        )

        assertEquals(setOf("com.example.one", "com.example.two"), selected)
        assertEquals(setOf("com.example.one"), initial)
        assertFalse(selected === initial)
    }

    @Test
    fun togglingSingleAppReturnsANewSetAndPreservesFilteredSelections() {
        val initial = mutableSetOf("com.example.visible", "com.example.hidden")

        val afterRemovingVisible = toggleSelectableAppSelection(
            currentSelection = initial,
            packageName = "com.example.visible",
        )
        val afterAddingAnother = toggleSelectableAppSelection(
            currentSelection = afterRemovingVisible,
            packageName = "com.example.another",
        )

        assertEquals(setOf("com.example.hidden"), afterRemovingVisible)
        assertEquals(setOf("com.example.hidden", "com.example.another"), afterAddingAnother)
        assertEquals(setOf("com.example.visible", "com.example.hidden"), initial)
        assertFalse(afterRemovingVisible === initial)
        assertFalse(afterAddingAnother === afterRemovingVisible)
    }

    @Test
    fun allAppsSelectedOnlyTracksLoadedAppPackages() {
        val allApps = listOf("com.example.one", "com.example.two")

        assertFalse(areAllSelectableAppsSelected(emptySet(), allApps))
        assertFalse(areAllSelectableAppsSelected(setOf("com.example.one"), allApps))
        assertTrue(areAllSelectableAppsSelected(setOf("com.example.one", "com.example.two"), allApps))
        assertTrue(
            areAllSelectableAppsSelected(
                setOf("com.example.one", "com.example.two", "com.example.stale"),
                allApps,
            ),
        )
    }
}
