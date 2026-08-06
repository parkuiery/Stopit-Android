package com.uiery.keep.websiteblocking

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepDnsVpnSpikeActivityTest {
    @Test
    fun existingActivityHandlesAReplacementLaunchIntent() {
        val stopIntent = Intent(
            KeepDnsVpnService.ACTION_STOP,
        ).setClassName(
            "com.uiery.keep.dev",
            KeepDnsVpnSpikeActivity::class.java.name,
        ).putExtra(KeepDnsVpnService.EXTRA_STOP, true)

        val scenario = ActivityScenario.launch<KeepDnsVpnSpikeActivity>(stopIntent)
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val statusView = content.getChildAt(0) as TextView
            assertEquals(
                activity.getString(R.string.website_blocking_spike_stopped),
                statusView.text,
            )
            val activityInfo = activity.packageManager.getActivityInfo(
                activity.componentName,
                PackageManager.ComponentInfoFlags.of(0),
            )
            assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activityInfo.launchMode)

            val invalidStartIntent = Intent(KeepDnsVpnService.ACTION_START)
                .setClass(activity, KeepDnsVpnSpikeActivity::class.java)
                .putExtra(KeepDnsVpnService.EXTRA_DOMAIN, "not a domain")
            activity.onNewIntent(invalidStartIntent)
            assertEquals(
                activity.getString(R.string.website_blocking_spike_invalid_domain),
                statusView.text,
            )
            activity.finish()
        }
    }
}
