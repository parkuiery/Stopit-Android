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
import com.uiery.keep.websiteblocking.KeepDnsVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestContractIntegrationTest {
    // Static policy shape (sensitive permissions, exported flags, accessibility metadata,
    // and backup/data-extraction XML scope) is locked by
    // scripts.tests.test_android_manifest_contract so PR/release gates can fail before the
    // emulator starts. This class stays focused on PackageManager/runtime resolution.
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

    /*
     * 웹 차단 VpnService. 이 셋은 소스 매니페스트가 아니라 **병합된 매니페스트**에서 확인해야
     * 하는 것들이다. 1.8.0 이 Play 에서 막힌 자리가 정확히 여기라, 정적 테스트
     * (scripts/tests/test_website_blocking_manifest_boundary.py) 만으로는 부족하다. 그 테스트는
     * app/src/main 과 app/src/dev 를 읽지, 플레이버 병합 결과나 설치된 패키지를 보지 않는다.
     *
     * dev 쪽은 androidTestDev 의 WebsiteBlockingSpikeManifestTest 가 덮고 있었지만 prod 쪽에는
     * 대응하는 런타임 게이트가 없었다. 이 테스트는 prodDebug 스모크에서도 도므로 프로덕션
     * 패키지의 병합 결과를 실제로 확인한다.
     */
    @Test
    fun manifestRegistersDnsVpnServiceForTheSystemVpnAction() {
        assertTrue(
            matchingServiceClassNames(VPN_SERVICE_ACTION).contains(KeepDnsVpnService::class.java.name),
        )
    }

    @Test
    fun manifestDeclaresDnsVpnServiceAsNonExportedWithSystemBindPermission() {
        val info = serviceInfo(KeepDnsVpnService::class.java)

        // 익스포트되면 아무 앱이나 VPN 을 세우라고 시킬 수 있다. 시스템만 바인드해야 한다.
        assertFalse(info.exported)
        assertEquals(VPN_BIND_PERMISSION, info.permission)
    }

    @Test
    fun manifestDeclaresDnsVpnServiceAsSpecialUseForegroundService() {
        // 이 값이 빠진 채로 startForeground 를 부르면 Android 14+ 에서 죽고, 값이 있는데 Play
        // 신고가 없으면 업로드가 막힌다. 둘 다 릴리즈를 세우는 실패라 실기기에서 확인한다.
        //
        // specialUse 는 API 34 에서 생긴 값이라 그 아래 플랫폼은 파싱하지 않고 0 을 돌려준다.
        // minSdk 가 33 이므로 API 33 기기에서 이걸 실패로 읽으면 안 된다. 포그라운드 서비스
        // 타입 요구 자체가 Android 14+ 의 것이고, 그 아래에서는 선언이 무의미하다.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo(KeepDnsVpnService::class.java).foregroundServiceType,
        )
    }

    private fun matchingReceiverClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        val receivers = context.packageManager.queryBroadcastReceivers(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )

        return receivers.mapNotNull { it.activityInfo?.name }.toSet()
    }

    private fun matchingServiceClassNames(action: String): Set<String> {
        val intent = Intent(action).setPackage(context.packageName)
        val services = context.packageManager.queryIntentServices(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )

        return services.mapNotNull { it.serviceInfo?.name }.toSet()
    }

    private fun serviceInfo(serviceClass: Class<*>): ServiceInfo {
        val componentName = ComponentName(context, serviceClass)
        return context.packageManager.getServiceInfo(
            componentName,
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
    }

    private companion object {
        const val MESSAGING_EVENT_ACTION = "com.google.firebase.MESSAGING_EVENT"
        const val ACCESSIBILITY_BIND_PERMISSION = "android.permission.BIND_ACCESSIBILITY_SERVICE"
        const val ACCESSIBILITY_METADATA_NAME = "android.accessibilityservice"
        const val VPN_SERVICE_ACTION = "android.net.VpnService"
        const val VPN_BIND_PERMISSION = "android.permission.BIND_VPN_SERVICE"
    }
}
