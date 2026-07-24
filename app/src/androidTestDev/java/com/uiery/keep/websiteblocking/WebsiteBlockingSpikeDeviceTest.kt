package com.uiery.keep.websiteblocking

import android.net.VpnService
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.domain.websiteblocking.DomainName
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.assertEquals
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
            KeepDnsVpnSpikeService.startIntent(context, DomainName(BLOCKED_DOMAIN)),
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
        } finally {
            context.startService(KeepDnsVpnSpikeService.stopIntent(context))
        }
    }

    private fun exchangeDns(dnsServer: String, domain: String, transactionId: Int): ByteArray =
        DatagramSocket().use { socket ->
            socket.soTimeout = DNS_TIMEOUT_MILLIS
            val query = dnsQuery(domain, transactionId)
            socket.send(
                DatagramPacket(
                    query,
                    query.size,
                    InetAddress.getByName(dnsServer),
                    DNS_PORT,
                ),
            )
            val response = DatagramPacket(ByteArray(DNS_RESPONSE_BUFFER_SIZE), DNS_RESPONSE_BUFFER_SIZE)
            socket.receive(response)
            response.data.copyOf(response.length)
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
    }
}
