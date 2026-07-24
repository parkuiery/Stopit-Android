package com.uiery.keep.websiteblocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DnsTunIpVersion
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

class KeepDnsVpnSpikeService : VpnService() {
    private val lifecycleLock = Any()
    private val sessionOwner = DnsVpnSessionOwner<ExecutorService>()
    private var worker: ExecutorService? = null
    @Volatile
    private var tunHandle: TunHandle? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        val blockedDomains = setOf(normalizeDomain(intent?.getStringExtra(EXTRA_DOMAIN)))
        startVpnWorker(
            blockedDomains = blockedDomains,
            blockedDomainCount = blockedDomains.size,
            startId = startId,
        )
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

    private fun startVpnWorker(
        blockedDomains: Set<DomainName>,
        blockedDomainCount: Int,
        startId: Int,
    ) {
        synchronized(lifecycleLock) {
            val previousWorker = sessionOwner.activeWorkerHandle()?.worker
            val session = sessionOwner.startSession(startId)
            shutdownWorkerLocked(previousWorker)
            closeInactiveTunLocked(session)
            startForegroundForSpike(blockedDomainCount)
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "keep-dns-vpn-spike").apply { isDaemon = true }
            }
            worker = executor
            sessionOwner.publishWorkerHandle(DnsVpnWorkerHandle(session, executor))
            executor.execute {
                runVpn(session, blockedDomains)
            }
        }
    }

    private fun runVpn(session: DnsVpnSession, blockedDomains: Set<DomainName>) {
        val upstream = activeUpstreamDnsNetwork()
        if (upstream == null || upstream.dnsServers.isEmpty()) {
            Log.d(DIAGNOSTIC_TAG, "stop_reason=no_upstream_dns")
            stopFromWorker(session)
            return
        }

        val descriptor = try {
            establishDnsOnlyTun(upstream.network)
        } catch (_: RuntimeException) {
            null
        }
        if (descriptor == null) {
            Log.d(DIAGNOSTIC_TAG, "stop_reason=tun_establish_failed")
            stopFromWorker(session)
            return
        }
        if (sessionOwner.shouldWorkerExit(session)) {
            closeDescriptor(descriptor)
            return
        }
        synchronized(lifecycleLock) {
            if (sessionOwner.shouldWorkerExit(session)) {
                closeDescriptor(descriptor)
                return
            }
            tunHandle = TunHandle(session, descriptor)
        }
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    val processor = DnsVpnDatagramProcessor(
                        blockedDomains = blockedDomains,
                        upstreamExchange = { payload -> exchangeWithUpstream(payload, upstream) },
                    )
                    val buffer = ByteArray(TUN_BUFFER_SIZE)
                    while (!sessionOwner.shouldWorkerExit(session)) {
                        val length = input.read(buffer)
                        if (length <= 0) continue
                        when (val result = processor.process(buffer.copyOf(length))) {
                            is DnsVpnDatagramProcessResult.SendToTun -> output.write(result.packet)
                            is DnsVpnDatagramProcessResult.IgnorePacket -> Unit
                            is DnsVpnDatagramProcessResult.FailOpenStopVpn -> {
                                Log.d(
                                    DIAGNOSTIC_TAG,
                                    "stop_reason=${result.reason.name}" +
                                        " tun_reason=${result.tunFailureReason?.name ?: "none"}" +
                                        " ip_version=${result.tunIpVersion ?: -1}" +
                                        " protocol=${result.tunProtocol ?: -1}",
                                )
                                stopFromWorker(session)
                                return
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            Log.d(DIAGNOSTIC_TAG, "stop_reason=tun_io_failed")
            stopFromWorker(session)
        } finally {
            closeTun(session)
        }
    }

    private fun establishDnsOnlyTun(upstreamNetwork: Network): ParcelFileDescriptor? =
        Builder()
            .setSession(getString(R.string.website_blocking_spike_vpn_session))
            .setMtu(TUN_MTU)
            .setUnderlyingNetworks(arrayOf(upstreamNetwork))
            .addAddress(VIRTUAL_IPV4_CLIENT, 32)
            .addAddress(VIRTUAL_IPV6_CLIENT, 128)
            .addDnsServer(VIRTUAL_IPV4_DNS)
            .addDnsServer(VIRTUAL_IPV6_DNS)
            .addRoute(VIRTUAL_IPV4_DNS, 32)
            .addRoute(VIRTUAL_IPV6_DNS, 128)
            .establish()

    private fun activeUpstreamDnsNetwork(): UpstreamDnsNetwork? {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return null
        return UpstreamDnsNetwork(
            network = network,
            dnsServers = connectivityManager.getLinkProperties(network)?.dnsServers.orEmpty(),
        )
    }

    private fun exchangeWithUpstream(payload: ByteArray, upstream: UpstreamDnsNetwork): ByteArray? {
        upstream.dnsServers.forEach { dnsServer ->
            try {
                DatagramSocket().use { socket ->
                    if (!prepareUpstreamSocket(socket, upstream.network, ::protect)) return@forEach
                    socket.soTimeout = DNS_TIMEOUT_MILLIS
                    socket.connect(InetSocketAddress(dnsServer, DNS_PORT))
                    val outbound = DatagramPacket(payload.copyOf(), payload.size)
                    socket.send(outbound)

                    val responseBuffer = ByteArray(MAX_UPSTREAM_DNS_RECEIVE_SIZE)
                    val inbound = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(inbound)
                    return responseBuffer.copyOf(inbound.length)
                }
            } catch (error: IOException) {
                Log.d(
                    DIAGNOSTIC_TAG,
                    "upstream_attempt=failed" +
                        " address_family=${if (dnsServer.address.size == 4) 4 else 6}" +
                        " error_type=${error.javaClass.simpleName}",
                )
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

    private fun stopFromWorker(session: DnsVpnSession) {
        val startIdToStop = synchronized(lifecycleLock) {
            val stopResult = sessionOwner.stopIfOwner(session)
            if (stopResult.shouldStopService) {
                shutdownWorkerLocked(stopResult.workerToShutdown)
                closeTunLocked(session)
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopResult.startIdToStop
        }
        if (startIdToStop != null) {
            stopSelfResult(startIdToStop)
        }
    }

    private fun shutdown() {
        synchronized(lifecycleLock) {
            val stopResult = sessionOwner.stopActive()
            if (stopResult.shouldStopService) {
                shutdownWorkerLocked(stopResult.workerToShutdown)
                tunHandle?.let { closeTunLocked(it.session) }
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    private fun shutdownWorkerLocked(workerToShutdown: ExecutorService?) {
        workerToShutdown?.shutdownNow()
        if (worker == workerToShutdown) {
            worker = null
        }
    }

    private fun closeInactiveTunLocked(activeSession: DnsVpnSession) {
        val handle = tunHandle ?: return
        if (handle.session != activeSession) {
            closeTunLocked(handle.session)
        }
    }

    private fun closeTun(session: DnsVpnSession) {
        synchronized(lifecycleLock) {
            closeTunLocked(session)
        }
    }

    private fun closeTunLocked(session: DnsVpnSession) {
        val handle = tunHandle ?: return
        if (handle.session != session) return
        tunHandle = null
        closeDescriptor(handle.descriptor)
    }

    private fun closeDescriptor(descriptor: ParcelFileDescriptor) {
        try {
            descriptor.close()
        } catch (_: IOException) {
            // Idempotent cleanup only.
        }
    }

    private data class TunHandle(
        val session: DnsVpnSession,
        val descriptor: ParcelFileDescriptor,
    )

    private data class UpstreamDnsNetwork(
        val network: Network,
        val dnsServers: List<InetAddress>,
    )

    companion object {
        const val ACTION_START = "com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE"
        const val ACTION_STOP = "com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_STOP = "stop"

        private const val CHANNEL_ID = "website_blocking_spike"
        private const val DIAGNOSTIC_TAG = "KeepDnsVpnSpike"
        private const val NOTIFICATION_ID = 53_053
        private const val DEFAULT_DOMAIN = "example.com"
        private const val VIRTUAL_IPV4_CLIENT = "10.111.0.2"
        private const val VIRTUAL_IPV4_DNS = "10.111.0.1"
        private const val VIRTUAL_IPV6_CLIENT = "fd00:7579:6473::2"
        private const val VIRTUAL_IPV6_DNS = "fd00:7579:6473::1"
        private const val TUN_MTU = 1500
        private const val TUN_BUFFER_SIZE = 65_535
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MILLIS = 1_500
        private val MAX_UPSTREAM_DNS_RECEIVE_SIZE =
            DnsVpnUpstreamResponsePolicy.receiveBufferSize(DnsTunIpVersion.IPv4)

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

internal fun prepareUpstreamSocket(
    socket: DatagramSocket,
    network: Network,
    protectSocket: (DatagramSocket) -> Boolean,
): Boolean {
    if (!protectSocket(socket)) return false
    network.bindSocket(socket)
    return true
}
