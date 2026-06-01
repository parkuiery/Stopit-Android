package com.uiery.keep.manifest

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.R
import com.uiery.keep.receiver.BootReceiver
import com.uiery.keep.service.KeepAccessibilityService
import com.uiery.keep.service.KeepMessagingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestContractIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manifestRegistersBootReceiverForBootCompleted() {
        assertTrue(
            matchingReceiverClassNames(Intent.ACTION_BOOT_COMPLETED).contains(BootReceiver::class.java.name),
        )
    }

    @Test
    fun manifestRegistersBootReceiverForMyPackageReplaced() {
        assertTrue(
            matchingReceiverClassNames(Intent.ACTION_MY_PACKAGE_REPLACED).contains(BootReceiver::class.java.name),
        )
    }

    @Test
    fun manifestRegistersKeepMessagingServiceForMessagingEvent() {
        assertTrue(
            matchingServiceClassNames(MESSAGING_EVENT_ACTION).contains(KeepMessagingService::class.java.name),
        )
    }

    @Test
    fun manifestDeclaresKeepMessagingServiceAsNonExported() {
        assertFalse(serviceInfo(KeepMessagingService::class.java).exported)
    }

    @Test
    fun manifestDeclaresKeepAccessibilityServiceBindPermission() {
        assertEquals(
            ACCESSIBILITY_BIND_PERMISSION,
            serviceInfo(KeepAccessibilityService::class.java).permission,
        )
    }

    @Test
    fun manifestDeclaresKeepAccessibilityServiceMetadata() {
        assertEquals(
            R.xml.accessibility_service_config,
            serviceInfo(KeepAccessibilityService::class.java).metaData?.getInt(ACCESSIBILITY_METADATA_NAME),
        )
    }

    private fun matchingReceiverClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        val receivers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
        }

        return receivers.mapNotNull { it.activityInfo?.name }.toSet()
    }

    private fun matchingServiceClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }

        return services.mapNotNull { it.serviceInfo?.name }.toSet()
    }

    private fun serviceInfo(serviceClass: Class<*>): ServiceInfo {
        val componentName = ComponentName(context, serviceClass)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        }
    }

    private companion object {
        const val MESSAGING_EVENT_ACTION = "com.google.firebase.MESSAGING_EVENT"
        const val ACCESSIBILITY_BIND_PERMISSION = "android.permission.BIND_ACCESSIBILITY_SERVICE"
        const val ACCESSIBILITY_METADATA_NAME = "android.accessibilityservice"
    }
}
