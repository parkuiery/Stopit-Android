package com.uiery.keep.feature.home.appselection

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class InstalledAppRepositoryTest {

    private val packageManager = mock(PackageManager::class.java)
    private val repository = InstalledAppRepository(
        packageManager = packageManager,
        ownPackageName = OWN_PACKAGE_NAME,
    )

    @Test
    fun loadSelectableAppsUsesSharedPackageNameFallbackWhenLabelLookupFails() {
        val appInfo = ApplicationInfo().apply { packageName = TARGET_PACKAGE_NAME }
        val placeholder = mock(Drawable::class.java)
        `when`(packageManager.getInstalledApplications(PackageManager.GET_META_DATA))
            .thenReturn(listOf(appInfo))
        `when`(packageManager.getApplicationInfo(TARGET_PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo))
            .thenThrow(RuntimeException("label unavailable"))
        `when`(packageManager.getLaunchIntentForPackage(TARGET_PACKAGE_NAME))
            .thenReturn(Intent())
        `when`(packageManager.getApplicationIcon(appInfo)).thenReturn(placeholder)

        val apps = repository.loadSelectableApps()

        assertEquals(1, apps.size)
        assertEquals(TARGET_PACKAGE_NAME, apps.single().packageName)
        assertEquals(TARGET_PACKAGE_NAME, apps.single().appName)
        assertSame(placeholder, apps.single().appIcon)
    }

    @Test
    fun loadSelectableAppsUsesSharedPlaceholderIconWhenIconLookupFails() {
        val appInfo = ApplicationInfo().apply { packageName = TARGET_PACKAGE_NAME }
        val placeholder = mock(Drawable::class.java)
        `when`(packageManager.getInstalledApplications(PackageManager.GET_META_DATA))
            .thenReturn(listOf(appInfo))
        `when`(packageManager.getApplicationInfo(TARGET_PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo)).thenReturn("Target App")
        `when`(packageManager.getLaunchIntentForPackage(TARGET_PACKAGE_NAME))
            .thenReturn(Intent())
        `when`(packageManager.getApplicationIcon(appInfo))
            .thenThrow(RuntimeException("icon unavailable"))
        `when`(packageManager.defaultActivityIcon).thenReturn(placeholder)

        val apps = repository.loadSelectableApps()

        assertEquals(1, apps.size)
        assertEquals("Target App", apps.single().appName)
        assertSame(placeholder, apps.single().appIcon)
    }

    @Test
    fun loadSelectableAppsStillFiltersOwnPackageAndNonLaunchableApps() {
        val ownApp = ApplicationInfo().apply { packageName = OWN_PACKAGE_NAME }
        val launchableApp = ApplicationInfo().apply { packageName = TARGET_PACKAGE_NAME }
        val nonLaunchableApp = ApplicationInfo().apply { packageName = NON_LAUNCHABLE_PACKAGE_NAME }
        val icon = mock(Drawable::class.java)
        `when`(packageManager.getInstalledApplications(PackageManager.GET_META_DATA))
            .thenReturn(listOf(ownApp, launchableApp, nonLaunchableApp))
        listOf(ownApp, launchableApp, nonLaunchableApp).forEach { app ->
            `when`(packageManager.getApplicationInfo(app.packageName, 0)).thenReturn(app)
            `when`(packageManager.getApplicationLabel(app)).thenReturn(app.packageName)
            `when`(packageManager.getApplicationIcon(app)).thenReturn(icon)
        }
        `when`(packageManager.getLaunchIntentForPackage(OWN_PACKAGE_NAME)).thenReturn(Intent())
        `when`(packageManager.getLaunchIntentForPackage(TARGET_PACKAGE_NAME)).thenReturn(Intent())
        `when`(packageManager.getLaunchIntentForPackage(NON_LAUNCHABLE_PACKAGE_NAME)).thenReturn(null)

        val apps = repository.loadSelectableApps()

        assertEquals(listOf(TARGET_PACKAGE_NAME), apps.map { it.packageName })
    }

    private companion object {
        const val OWN_PACKAGE_NAME = "com.uiery.keep.dev"
        const val TARGET_PACKAGE_NAME = "com.example.target"
        const val NON_LAUNCHABLE_PACKAGE_NAME = "com.example.serviceonly"
    }
}
