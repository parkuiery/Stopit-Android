package com.uiery.keep.appselection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
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
 *
 * The reassignable roles are re-resolved on the schedule in [BlockExemptRoleCachePolicy], because a
 * user can change their default dialer or wallet at any time and no broadcast tells us. The home
 * launcher is not — see [homePackages].
 */
@Singleton
class AndroidBlockExemptPackageProvider(
    private val context: Context,
    private val elapsedRealtimeMillis: () -> Long,
) : BlockExemptPackageProvider {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, SystemClock::elapsedRealtime)

    /**
     * The home launcher, resolved once per process and then frozen.
     *
     * Re-resolving it would be the more honest answer — the user can change launchers too — but a
     * failed re-resolution reads as "this device has no launcher", and an empty home set makes the
     * launcher blockable, which traps the user in a home -> block screen -> home loop. Freezing the
     * first answer means a launcher change is not picked up until the process restarts. That is the
     * lesser of the two failures and is tracked separately.
     */
    private val homePackages: Set<String> by lazy { resolveHomePackages(context.packageManager) }

    /**
     * The last resolution, held as one object so a reader never pairs fresh packages with a stale
     * timestamp.
     */
    private class Resolution(
        val packages: BlockExemptPackages,
        val roles: ReassignableDeviceRoles,
        val atElapsedRealtime: Long,
    )

    @Volatile
    private var resolution: Resolution? = null

    override fun exemptPackages(): BlockExemptPackages {
        val now = elapsedRealtimeMillis()
        val current = resolution
        if (current != null && BlockExemptRoleCachePolicy.isFresh(current.atElapsedRealtime, now)) {
            return current.packages
        }
        // Two callers arriving together may both resolve. The lookups are idempotent and the loser
        // just overwrites with an equally fresh answer, which is cheaper than holding a lock across
        // binder calls on the path a block decision waits on.
        val roles = BlockExemptRoleCachePolicy.keepingResolvedRoles(
            resolved = resolveReassignableRoles(),
            previous = current?.roles,
        )
        val packages = BlockExemptPackagePolicy.exemptPackages(
            homePackages = homePackages,
            settingsPackage = roles.settingsPackage,
            dialerPackage = roles.dialerPackage,
            smsPackage = roles.smsPackage,
            nfcPaymentPackage = roles.nfcPaymentPackage,
        )
        resolution = Resolution(packages = packages, roles = roles, atElapsedRealtime = now)
        return packages
    }

    private fun resolveReassignableRoles(): ReassignableDeviceRoles {
        val packageManager = context.packageManager
        return ReassignableDeviceRoles(
            settingsPackage = resolvePackage(packageManager, Intent(Settings.ACTION_SETTINGS)),
            dialerPackage = defaultDialerPackage()
                ?: resolvePackage(packageManager, Intent(Intent.ACTION_DIAL)),
            smsPackage = defaultSmsPackage(),
            nfcPaymentPackage = defaultNfcPaymentPackage(),
        )
    }

    /**
     * The active home launcher only. Enumerating every installed launcher would mean a broad
     * package query, which `docs/QUERY_ALL_PACKAGES_POLICY.md` keeps confined to
     * [InstalledAppRepository], so this resolves the single current holder instead.
     */
    private fun resolveHomePackages(packageManager: PackageManager): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return setOfNotNull(resolvePackage(packageManager, homeIntent))
    }

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
