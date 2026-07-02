package com.uiery.keep.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppDisplayMetadataResolverTest {

    private val packageManager = mock(PackageManager::class.java)
    private val resolver = AppDisplayMetadataResolver(packageManager)

    @Test
    fun resolve_returnsLabelAndIconForInstalledPackage() {
        val appInfo = ApplicationInfo().apply { packageName = PACKAGE_NAME }
        val icon = mock(Drawable::class.java)
        `when`(packageManager.getApplicationInfo(PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo)).thenReturn("Example App")
        `when`(packageManager.getApplicationIcon(appInfo)).thenReturn(icon)

        val metadata = resolver.resolve(PACKAGE_NAME)

        assertEquals(PACKAGE_NAME, metadata.packageName)
        assertEquals("Example App", metadata.label)
        assertSame(icon, metadata.icon)
        assertEquals("Example App", metadata.contentDescription)
    }

    @Test
    fun resolve_usesPlaceholderIconWhenPackageLookupFails() {
        val placeholder = mock(Drawable::class.java)
        `when`(packageManager.getApplicationInfo(MISSING_PACKAGE_NAME, 0))
            .thenThrow(PackageManager.NameNotFoundException(MISSING_PACKAGE_NAME))
        `when`(packageManager.defaultActivityIcon).thenReturn(placeholder)

        val metadata = resolver.resolve(MISSING_PACKAGE_NAME)

        assertEquals(MISSING_PACKAGE_NAME, metadata.packageName)
        assertEquals(MISSING_PACKAGE_NAME, metadata.label)
        assertSame(placeholder, metadata.icon)
        assertEquals(MISSING_PACKAGE_NAME, metadata.contentDescription)
    }

    @Test
    fun resolve_keepsLabelAndUsesPlaceholderWhenIconLookupFails() {
        val appInfo = ApplicationInfo().apply { packageName = PACKAGE_NAME }
        val placeholder = mock(Drawable::class.java)
        `when`(packageManager.getApplicationInfo(PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo)).thenReturn("Example App")
        `when`(packageManager.getApplicationIcon(appInfo))
            .thenThrow(RuntimeException("icon unavailable"))
        `when`(packageManager.defaultActivityIcon).thenReturn(placeholder)

        val metadata = resolver.resolve(PACKAGE_NAME)

        assertEquals("Example App", metadata.label)
        assertSame(placeholder, metadata.icon)
        assertEquals("Example App", metadata.contentDescription)
    }

    @Test
    fun resolve_fallsBackToPackageNameAndKeepsPlaceholderWhenLabelLookupFails() {
        val appInfo = ApplicationInfo().apply { packageName = PACKAGE_NAME }
        val placeholder = mock(Drawable::class.java)
        `when`(packageManager.getApplicationInfo(PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo))
            .thenThrow(RuntimeException("label unavailable"))
        `when`(packageManager.getApplicationIcon(appInfo))
            .thenThrow(RuntimeException("icon unavailable"))
        `when`(packageManager.defaultActivityIcon).thenReturn(placeholder)

        val metadata = resolver.resolve(PACKAGE_NAME)

        assertEquals(PACKAGE_NAME, metadata.label)
        assertSame(placeholder, metadata.icon)
        assertEquals(PACKAGE_NAME, metadata.contentDescription)
    }

    @Test
    fun resolve_fallsBackToPackageNameWhenLabelIsBlank() {
        val appInfo = ApplicationInfo().apply { packageName = PACKAGE_NAME }
        val icon = mock(Drawable::class.java)
        `when`(packageManager.getApplicationInfo(PACKAGE_NAME, 0)).thenReturn(appInfo)
        `when`(packageManager.getApplicationLabel(appInfo)).thenReturn("   ")
        `when`(packageManager.getApplicationIcon(appInfo)).thenReturn(icon)

        val metadata = resolver.resolve(PACKAGE_NAME)

        assertEquals(PACKAGE_NAME, metadata.label)
        assertSame(icon, metadata.icon)
        assertEquals(PACKAGE_NAME, metadata.contentDescription)
    }

    private companion object {
        const val PACKAGE_NAME = "com.example.app"
        const val MISSING_PACKAGE_NAME = "com.example.deleted"
    }
}
