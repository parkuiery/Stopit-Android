package com.uiery.keep.websiteblocking

import android.net.VpnService
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.domain.websiteblocking.DomainName
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebsiteBlockingSpikeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun dnsVpnBlocksExactAndSubdomainWhileAllowingOtherDomains() {
        assumeTrue(
            "Run only for an explicit physical-device VPN check.",
            InstrumentationRegistry.getArguments().getString(RUN_DEVICE_TEST_ARGUMENT) == "true",
        )
        assumeTrue(
            "VPN consent must be granted once through KeepDnsVpnSpikeActivity.",
            VpnService.prepare(context) == null,
        )

        ContextCompat.startForegroundService(
            context,
            KeepDnsVpnService.startIntent(context, DomainName(BLOCKED_DOMAIN)),
        )
        SystemClock.sleep(VPN_STARTUP_DELAY_MILLIS)

        try {
            listOf(VIRTUAL_IPV4_DNS, VIRTUAL_IPV6_DNS).forEachIndexed { index, dnsServer ->
                val transactionBase = 0x4100 + (index * 0x10)
                assertEquals(
                    "$dnsServer must block the exact domain",
                    NXDOMAIN_RCODE,
                    exchangeDns(dnsServer, BLOCKED_DOMAIN, transactionBase + 1).rcode(),
                )
                assertEquals(
                    "$dnsServer must block a true subdomain",
                    NXDOMAIN_RCODE,
                    exchangeDns(dnsServer, "www.$BLOCKED_DOMAIN", transactionBase + 2).rcode(),
                )
                assertEquals(
                    "$dnsServer must forward an allowed domain",
                    NO_ERROR_RCODE,
                    exchangeDns(dnsServer, ALLOWED_DOMAIN, transactionBase + 3).rcode(),
                )
            }
            verifyLocalBlockLatency()
            verifyAllowedDnsReliability()
        } finally {
            context.startService(KeepDnsVpnService.stopIntent(context))
        }
    }

    private fun verifyAllowedDnsReliability() {
        val probes = listOf(DnsProbe(VIRTUAL_IPV4_DNS), DnsProbe(VIRTUAL_IPV6_DNS))
        val latencySamplesMillis = mutableListOf<Long>()
        var successfulAttempts = 0
        try {
            repeat(ALLOWED_RELIABILITY_ATTEMPTS) { index ->
                try {
                    val startedAtNanos = SystemClock.elapsedRealtimeNanos()
                    assertEquals(
                        "Allowed DNS attempt ${index + 1} must succeed",
                        NO_ERROR_RCODE,
                        probes[index % probes.size]
                            .exchange(ALLOWED_DOMAIN, RELIABILITY_TRANSACTION_BASE + index)
                            .rcode(),
                    )
                    latencySamplesMillis +=
                        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / NANOS_PER_MILLI
                    successfulAttempts += 1
                } catch (error: IOException) {
                    Log.i(
                        DIAGNOSTIC_TAG,
                        "allowed_dns_attempts=$ALLOWED_RELIABILITY_ATTEMPTS" +
                            " successes=$successfulAttempts failures=1",
                    )
                    throw AssertionError(
                        "Allowed DNS attempt ${index + 1} failed after $successfulAttempts successes",
                        error,
                    )
                }
                if (index < ALLOWED_RELIABILITY_ATTEMPTS - 1) {
                    SystemClock.sleep(ALLOWED_RELIABILITY_INTERVAL_MILLIS)
                }
            }
        } finally {
            probes.forEach(DnsProbe::close)
        }
        val sortedLatencyMillis = latencySamplesMillis.sorted()
        Log.i(
            DIAGNOSTIC_TAG,
            "allowed_dns_attempts=$ALLOWED_RELIABILITY_ATTEMPTS failures=0" +
                " p95_ms=${sortedLatencyMillis.percentile(95)}" +
                " p99_ms=${sortedLatencyMillis.percentile(99)}" +
                " max_ms=${sortedLatencyMillis.last()}",
        )
    }

    private fun verifyLocalBlockLatency() {
        DnsProbe(VIRTUAL_IPV4_DNS).use { probe ->
            repeat(LATENCY_WARMUP_ATTEMPTS) { index ->
                probe.exchange(BLOCKED_DOMAIN, LATENCY_WARMUP_TRANSACTION_BASE + index)
            }
            val samplesMillis = List(LATENCY_SAMPLE_COUNT) { index ->
                val startedAtNanos = SystemClock.elapsedRealtimeNanos()
                val response = probe.exchange(BLOCKED_DOMAIN, LATENCY_TRANSACTION_BASE + index)
                val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / NANOS_PER_MILLI
                assertEquals(NXDOMAIN_RCODE, response.rcode())
                elapsedMillis
            }.sorted()
            val p95Millis = samplesMillis[(samplesMillis.size * 95 / 100).coerceAtMost(samplesMillis.lastIndex)]
            Log.i(
                DIAGNOSTIC_TAG,
                "local_block_latency_samples=$LATENCY_SAMPLE_COUNT p95_ms=$p95Millis",
            )
            assertTrue(
                "Local block latency p95 ${p95Millis}ms must be <= ${MAX_LOCAL_P95_MILLIS}ms",
                p95Millis <= MAX_LOCAL_P95_MILLIS,
            )
        }
    }

    private fun exchangeDns(dnsServer: String, domain: String, transactionId: Int): ByteArray =
        DnsProbe(dnsServer).use { probe ->
            probe.exchange(domain, transactionId)
        }

    private inner class DnsProbe(dnsServer: String) : AutoCloseable {
        private val socket = DatagramSocket().apply {
            soTimeout = DNS_TIMEOUT_MILLIS
        }
        private val address = InetAddress.getByName(dnsServer)

        fun exchange(domain: String, transactionId: Int): ByteArray {
            val query = dnsQuery(domain, transactionId)
            socket.send(
                DatagramPacket(
                    query,
                    query.size,
                    address,
                    DNS_PORT,
                ),
            )
            val response = DatagramPacket(ByteArray(DNS_RESPONSE_BUFFER_SIZE), DNS_RESPONSE_BUFFER_SIZE)
            socket.receive(response)
            return response.data.copyOf(response.length)
        }

        override fun close() {
            socket.close()
        }
    }

    private fun dnsQuery(domain: String, transactionId: Int): ByteArray {
        val labels = domain.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.map { it.code.toByte() }
        }
        return byteArrayOf(
            (transactionId ushr 8).toByte(),
            transactionId.toByte(),
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
        ) + labels + byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01)
    }

    private fun ByteArray.rcode(): Int = (this[3].toInt() and 0x0F)

    private fun List<Long>.percentile(percent: Int): Long =
        this[((size * percent) / 100).coerceAtMost(lastIndex)]

    private companion object {
        const val RUN_DEVICE_TEST_ARGUMENT = "runVpnDeviceTest"
        const val BLOCKED_DOMAIN = "example.com"
        const val ALLOWED_DOMAIN = "www.cloudflare.com"
        const val VPN_STARTUP_DELAY_MILLIS = 1_000L
        const val VIRTUAL_IPV4_DNS = "10.111.0.1"
        const val VIRTUAL_IPV6_DNS = "fd00:7579:6473::1"
        const val DNS_PORT = 53
        const val DNS_TIMEOUT_MILLIS = 3_000
        const val DNS_RESPONSE_BUFFER_SIZE = 1_500
        const val NO_ERROR_RCODE = 0
        const val NXDOMAIN_RCODE = 3
        const val ALLOWED_RELIABILITY_ATTEMPTS = 500
        const val ALLOWED_RELIABILITY_INTERVAL_MILLIS = 50L
        const val LATENCY_WARMUP_ATTEMPTS = 20
        const val LATENCY_SAMPLE_COUNT = 200
        const val MAX_LOCAL_P95_MILLIS = 20L
        const val RELIABILITY_TRANSACTION_BASE = 0x5000
        const val LATENCY_WARMUP_TRANSACTION_BASE = 0x6000
        const val LATENCY_TRANSACTION_BASE = 0x6100
        const val NANOS_PER_MILLI = 1_000_000L
        const val DIAGNOSTIC_TAG = "KeepDnsVpnSpikeTest"
    }
}
