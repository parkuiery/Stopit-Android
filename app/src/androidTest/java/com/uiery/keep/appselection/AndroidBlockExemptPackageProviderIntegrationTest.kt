package com.uiery.keep.appselection

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pure policy is covered on the JVM; this covers the half that talks to the framework, which is
 * the half that decides whether the right packages are exempt on a real device.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBlockExemptPackageProviderIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = AndroidBlockExemptPackageProvider(context)

    @Test
    fun resolvesTheActiveHomeLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = homeIntent.resolveActivity(context.packageManager)?.packageName

        // A device with no resolvable home activity has nothing to assert against.
        if (resolvedHome == null || resolvedHome == SYSTEM_RESOLVER_PACKAGE) return

        assertEquals(setOf(resolvedHome), provider.exemptPackages().homePackages)
    }

    @Test
    fun resolvesSettingsAsASensitiveRatherThanAHomePackage() {
        val settingsPackage = Intent(Settings.ACTION_SETTINGS)
            .resolveActivity(context.packageManager)
            ?.packageName ?: return

        val exempt = provider.exemptPackages()

        assertTrue(exempt.sensitivePackages.contains(settingsPackage))
        assertFalse(exempt.homePackages.contains(settingsPackage))
    }

    @Test
    fun neverExemptsThePlaceholderOrBlankPackages() {
        val exempt = provider.exemptPackages()

        assertFalse(exempt.all.contains(SYSTEM_RESOLVER_PACKAGE))
        assertTrue(exempt.all.none { it.isBlank() })
    }

    @Test
    fun curatedWalletPackagesSurviveResolutionOnEveryDevice() {
        assertTrue(provider.exemptPackages().sensitivePackages.containsAll(BlockExemptPackagePolicy.PAYMENT_PACKAGES))
    }

    @Test
    fun lookupsWithinTheTtlReuseTheSameResolution() {
        var now = 0L
        val provider = AndroidBlockExemptPackageProvider(context) { now }

        val first = provider.exemptPackages()
        now += BlockExemptRoleCachePolicy.TTL_MS - 1

        assertSame(first, provider.exemptPackages())
    }

    /**
     * The identity check is the point: on an unchanged device the two results are equal either way,
     * so only a distinct instance proves the roles were actually looked up again.
     */
    @Test
    fun theTtlElapsingResolvesTheRolesAgain() {
        var now = 0L
        val provider = AndroidBlockExemptPackageProvider(context) { now }

        val first = provider.exemptPackages()
        now += BlockExemptRoleCachePolicy.TTL_MS
        val second = provider.exemptPackages()

        assertNotSame(first, second)
        assertEquals(first, second)
    }

    /**
     * Re-resolution must never cost the device its launcher exemption — an empty home set is what
     * makes a home -> block screen -> home loop possible.
     */
    @Test
    fun reResolvingNeverDropsTheHomeLauncher() {
        var now = 0L
        val provider = AndroidBlockExemptPackageProvider(context) { now }

        val homePackages = provider.exemptPackages().homePackages
        repeat(3) {
            now += BlockExemptRoleCachePolicy.TTL_MS
            assertEquals(homePackages, provider.exemptPackages().homePackages)
        }
    }

    private companion object {
        const val SYSTEM_RESOLVER_PACKAGE = "android"
    }
}
