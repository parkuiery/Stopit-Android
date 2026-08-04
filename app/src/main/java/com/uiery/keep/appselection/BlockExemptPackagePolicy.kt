package com.uiery.keep.appselection

/**
 * Packages exempt from blocking, split by how much authority they yield to.
 *
 * [homePackages] is unconditional. Blocking the home launcher traps the user in a
 * home -> block screen -> home loop, and no lock is worth making the device unusable.
 *
 * [essentialPackages] only applies to self-imposed locks. Parent mode is an allowlist a supervisor
 * configured deliberately, so it keeps authority over settings and wallet apps — otherwise the
 * supervised user could walk into Settings and turn the accessibility service off.
 */
data class BlockExemptPackages(
    val homePackages: Set<String> = emptySet(),
    val essentialPackages: Set<String> = emptySet(),
) {
    /** Everything a user must never be able to pick as a blocking target. */
    val all: Set<String> = homePackages + essentialPackages
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
 * Pure policy for packages that must stay reachable even while a lock is active.
 *
 * Users never pick these deliberately; they reach the blocked set through the "all apps"
 * select-all, and then crowd out the real target in the emergency unlock picker.
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
    ): BlockExemptPackages = BlockExemptPackages(
        homePackages = realPackages(homePackages),
        essentialPackages = realPackages(
            PAYMENT_PACKAGES + listOfNotNull(settingsPackage, dialerPackage, smsPackage, nfcPaymentPackage),
        ),
    )

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
