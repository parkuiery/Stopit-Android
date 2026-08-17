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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DnsTunIpVersion
import com.uiery.keep.domain.websiteblocking.DnsVpnUpstreamRecovery
import com.uiery.keep.domain.websiteblocking.DnsVpnUpstreamRecoveryPolicy
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingDomainSetPolicy
import com.uiery.keep.util.AppLogger
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
    // 업스트림이 죽어 필터가 물러난 뒤 몇 번째 복귀 시도인지. 다시 서면 0 으로 돌아간다.
    private var recoveryAttempt = 0
    private var recoveryRetry: Runnable? = null
    // 창의 마감. 이걸 들고 있어야 복구를 언제까지 시도할지 스스로 판단할 수 있다.
    private var stopAtEpochMillis = 0L
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
            // 요청에 의한 종료다. 지난 실패 경고까지 여기서 함께 걷어낸다.
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
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
        startForegroundNotice(request.blockedDomainCount)
        handleBindingCommand(bindingCoordinator.updateRequest(request))
        scheduleDeadlineStop(intent?.getLongExtra(EXTRA_STOP_AT_EPOCH_MILLIS, 0L) ?: 0L)
        return START_REDELIVER_INTENT
    }

    override fun onRevoke() {
        shutdown()
        // 잠금은 그대로인데 차단만 사라진 상태다. 조용히 끝내면 막히고 있다고 오해한다.
        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Unavailable)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        WebsiteBlockingRuntimeState.clearActive()
        super.onDestroy()
    }

    private fun startForegroundNotice(blockedDomainCount: Int) {
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
                Thread(runnable, "keep-dns-vpn-worker").apply { isDaemon = true }
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
            AppLogger.debug(DIAGNOSTIC_TAG, "stop_reason=tun_establish_failed")
            // 동의는 받았는데 TUN 을 세우지 못했다. 다른 VPN 이 슬롯을 쥐고 있는 경우가
            // 대표적이다. 잠금은 유지되지만 웹사이트는 막히지 않으므로 화면이 알려야 한다.
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Unavailable)
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
        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Active)
        try {
            createUpstreamEndpointPool(upstream).use { upstreamPool ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(descriptor.fileDescriptor).use { output ->
                        val processor = DnsVpnDatagramProcessor(
                            blockedDomains = blockedDomains,
                            // TUN 이 섰다는 것만으로는 회복이 아니다. 업스트림 왕복이 실제로
                            // 성공해야 재시도 예산을 되돌린다. 그러지 않으면 손실이 큰 회선에서
                            // 빠른 재시도만 무한히 반복되고 백오프가 걸리지 않는다.
                            upstreamExchange = { payload ->
                                upstreamPool.exchange(payload)?.also { markUpstreamHealthy() }
                            },
                        )
                        val buffer = ByteArray(TUN_BUFFER_SIZE)
                        while (!sessionOwner.shouldWorkerExit(session)) {
                            val length = input.read(buffer)
                            if (length <= 0) continue
                            when (val result = processor.process(buffer.copyOf(length))) {
                                is DnsVpnDatagramProcessResult.SendToTun -> output.write(result.packet)
                                is DnsVpnDatagramProcessResult.IgnorePacket -> Unit
                                is DnsVpnDatagramProcessResult.FailOpenStopVpn -> {
                                    AppLogger.debug(
                                        DIAGNOSTIC_TAG,
                                        "stop_reason=${result.reason.name}" +
                                            " tun_reason=${result.tunFailureReason?.name ?: "none"}" +
                                            " ip_version=${result.tunIpVersion ?: -1}" +
                                            " protocol=${result.tunProtocol ?: -1}",
                                    )
                                    // 필터가 물러나면 그 순간부터 웹사이트는 열려 있다.
                                    // 조용히 끝내면 사용자는 막히고 있다고 믿은 채로 남는다.
                                    WebsiteBlockingRuntimeState.update(
                                        WebsiteBlockingStatus.NetworkUnavailable,
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
            AppLogger.debug(DIAGNOSTIC_TAG, "stop_reason=tun_io_failed")
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.NetworkUnavailable)
            stopFromWorker(session)
        } finally {
            closeTun(session)
        }
    }

    private fun establishDnsOnlyTun(upstreamNetwork: Network): ParcelFileDescriptor? =
        Builder()
            .setSession(getString(R.string.website_blocking_service_vpn_session))
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
                AppLogger.debug(
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
            AppLogger.debug(
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
            .setContentTitle(getString(R.string.website_blocking_service_notification_title))
            .setContentText(getString(R.string.website_blocking_service_notification_text, blockedDomainCount))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.website_blocking_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.website_blocking_service_channel_description)
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
            recoveryRetry?.let(mainHandler::removeCallbacks)
            recoveryRetry = null
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
        // 복구 판단이 "언제까지 기다릴 값어치가 있는가"를 알려면 마감을 들고 있어야 한다.
        this.stopAtEpochMillis = stopAtEpochMillis
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
                AppLogger.debug(
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
            AppLogger.debug(DIAGNOSTIC_TAG, "upstream_suspended network=$network")
        }
    }

    /** 업스트림이 실제로 답을 준 순간. 여기서만 재시도 예산이 되돌아온다. */
    private fun markUpstreamHealthy() {
        if (recoveryAttempt == 0 && retryCount == 0) return
        synchronized(lifecycleLock) {
            recoveryAttempt = 0
            retryCount = 0
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
            recoverOrStopFromWorker(session, network)
            return
        }

        AppLogger.debug(
            DIAGNOSTIC_TAG,
            "upstream_retry_scheduled network=$network" +
                " delay_ms=$UPSTREAM_TRANSITION_RETRY_DELAY_MILLIS",
        )
        mainHandler.postDelayed(
            {
                val command = bindingCoordinator.retryBinding(network)
                if (command is DnsVpnNetworkBindingCommand.Bind) {
                    AppLogger.debug(DIAGNOSTIC_TAG, "upstream_retry network=$network")
                    startVpnWorker(command.request, command.upstream, isRetry = true)
                }
            },
            UPSTREAM_TRANSITION_RETRY_DELAY_MILLIS,
        )
    }

    /**
     * 전환용 빠른 재시도까지 소진된 뒤의 자리.
     *
     * 여기서 서비스를 끝내면 네트워크 콜백도 함께 해제되어, 회선이 돌아와도 다시 세울 주체가
     * 없다. 창이 남아 있는 동안에는 살아서 기다린다. 필터가 물러나 있는 것은 지금과 같지만
     * (도메인은 열려 있다), 적어도 돌아올 수는 있다.
     */
    private fun recoverOrStopFromWorker(session: DnsVpnSession, network: Network) {
        val delayMillis = when (
            val recovery = DnsVpnUpstreamRecoveryPolicy.decide(
                attempt = recoveryAttempt,
                nowEpochMillis = System.currentTimeMillis(),
                stopAtEpochMillis = stopAtEpochMillis,
            )
        ) {
            DnsVpnUpstreamRecovery.GiveUp -> {
                AppLogger.debug(DIAGNOSTIC_TAG, "upstream_recovery=given_up network=$network")
                stopFromWorker(session)
                return
            }

            is DnsVpnUpstreamRecovery.RetryAfter -> recovery.delayMillis
        }

        synchronized(lifecycleLock) {
            if (!sessionOwner.isActive(session)) return
            recoveryAttempt += 1
            val stopResult = sessionOwner.stopIfOwner(session)
            shutdownWorkerLocked(stopResult.workerToShutdown)
            closeTunLocked(session)
        }
        AppLogger.debug(
            DIAGNOSTIC_TAG,
            "upstream_recovery_scheduled network=$network" +
                " attempt=$recoveryAttempt delay_ms=$delayMillis",
        )

        val retry = Runnable {
            val command = bindingCoordinator.retryBinding(network)
            if (command is DnsVpnNetworkBindingCommand.Bind) {
                AppLogger.debug(DIAGNOSTIC_TAG, "upstream_recovery_retry network=$network")
                startVpnWorker(command.request, command.upstream, isRetry = true)
            }
        }
        recoveryRetry?.let(mainHandler::removeCallbacks)
        recoveryRetry = retry
        mainHandler.postDelayed(retry, delayMillis)
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

        /*
         * 프로덕션 출시 이후에는 바꾸지 않는다. 채널 ID 를 바꾸면 Android 는 새 채널을 만들고,
         * 사용자가 기존 채널에 해둔 설정(끄기·중요도·소리)은 아무도 보지 않는 채널에 남는다.
         * 알림을 껐던 사용자에게 알림이 다시 뜨기 시작한다.
         *
         * 이 값은 스파이크 시절 `website_blocking_spike` 였고, prod 출시 직전에 마지막으로
         * 정리했다. 그때가 바꿀 수 있는 마지막 시점이었다.
         */
        private const val CHANNEL_ID = "website_blocking"
        private const val DIAGNOSTIC_TAG = "KeepWebsiteBlocking"
        private const val NOTIFICATION_ID = 53_053
        private const val VIRTUAL_IPV4_CLIENT = "10.111.0.2"
        private const val VIRTUAL_IPV4_DNS = "10.111.0.1"
        private const val VIRTUAL_IPV6_CLIENT = "fd00:7579:6473::2"
        private const val VIRTUAL_IPV6_DNS = "fd00:7579:6473::1"
        private const val TUN_MTU = 1500
        private const val TUN_BUFFER_SIZE = 65_535
        private const val DNS_PORT = 53
        // 실기기 회선에서 1.1.1.1 왕복이 약 1.7초였다. 1.5초로는 모든 질의가 시간 초과로
        // 떨어져 느린 회선이 상시 fail-open 으로 수렴한다. 이 값을 늘려도 차단 대상은
        // 로컬에서 NXDOMAIN 을 만들므로 느려지는 것은 통과 도메인뿐이다.
        private const val DNS_TIMEOUT_MILLIS = 5_000
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
