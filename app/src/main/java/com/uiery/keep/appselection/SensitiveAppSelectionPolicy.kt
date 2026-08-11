package com.uiery.keep.appselection

/** A sensitive app the user is about to block, with the role that explains what it costs. */
data class SensitiveAppSelection(
    val packageName: String,
    val appName: String,
    val role: SensitiveAppRole,
)

/**
 * Pure policy for the confirmation shown before a blocking selection is saved.
 *
 * 1.7.12 hid settings, dialer, messaging and wallet from the picker so "select all" could not sweep
 * them in. That also made them impossible to block on purpose, which is a core reason people install
 * a focus app. They are selectable again, and this is what keeps the sweep from being silent.
 */
object SensitiveAppSelectionPolicy {
    /**
     * The sensitive apps in [selectedPackages], ordered by [SensitiveAppRole] so the list reads the
     * same way every time. Empty means the selection can be saved without asking.
     *
     * Deliberately blind to how a package got selected: "select all" and an individual tap both land
     * here. A confirmation that only guards one path leaves the other silent, which is the failure
     * this replaces.
     *
     * A package with no entry in [appNamesByPackage] is dropped rather than shown by package id — a
     * confirmation the user cannot read is worse than one line short, and the picker always has a
     * label for anything it let them select.
     */
    fun pendingConfirmations(
        selectedPackages: Set<String>,
        appNamesByPackage: Map<String, String>,
        exemptPackages: BlockExemptPackages,
    ): List<SensitiveAppSelection> =
        BlockExemptPackagePolicy
            .sensitiveSelections(selectedPackages, exemptPackages)
            .mapNotNull { (packageName, role) ->
                appNamesByPackage[packageName]?.let { appName ->
                    SensitiveAppSelection(packageName, appName, role)
                }
            }
            .sortedWith(compareBy({ it.role.ordinal }, { it.appName }))

    /** The selection with every sensitive app removed, for "continue without them". */
    fun withoutSensitiveApps(
        selectedPackages: Set<String>,
        exemptPackages: BlockExemptPackages,
    ): Set<String> = selectedPackages.filterNotTo(linkedSetOf()) { packageName ->
        packageName in exemptPackages.sensitivePackages
    }
}
