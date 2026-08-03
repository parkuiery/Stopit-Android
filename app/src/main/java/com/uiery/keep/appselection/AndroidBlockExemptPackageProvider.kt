package com.uiery.keep.appselection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the device roles Stopit must never block.
 *
 * Every lookup here is best-effort: a device without a dialer, a wallet or a resolvable settings
 * activity simply contributes no exemption instead of failing the blocking pipeline.
 */
@Singleton
class AndroidBlockExemptPackageProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlockExemptPackageProvider {

    private val resolvedPackages: Set<String> by lazy { resolvePackages() }

    override fun exemptPackages(): Set<String> = resolvedPackages

    private fun resolvePackages(): Set<String> {
        val packageManager = context.packageManager
        return BlockExemptPackagePolicy.exemptPackages(
            homePackages = homePackages(packageManager),
            settingsPackage = resolvePackage(packageManager, Intent(Settings.ACTION_SETTINGS)),
            dialerPackage = defaultDialerPackage()
                ?: resolvePackage(packageManager, Intent(Intent.ACTION_DIAL)),
            smsPackage = defaultSmsPackage(),
            nfcPaymentPackage = defaultNfcPaymentPackage(),
        )
    }

    /**
     * Every installed launcher, not just the active one, so switching launchers mid-lock cannot
     * strand the user on a blocked home screen.
     */
    private fun homePackages(packageManager: PackageManager): Set<String> = runCatching {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName }
    }.getOrDefault(emptySet())

    private fun resolvePackage(packageManager: PackageManager, intent: Intent): String? = runCatching {
        intent.resolveActivity(packageManager)?.packageName
    }.getOrNull()

    private fun defaultDialerPackage(): String? = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()

    private fun defaultSmsPackage(): String? = runCatching {
        Telephony.Sms.getDefaultSmsPackage(context)
    }.getOrNull()

    /**
     * The wallet the user actually tap-to-pays with. Stored as a flattened component name and not
     * exposed through a typed API, so a missing or malformed value just means no exemption.
     */
    private fun defaultNfcPaymentPackage(): String? = runCatching {
        Settings.Secure
            .getString(context.contentResolver, NFC_PAYMENT_DEFAULT_COMPONENT)
            ?.let(ComponentName::unflattenFromString)
            ?.packageName
    }.getOrNull()

    private companion object {
        const val NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component"
    }
}
