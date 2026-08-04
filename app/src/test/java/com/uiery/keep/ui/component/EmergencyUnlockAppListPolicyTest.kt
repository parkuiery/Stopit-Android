package com.uiery.keep.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockAppListPolicyTest {

    @Test
    fun appsAreOrderedByLabelSoTheListStopsFollowingStorageOrder() {
        val ordered = EmergencyUnlockAppListPolicy.orderApps(
            listOf(
                app("com.example.zeta", "Zeta"),
                app("com.example.alpha", "alpha"),
                app("com.example.beta", "Beta"),
            ),
        )

        assertEquals(
            listOf("alpha", "Beta", "Zeta"),
            ordered.map { it.label },
        )
    }

    @Test
    fun appsSharingALabelFallBackToPackageNameForAStableOrder() {
        val ordered = EmergencyUnlockAppListPolicy.orderApps(
            listOf(
                app("com.example.second", "Same"),
                app("com.example.first", "Same"),
            ),
        )

        assertEquals(
            listOf("com.example.first", "com.example.second"),
            ordered.map { it.packageName },
        )
    }

    @Test
    fun blankQueryKeepsEveryApp() {
        val apps = listOf(app("com.example.alpha", "Alpha"), app("com.example.beta", "Beta"))

        assertEquals(apps, EmergencyUnlockAppListPolicy.filterApps(apps, ""))
        assertEquals(apps, EmergencyUnlockAppListPolicy.filterApps(apps, "   "))
    }

    @Test
    fun queryMatchesLabelsCaseInsensitivelyAndIgnoresSurroundingSpace() {
        val apps = listOf(
            app("com.instagram.android", "Instagram"),
            app("com.google.android.youtube", "YouTube"),
        )

        assertEquals(
            listOf("com.instagram.android"),
            EmergencyUnlockAppListPolicy.filterApps(apps, "  gram ").map { it.packageName },
        )
    }

    @Test
    fun queryAlsoMatchesPackageNamesSoUnresolvedLabelsStayReachable() {
        val apps = listOf(app("com.samsung.android.spay", "com.samsung.android.spay"))

        assertEquals(apps, EmergencyUnlockAppListPolicy.filterApps(apps, "samsung"))
    }

    @Test
    fun queryWithNoMatchReturnsNothing() {
        val apps = listOf(app("com.instagram.android", "Instagram"))

        assertTrue(EmergencyUnlockAppListPolicy.filterApps(apps, "zzz").isEmpty())
    }

    @Test
    fun searchStaysHiddenForShortListsAndAppearsOnceScanningGetsHard() {
        assertFalse(EmergencyUnlockAppListPolicy.showsSearch(1))
        assertFalse(EmergencyUnlockAppListPolicy.showsSearch(EmergencyUnlockAppListPolicy.SEARCH_VISIBILITY_THRESHOLD))
        assertTrue(EmergencyUnlockAppListPolicy.showsSearch(EmergencyUnlockAppListPolicy.SEARCH_VISIBILITY_THRESHOLD + 1))
    }

    private fun app(packageName: String, label: String) =
        EmergencyUnlockSelectableApp(packageName = packageName, label = label)
}
