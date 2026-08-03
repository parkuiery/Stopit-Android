package com.uiery.keep.ui.component

/**
 * One selectable row in the emergency unlock app step.
 *
 * Icons stay out of this model so ordering and filtering can be verified on the JVM.
 */
internal data class EmergencyUnlockSelectableApp(
    val packageName: String,
    val label: String,
)

/**
 * Ordering and search rules for the emergency unlock app step.
 *
 * The blocked set arrives as a [Set] out of DataStore, whose iteration order is arbitrary. Rendering
 * it as-is scattered the apps a user actually wanted to unlock among everything else they had ever
 * blocked, so the step sorts by label and offers search once the list stops being scannable.
 */
internal object EmergencyUnlockAppListPolicy {
    /**
     * Up to this many rows fit on screen without hunting, so the search field would only take space
     * away from the list it is meant to help with.
     */
    const val SEARCH_VISIBILITY_THRESHOLD = 8

    fun showsSearch(appCount: Int): Boolean = appCount > SEARCH_VISIBILITY_THRESHOLD

    fun orderApps(apps: List<EmergencyUnlockSelectableApp>): List<EmergencyUnlockSelectableApp> =
        apps.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, EmergencyUnlockSelectableApp::label)
                .thenBy(EmergencyUnlockSelectableApp::packageName),
        )

    /**
     * Package names are matched too, so an app whose label could not be resolved — and therefore
     * renders as its package name — is still reachable through search.
     */
    fun filterApps(
        apps: List<EmergencyUnlockSelectableApp>,
        query: String,
    ): List<EmergencyUnlockSelectableApp> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return apps
        return apps.filter { app ->
            app.label.contains(trimmedQuery, ignoreCase = true) ||
                app.packageName.contains(trimmedQuery, ignoreCase = true)
        }
    }
}
