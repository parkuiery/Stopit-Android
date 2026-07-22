package com.uiery.keep.manifest

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.websiteblocking.KeepDnsVpnSpikeActivity
import com.uiery.keep.websiteblocking.KeepDnsVpnSpikeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebsiteBlockingSpikeManifestTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun devManifestDeclaresVpnSpikePermissions() {
        val permissions = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        ).requestedPermissions.orEmpty().toSet()

        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE))
    }

    @Test
    fun vpnSpikeActivityIsExportedForAdbWithoutLauncherIntent() {
        val info = activityInfo(KeepDnsVpnSpikeActivity::class.java)

        assertTrue(info.exported)
        assertFalse(
            matchingActivityClassNames(Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER)
                .contains(KeepDnsVpnSpikeActivity::class.java.name),
        )
    }

    @Test
    fun vpnSpikeServiceIsNonExportedAndBoundAsVpnService() {
        val info = serviceInfo(KeepDnsVpnSpikeService::class.java)

        assertFalse(info.exported)
        assertEquals(Manifest.permission.BIND_VPN_SERVICE, info.permission)
        assertTrue(matchingServiceClassNames(VpnService.SERVICE_INTERFACE).contains(info.name))
    }

    @Test
    fun vpnSpikeServiceDeclaresSpecialUseForegroundTypeAndVpnMetadata() {
        val info = serviceInfo(KeepDnsVpnSpikeService::class.java)

        assertTrue((info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) != 0)
        assertEquals(
            false,
            info.metaData?.getBoolean("android.net.VpnService.SUPPORTS_ALWAYS_ON"),
        )
        assertTrue(
            context.packageManager.getProperty(
                "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
                ComponentName(context, KeepDnsVpnSpikeService::class.java),
            ).string
                ?.contains("local DNS website blocking spike", ignoreCase = true) == true,
        )
    }

    private fun matchingActivityClassNames(action: String, category: String): Set<String> {
        val intent = Intent(action).addCategory(category).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        ).mapNotNull { it.activityInfo?.name }.toSet()
    }

    private fun matchingServiceClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        return context.packageManager.queryIntentServices(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        ).mapNotNull { it.serviceInfo?.name }.toSet()
    }

    private fun activityInfo(activityClass: Class<*>): ActivityInfo {
        return context.packageManager.getActivityInfo(
            ComponentName(context, activityClass),
            PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    }

    private fun serviceInfo(serviceClass: Class<*>): ServiceInfo {
        return context.packageManager.getServiceInfo(
            ComponentName(context, serviceClass),
            PackageManager.ComponentInfoFlags.of((PackageManager.GET_META_DATA or PackageManager.MATCH_ALL).toLong()),
        )
    }
}
