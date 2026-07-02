package com.uiery.keep.appselection

/**
 * Pure policy for the app-selection picker.
 *
 * Stopit needs broad package visibility only to let the user choose which launchable apps
 * should be blocked. Keep Android framework queries in [InstalledAppRepository] and keep
 * this filtering contract testable on the JVM.
 */
data class SelectableAppCandidate(
    val packageName: String,
    val appName: String,
    val hasLaunchIntent: Boolean,
)

object SelectableAppPolicy {
    fun filterSelectableApps(
        candidates: List<SelectableAppCandidate>,
        ownPackageName: String = "com.uiery.keep",
    ): List<SelectableAppCandidate> = candidates
        .asSequence()
        .filter { candidate -> candidate.hasLaunchIntent }
        .filterNot { candidate -> candidate.packageName == ownPackageName }
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, SelectableAppCandidate::appName)
                .thenBy { it.packageName },
        )
        .toList()
}
