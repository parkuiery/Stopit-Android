package com.uiery.keep.appselection

/**
 * Supplies the packages Stopit must never block on this device.
 *
 * Resolving them needs Android system services, so [AndroidBlockExemptPackageProvider] owns the
 * framework lookups and policy code stays JVM-testable behind this interface.
 */
fun interface BlockExemptPackageProvider {
    fun exemptPackages(): Set<String>

    companion object {
        /** No exemptions. Only for tests and previews that never reach a real device. */
        val None = BlockExemptPackageProvider { emptySet() }
    }
}

/**
 * Pure policy for packages that must stay reachable even while a lock is active.
 *
 * Blocking the home launcher traps the user in a home -> block screen -> home loop with no way out,
 * and blocking the dialer, messaging, settings or wallet apps removes the escape hatches people
 * need in an emergency. Users never pick these deliberately; they reach the blocked set through the
 * "all apps" select-all, and then crowd out the real target in the emergency unlock picker.
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
    ): Set<String> = (
        PAYMENT_PACKAGES +
            homePackages +
            listOfNotNull(settingsPackage, dialerPackage, smsPackage, nfcPaymentPackage)
        )
        .filterNotTo(linkedSetOf()) { packageName ->
            packageName.isBlank() || packageName == SYSTEM_RESOLVER_PACKAGE
        }

    fun isExempt(packageName: String, exemptPackages: Set<String>): Boolean =
        packageName in exemptPackages

    fun filterBlockable(packages: Set<String>, exemptPackages: Set<String>): Set<String> {
        if (exemptPackages.isEmpty()) return packages
        return packages.filterNotTo(linkedSetOf()) { packageName ->
            isExempt(packageName, exemptPackages)
        }
    }
}
