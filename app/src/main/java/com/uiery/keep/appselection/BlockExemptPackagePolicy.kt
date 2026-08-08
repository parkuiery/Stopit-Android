package com.uiery.keep.appselection

/**
 * A device role whose app can be blocked, but not without telling the user what it costs.
 *
 * The role is what makes the warning specific: "you will not receive verification codes" lands,
 * "this is a system app" does not.
 */
enum class SensitiveAppRole {
    SETTINGS,
    DIALER,
    MESSAGING,
    WALLET,
}

/**
 * Device-role packages, split by whether the user is allowed to block them.
 *
 * [homePackages] is the only unconditional exemption. Blocking the home launcher traps the user in
 * a home -> block screen -> home loop, and no lock is worth making the device unusable. It is never
 * selectable and never blockable.
 *
 * [sensitiveRoles] — settings, dialer, messaging, wallet — *are* blockable. Blocking a messaging app
 * is a core reason people install a focus app, and 1.7.12 made it impossible by hiding these from
 * the picker entirely. What that release actually needed to fix was that "select all" pulled them in
 * *silently*, so the fix is disclosure, not removal: they stay in the picker and in select all, and
 * the caller confirms them before saving.
 */
data class BlockExemptPackages(
    val homePackages: Set<String> = emptySet(),
    val sensitiveRoles: Map<String, SensitiveAppRole> = emptyMap(),
) {
    val sensitivePackages: Set<String> = sensitiveRoles.keys

    /**
     * The dialer, reachable on its own because an incoming call is not something the user chose to
     * do. With the screen off Android brings the dialer's in-call activity to the front, which is a
     * window change like any other, so a blocked dialer would put the block screen over a ringing
     * phone. Blocking decisions step aside for this package while a call is live; placing a call is
     * still blocked.
     */
    val dialerPackages: Set<String> = packagesFor(SensitiveAppRole.DIALER)

    private fun packagesFor(role: SensitiveAppRole): Set<String> =
        sensitiveRoles.filterValues { it == role }.keys

    /**
     * Every resolved device role. Blocking decisions must not use this — they only exempt
     * [homePackages]. This is for surfaces that describe the device rather than restrict it, such
     * as leaving system roles out of usage insight.
     */
    val all: Set<String> = homePackages + sensitivePackages
}

/**
 * Supplies the packages Stopit must never block on this device.
 *
 * Resolving them needs Android system services, so [AndroidBlockExemptPackageProvider] owns the
 * framework lookups and policy code stays JVM-testable behind this interface.
 */
fun interface BlockExemptPackageProvider {
    fun exemptPackages(): BlockExemptPackages

    companion object {
        /** No exemptions. Only for tests and previews that never reach a real device. */
        val None = BlockExemptPackageProvider { BlockExemptPackages() }
    }
}

/**
 * Pure policy for resolving the device roles a lock has to reason about.
 *
 * Only the home launcher must stay reachable. The rest are blockable but consequential enough that
 * the user should be told what they are giving up before the selection is saved.
 */
object BlockExemptPackagePolicy {
    /**
     * The package the framework reports when several activities handle an intent and no default is
     * set. It is a chooser placeholder, not a real app, so it must never become an exemption.
     */
    private const val SYSTEM_RESOLVER_PACKAGE = "android"

    /**
     * Wallet apps no device-role intent resolves to.
     *
     * The NFC default payment component covers whichever wallet the user actually set, so this list
     * only has to catch the wallets that ship preinstalled and stay unset until first use.
     */
    val PAYMENT_PACKAGES: Set<String> = setOf(
        "com.samsung.android.spay", // Samsung Wallet / Samsung Pay
        "com.samsung.android.spaylite", // Samsung Pay Mini
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.google.android.apps.nbu.paisa.user", // Google Pay
    )

    fun exemptPackages(
        homePackages: Set<String>,
        settingsPackage: String?,
        dialerPackage: String?,
        smsPackage: String?,
        nfcPaymentPackage: String?,
    ): BlockExemptPackages {
        val resolvedHomePackages = realPackages(homePackages)
        // Later entries win, so the resolved default dialer/messenger overrides a preinstalled
        // wallet entry if a device ever ships one package in both roles.
        val roles = buildMap {
            PAYMENT_PACKAGES.forEach { put(it, SensitiveAppRole.WALLET) }
            nfcPaymentPackage?.let { put(it, SensitiveAppRole.WALLET) }
            smsPackage?.let { put(it, SensitiveAppRole.MESSAGING) }
            dialerPackage?.let { put(it, SensitiveAppRole.DIALER) }
            settingsPackage?.let { put(it, SensitiveAppRole.SETTINGS) }
        }
        val realSensitivePackages = realPackages(roles.keys)
        return BlockExemptPackages(
            homePackages = resolvedHomePackages,
            // The home launcher is unconditional, so it must never also appear as sensitive —
            // otherwise the alert would offer to block something that cannot be blocked.
            sensitiveRoles = roles.filterKeys {
                it in realSensitivePackages && it !in resolvedHomePackages
            },
        )
    }

    /**
     * The sensitive roles a selection is about to block, keyed by package.
     *
     * This drives the confirmation shown before the selection is saved. It is deliberately blind to
     * how the packages got selected — "select all" and an individual tap both land here — because a
     * dialog that only guards one path leaves the other silent, which is the failure 1.7.12 tried to
     * fix by removing the apps entirely. Empty means there is nothing to confirm.
     */
    fun sensitiveSelections(
        selectedPackages: Set<String>,
        exemptPackages: BlockExemptPackages,
    ): Map<String, SensitiveAppRole> =
        exemptPackages.sensitiveRoles.filterKeys { it in selectedPackages }

    fun isExempt(packageName: String, exemptPackages: Set<String>): Boolean =
        packageName in exemptPackages

    fun filterBlockable(packages: Set<String>, exemptPackages: Set<String>): Set<String> {
        if (exemptPackages.isEmpty()) return packages
        return packages.filterNotTo(linkedSetOf()) { packageName ->
            isExempt(packageName, exemptPackages)
        }
    }

    private fun realPackages(packages: Collection<String>): Set<String> =
        packages.filterNotTo(linkedSetOf()) { packageName ->
            packageName.isBlank() || packageName == SYSTEM_RESOLVER_PACKAGE
        }
}
