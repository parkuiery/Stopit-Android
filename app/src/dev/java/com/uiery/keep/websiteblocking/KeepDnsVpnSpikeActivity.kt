package com.uiery.keep.websiteblocking

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.DomainNameNormalizationResult
import com.uiery.keep.domain.websiteblocking.DomainNamePolicy

class KeepDnsVpnSpikeActivity : Activity() {
    private lateinit var statusView: TextView
    private var pendingDomain: DomainName = DEFAULT_DOMAIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 16f
        }
        setContentView(statusView)
        handleLaunchIntent(intent)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(launchIntent: Intent) {
        if (launchIntent.action == KeepDnsVpnService.ACTION_STOP ||
            launchIntent.getBooleanExtra(KeepDnsVpnService.EXTRA_STOP, false)
        ) {
            stopSpikeService()
            return
        }

        val domainResult = DomainNamePolicy.normalize(
            launchIntent.getStringExtra(KeepDnsVpnService.EXTRA_DOMAIN).orEmpty()
                .ifBlank { DEFAULT_DOMAIN.value },
        )
        val normalizedDomain = (domainResult as? DomainNameNormalizationResult.Valid)?.domain
        if (normalizedDomain == null) {
            statusView.setText(R.string.website_blocking_spike_invalid_domain)
            return
        }
        pendingDomain = normalizedDomain
        requestVpnConsent()
    }

    @Deprecated("Deprecated by Android framework; sufficient for this adb-only dev spike.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != VPN_CONSENT_REQUEST_CODE) return

        if (resultCode == RESULT_OK) {
            startSpikeService(pendingDomain)
            statusView.text = getString(R.string.website_blocking_spike_started, 1)
        } else {
            statusView.setText(R.string.website_blocking_spike_consent_denied)
        }
    }

    private fun requestVpnConsent() {
        val consentIntent = VpnService.prepare(this)
        if (consentIntent == null) {
            startSpikeService(pendingDomain)
            statusView.text = getString(R.string.website_blocking_spike_started, 1)
        } else {
            statusView.setText(R.string.website_blocking_spike_requesting_consent)
            @Suppress("DEPRECATION")
            startActivityForResult(consentIntent, VPN_CONSENT_REQUEST_CODE)
        }
    }

    private fun startSpikeService(domain: DomainName) {
        ContextCompat.startForegroundService(
            this,
            KeepDnsVpnService.startIntent(this, domain),
        )
    }

    private fun stopSpikeService() {
        startService(KeepDnsVpnService.stopIntent(this))
        statusView.setText(R.string.website_blocking_spike_stopped)
    }

    private companion object {
        const val VPN_CONSENT_REQUEST_CODE = 53
        val DEFAULT_DOMAIN = DomainName("example.com")
    }
}
