package com.uiery.keep.appselection

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectableAppPolicyTest {

    @Test
    fun selectableAppsExcludeSelfAndAppsWithoutLaunchIntent() {
        val candidates = listOf(
            SelectableAppCandidate(
                packageName = "com.uiery.keep",
                appName = "Stopit",
                hasLaunchIntent = true,
            ),
            SelectableAppCandidate(
                packageName = "com.example.visible",
                appName = "Visible App",
                hasLaunchIntent = true,
            ),
            SelectableAppCandidate(
                packageName = "com.example.hidden",
                appName = "Hidden Service",
                hasLaunchIntent = false,
            ),
        )

        val result = SelectableAppPolicy.filterSelectableApps(candidates)

        assertEquals(listOf("com.example.visible"), result.map { it.packageName })
    }

    @Test
    fun selectableAppsAreSortedByLabelForStablePickerOrder() {
        val candidates = listOf(
            SelectableAppCandidate(
                packageName = "com.example.zeta",
                appName = "Zeta",
                hasLaunchIntent = true,
            ),
            SelectableAppCandidate(
                packageName = "com.example.alpha",
                appName = "alpha",
                hasLaunchIntent = true,
            ),
            SelectableAppCandidate(
                packageName = "com.example.beta",
                appName = "Beta",
                hasLaunchIntent = true,
            ),
        )

        val result = SelectableAppPolicy.filterSelectableApps(candidates)

        assertEquals(
            listOf("com.example.alpha", "com.example.beta", "com.example.zeta"),
            result.map { it.packageName },
        )
    }
}
