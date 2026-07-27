package com.uiery.keep.websiteblocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DnsTunIpVersion
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingDomainSetPolicy
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KeepDnsVpnService : VpnService() {
    private val lifecycleLock = Any()
    private val sessionOwner = DnsVpnSessionOwner<ExecutorService>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindingCoordinator =
        DnsVpnNetworkBindingCoordinator<ActiveVpnRequest, Network, InetAddress>()
    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            handleBindingCommand(
                bindingCoordinator.updateUpstream(
                    network = network,
                    dnsServers = linkProperties.dnsServers,
                ),
            )
        }

        override fun onLost(network: Network) {
            handleBindingCommand(bindingCoordinator.loseUpstream(network))
        }
    }
    private var worker: ExecutorService? = null
    private var networkCallbackRegistered = false
    private var retryNetwork: Network? = null
    private var retryCount = 0
    @Volatile
    private var tunHandle: TunHandle? = null
    private var deadlineStop: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        registerUnderlyingNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent?.getBooleanExtra(EXTRA_STOP, false) == true) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }

        val configuredDomains = intent
            ?.getStringArrayListExtra(EXTRA_DOMAINS)
            ?.toSet()
            ?: setOfNotNull(intent?.getStringExtra(EXTRA_DOMAIN))
        val blockedDomains = WebsiteBlockingDomainSetPolicy.normalize(configuredDomains)
        if (blockedDomains.isEmpty()) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        val request = ActiveVpnRequest(
            blockedDomains = blockedDomains,
            blockedDomainCount = blockedDomains.size,
            startId = startId,
        )
        startForegroundForSpike(request.blockedDomainCount)
        handleBindingCommand(bindingCoordinator.updateRequest(request))
        scheduleDeadlineStop(intent?.getLongExtra(EXTRA_STOP_AT_EPOCH_MILLIS, 0L) ?: 0L)
        return START_REDELIVER_INTENT
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
        request: ActiveVpnRequest,
        upstream: DnsVpnUnderlyingNetwork<Network, InetAddress>,
        isRetry: Boolean = false,
    ) {
        synchronized(lifecycleLock) {
            if (!isRetry || retryNetwork != upstream.network) {
                retryNetwork = upstream.network
                retryCount = 0
            }
            val previousWorker = sessionOwner.activeWorkerHandle()?.worker
            val session = sessionOwner.startSession(request.startId)
            shutdownWorkerLocked(previousWorker)
            closeInactiveTunLocked(session)
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "keep-dns-vpn-spike").apply { isDaemon = true }
            }
            worker = executor
            sessionOwner.publishWorkerHandle(DnsVpnWorkerHandle(session, executor))
            executor.execute {
                runVpn(session, request.blockedDomains, upstream)
            }
        }
    }

    private fun runVpn(
        session: DnsVpnSession,
        blockedDomains: Set<DomainName>,
        upstream: DnsVpnUnderlyingNetwork<Network, InetAddress>,
    ) {
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
            createUpstreamEndpointPool(upstream).use { upstreamPool ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(descriptor.fileDescriptor).use { output ->
                        val processor = DnsVpnDatagramProcessor(
                            blockedDomains = blockedDomains,
                            upstreamExchange = upstreamPool::exchange,
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
                                    if (result.reason == DnsVpnDatagramProcessStopReason.UpstreamUnavailable) {
                                        retryOrStopFromWorker(session, upstream.network)
                                    } else {
                                        stopFromWorker(session)
                                    }
                                    return
                                }
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

    private fun createUpstreamEndpointPool(
        upstream: DnsVpnUnderlyingNetwork<Network, InetAddress>,
    ): DnsUpstreamEndpointPool<InetAddress, DnsUpstreamDatagramEndpoint> =
        DnsUpstreamEndpointPool(
            addresses = upstream.dnsServers,
            endpointFactory = { dnsServer ->
                openUpstreamEndpoint(dnsServer, upstream.network)
            },
            exchange = DnsUpstreamDatagramEndpoint::exchange,
            onFailure = { dnsServer, error ->
                Log.d(
                    DIAGNOSTIC_TAG,
                    "upstream_attempt=failed" +
                        " address_family=${if (dnsServer.address.size == 4) 4 else 6}" +
                        " error_type=${error.javaClass.simpleName}",
                )
            },
            failureCooldownMillis = UPSTREAM_FAILURE_COOLDOWN_MILLIS,
            nowMillis = SystemClock::elapsedRealtime,
        )

    private fun openUpstreamEndpoint(
        dnsServer: InetAddress,
        network: Network,
    ): DnsUpstreamDatagramEndpoint? {
        val socket = DatagramSocket()
        return try {
            if (!prepareUpstreamSocket(socket, network, ::protect)) {
                socket.close()
                null
            } else {
                socket.soTimeout = DNS_TIMEOUT_MILLIS
                socket.connect(InetSocketAddress(dnsServer, DNS_PORT))
                DnsUpstreamDatagramEndpoint(socket)
            }
        } catch (error: IOException) {
            socket.close()
            Log.d(
                DIAGNOSTIC_TAG,
                "upstream_open=failed" +
                    " address_family=${if (dnsServer.address.size == 4) 4 else 6}" +
                    " error_type=${error.javaClass.simpleName}",
            )
            null
        } catch (_: RuntimeException) {
            socket.close()
            null
        }
    }

    private class DnsUpstreamDatagramEndpoint(
        private val socket: DatagramSocket,
    ) : AutoCloseable {
        fun exchange(payload: ByteArray): ByteArray {
            val outbound = DatagramPacket(payload.copyOf(), payload.size)
            socket.send(outbound)

            val responseBuffer = ByteArray(MAX_UPSTREAM_DNS_RECEIVE_SIZE)
            val inbound = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(inbound)
            return responseBuffer.copyOf(inbound.length)
        }

        override fun close() {
            try {
                socket.close()
            } catch (_: RuntimeException) {
                // Idempotent cleanup only.
            }
        }
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

    private fun stopFromWorker(session: DnsVpnSession) {
        val startIdToStop = synchronized(lifecycleLock) {
            val stopResult = sessionOwner.stopIfOwner(session)
            if (stopResult.shouldStopService) {
                bindingCoordinator.stop()
                unregisterUnderlyingNetworkCallback()
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
            deadlineStop?.let(mainHandler::removeCallbacks)
            deadlineStop = null
            bindingCoordinator.stop()
            unregisterUnderlyingNetworkCallback()
            val stopResult = sessionOwner.stopActive()
            if (stopResult.shouldStopService) {
                shutdownWorkerLocked(stopResult.workerToShutdown)
                tunHandle?.let { closeTunLocked(it.session) }
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    private fun scheduleDeadlineStop(stopAtEpochMillis: Long) {
        deadlineStop?.let(mainHandler::removeCallbacks)
        deadlineStop = null
        if (stopAtEpochMillis <= 0L) return

        val stopAction = Runnable {
            shutdown()
            stopSelf()
        }
        deadlineStop = stopAction
        mainHandler.postDelayed(
            stopAction,
            (stopAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L),
        )
    }

    private fun registerUnderlyingNetworkCallback() {
        if (networkCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager.registerBestMatchingNetworkCallback(
            request,
            networkCallback,
            mainHandler,
        )
        networkCallbackRegistered = true
    }

    private fun unregisterUnderlyingNetworkCallback() {
        if (!networkCallbackRegistered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        networkCallbackRegistered = false
    }

    private fun handleBindingCommand(
        command: DnsVpnNetworkBindingCommand<ActiveVpnRequest, Network, InetAddress>?,
    ) {
        when (command) {
            is DnsVpnNetworkBindingCommand.Bind -> {
                Log.d(
                    DIAGNOSTIC_TAG,
                    "upstream_rebind network=${command.upstream.network}" +
                        " dns_count=${command.upstream.dnsServers.size}",
                )
                startVpnWorker(command.request, command.upstream)
            }
            is DnsVpnNetworkBindingCommand.Suspend -> suspendVpnForLostNetwork(command.network)
            null -> Unit
        }
    }

    private fun suspendVpnForLostNetwork(network: Network) {
        synchronized(lifecycleLock) {
            val stopResult = sessionOwner.stopActive()
            shutdownWorkerLocked(stopResult.workerToShutdown)
            tunHandle?.let { closeTunLocked(it.session) }
            Log.d(DIAGNOSTIC_TAG, "upstream_suspended network=$network")
        }
    }

    private fun retryOrStopFromWorker(session: DnsVpnSession, network: Network) {
        val shouldRetry = synchronized(lifecycleLock) {
            if (!sessionOwner.isActive(session)) return
            if (retryNetwork != network || retryCount >= UPSTREAM_TRANSITION_RETRY_LIMIT) {
                false
            } else {
                retryCount += 1
                val stopResult = sessionOwner.stopIfOwner(session)
                shutdownWorkerLocked(stopResult.workerToShutdown)
                closeTunLocked(session)
                true
            }
        }
        if (!shouldRetry) {
            stopFromWorker(session)
            return
        }

        Log.d(
            DIAGNOSTIC_TAG,
            "upstream_retry_scheduled network=$network" +
                " delay_ms=$UPSTREAM_TRANSITION_RETRY_DELAY_MILLIS",
        )
        mainHandler.postDelayed(
            {
                val command = bindingCoordinator.retryBinding(network)
                if (command is DnsVpnNetworkBindingCommand.Bind) {
                    Log.d(DIAGNOSTIC_TAG, "upstream_retry network=$network")
                    startVpnWorker(command.request, command.upstream, isRetry = true)
                }
            },
            UPSTREAM_TRANSITION_RETRY_DELAY_MILLIS,
        )
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

    private data class ActiveVpnRequest(
        val blockedDomains: Set<DomainName>,
        val blockedDomainCount: Int,
        val startId: Int,
    )

    companion object {
        const val ACTION_START = "com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE"
        const val ACTION_STOP = "com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_DOMAINS = "domains"
        const val EXTRA_STOP = "stop"
        const val EXTRA_STOP_AT_EPOCH_MILLIS = "stop_at_epoch_millis"

        private const val CHANNEL_ID = "website_blocking_spike"
        private const val DIAGNOSTIC_TAG = "KeepDnsVpnSpike"
        private const val NOTIFICATION_ID = 53_053
        private const val VIRTUAL_IPV4_CLIENT = "10.111.0.2"
        private const val VIRTUAL_IPV4_DNS = "10.111.0.1"
        private const val VIRTUAL_IPV6_CLIENT = "fd00:7579:6473::2"
        private const val VIRTUAL_IPV6_DNS = "fd00:7579:6473::1"
        private const val TUN_MTU = 1500
        private const val TUN_BUFFER_SIZE = 65_535
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MILLIS = 1_500
        private const val UPSTREAM_FAILURE_COOLDOWN_MILLIS = 30_000L
        private const val UPSTREAM_TRANSITION_RETRY_LIMIT = 1
        private const val UPSTREAM_TRANSITION_RETRY_DELAY_MILLIS = 250L
        private val MAX_UPSTREAM_DNS_RECEIVE_SIZE =
            DnsVpnUpstreamResponsePolicy.receiveBufferSize(DnsTunIpVersion.IPv4)

        fun startIntent(context: Context, domain: DomainName): Intent =
            startIntent(context, setOf(domain))

        fun startIntent(
            context: Context,
            domains: Set<DomainName>,
            stopAtEpochMillis: Long? = null,
        ): Intent =
            Intent(context, KeepDnsVpnService::class.java)
                .setAction(ACTION_START)
                .putStringArrayListExtra(
                    EXTRA_DOMAINS,
                    ArrayList(domains.map { it.value }.sorted()),
                )
                .apply {
                    stopAtEpochMillis?.let {
                        putExtra(EXTRA_STOP_AT_EPOCH_MILLIS, it)
                    }
                }

        fun stopIntent(context: Context): Intent =
            Intent(context, KeepDnsVpnService::class.java)
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
