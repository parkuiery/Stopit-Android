package com.uiery.keep.websiteblocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.DomainNameNormalizationResult
import com.uiery.keep.domain.websiteblocking.DomainNamePolicy
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class KeepDnsVpnSpikeService : VpnService() {
    private val closed = AtomicBoolean(true)
    private var worker: ExecutorService? = null
    @Volatile
    private var tunDescriptor: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        val blockedDomains = setOf(normalizeDomain(intent?.getStringExtra(EXTRA_DOMAIN)))
        startForegroundForSpike(blockedDomains.size)
        startVpnWorker(blockedDomains)
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        shutdown()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun startForegroundForSpike(blockedDomainCount: Int) {
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(blockedDomainCount),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun startVpnWorker(blockedDomains: Set<DomainName>) {
        closed.set(true)
        shutdownWorkerOnly()
        closeTun()
        closed.set(false)
        worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "keep-dns-vpn-spike").apply { isDaemon = true }
        }.also { executor ->
            executor.execute {
                runVpn(blockedDomains)
            }
        }
    }

    private fun runVpn(blockedDomains: Set<DomainName>) {
        val upstreamDnsServers = activeUpstreamDnsServers()
        if (upstreamDnsServers.isEmpty()) {
            stopFromWorker()
            return
        }

        val descriptor = try {
            establishDnsOnlyTun()
        } catch (_: RuntimeException) {
            null
        }
        if (descriptor == null) {
            stopFromWorker()
            return
        }
        tunDescriptor = descriptor

        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val processor = DnsVpnDatagramProcessor(
                        blockedDomains = blockedDomains,
                        upstreamExchange = { payload -> exchangeWithUpstream(payload, upstreamDnsServers) },
                    )
                    val buffer = ByteArray(TUN_BUFFER_SIZE)
                    while (!closed.get()) {
                        val length = input.read(buffer)
                        if (length <= 0) continue
                        when (val result = processor.process(buffer.copyOf(length))) {
                            is DnsVpnDatagramProcessResult.SendToTun -> output.write(result.packet)
                            is DnsVpnDatagramProcessResult.FailOpenStopVpn -> {
                                stopFromWorker()
                                return
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            stopFromWorker()
        } finally {
            closeTun()
        }
    }

    private fun establishDnsOnlyTun(): ParcelFileDescriptor? =
        Builder()
            .setSession(getString(R.string.website_blocking_spike_vpn_session))
            .setMtu(TUN_MTU)
            .addAddress(VIRTUAL_IPV4_DNS, 32)
            .addAddress(VIRTUAL_IPV6_DNS, 128)
            .addDnsServer(VIRTUAL_IPV4_DNS)
            .addDnsServer(VIRTUAL_IPV6_DNS)
            .addRoute(VIRTUAL_IPV4_DNS, 32)
            .addRoute(VIRTUAL_IPV6_DNS, 128)
            .establish()

    private fun activeUpstreamDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return emptyList()
        return connectivityManager.getLinkProperties(network)?.dnsServers.orEmpty()
    }

    private fun exchangeWithUpstream(payload: ByteArray, upstreamDnsServers: List<InetAddress>): ByteArray? {
        upstreamDnsServers.forEach { dnsServer ->
            try {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) return@forEach
                    socket.soTimeout = DNS_TIMEOUT_MILLIS
                    val outbound = DatagramPacket(
                        payload.copyOf(),
                        payload.size,
                        InetSocketAddress(dnsServer, DNS_PORT),
                    )
                    socket.send(outbound)

                    val responseBuffer = ByteArray(MAX_DNS_UDP_PAYLOAD)
                    val inbound = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(inbound)
                    return responseBuffer.copyOf(inbound.length)
                }
            } catch (_: IOException) {
                // Try the next DNS server; if all fail, the processor restores fail-open behavior.
            } catch (_: RuntimeException) {
                return null
            }
        }
        return null
    }

    private fun buildNotification(blockedDomainCount: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_stopit)
            .setContentTitle(getString(R.string.website_blocking_spike_notification_title))
            .setContentText(getString(R.string.website_blocking_spike_notification_text, blockedDomainCount))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.website_blocking_spike_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.website_blocking_spike_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun normalizeDomain(rawDomain: String?): DomainName {
        val input = rawDomain.orEmpty().ifBlank { DEFAULT_DOMAIN }
        val result = DomainNamePolicy.normalize(input)
        return (result as? DomainNameNormalizationResult.Valid)?.domain ?: DomainName(DEFAULT_DOMAIN)
    }

    private fun stopFromWorker() {
        shutdown()
        stopSelf()
    }

    private fun shutdown() {
        if (!closed.getAndSet(true)) {
            shutdownWorkerOnly()
            closeTun()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun shutdownWorkerOnly() {
        worker?.shutdownNow()
        worker = null
    }

    private fun closeTun() {
        try {
            tunDescriptor?.close()
        } catch (_: IOException) {
            // Idempotent cleanup only.
        } finally {
            tunDescriptor = null
        }
    }

    companion object {
        const val ACTION_START = "com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE"
        const val ACTION_STOP = "com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_STOP = "stop"

        private const val CHANNEL_ID = "website_blocking_spike"
        private const val NOTIFICATION_ID = 53_053
        private const val DEFAULT_DOMAIN = "example.com"
        private const val VIRTUAL_IPV4_DNS = "10.111.0.1"
        private const val VIRTUAL_IPV6_DNS = "fd00:7579:6473::1"
        private const val TUN_MTU = 1500
        private const val TUN_BUFFER_SIZE = 65_535
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MILLIS = 1_500
        private const val MAX_DNS_UDP_PAYLOAD = 4_096

        fun startIntent(context: Context, domain: DomainName): Intent =
            Intent(context, KeepDnsVpnSpikeService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DOMAIN, domain.value)

        fun stopIntent(context: Context): Intent =
            Intent(context, KeepDnsVpnSpikeService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_STOP, true)
    }
}
