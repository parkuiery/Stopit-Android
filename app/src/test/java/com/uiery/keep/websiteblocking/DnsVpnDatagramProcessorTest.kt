package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.DomainName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsVpnDatagramProcessorTest {
    @Test
    fun blockedQueryBuildsLocalNxDomainWithoutCallingUpstream() {
        var upstreamCalls = 0
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = setOf(DomainName("example.com")),
            upstreamExchange = {
                upstreamCalls += 1
                dnsResponse(it)
            },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("ads.example.com")))

        assertEquals(0, upstreamCalls)
        val response = result.requireResponseDnsPayload()
        assertEquals(0x8183, response.readUnsignedShort(2))
        assertEquals(1, response.readUnsignedShort(4))
        assertEquals(0, response.readUnsignedShort(6))
    }

    @Test
    fun blockedQueryDoesNotNeedUpstreamServers() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = setOf(DomainName("example.com")),
            upstreamExchange = { error("blocked query must not call upstream") },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("example.com")))

        assertTrue(result is DnsVpnDatagramProcessResult.SendToTun)
    }

    @Test
    fun allowedQueryForwardsDnsPayloadUpstreamAndWrapsAnswer() {
        val query = dnsQuery("allowed.example")
        val upstreamAnswer = dnsResponse(query)
        var forwardedPayload: ByteArray? = null
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = setOf(DomainName("blocked.example")),
            upstreamExchange = {
                forwardedPayload = it.copyOf()
                upstreamAnswer
            },
        )

        val result = processor.process(ipv4DnsPacket(query))

        assertArrayEquals(query, forwardedPayload)
        assertArrayEquals(upstreamAnswer, result.requireResponseDnsPayload())
    }

    @Test
    fun malformedDnsInsideValidUdpEnvelopeForwardsRawPayload() {
        val malformedDns = byteArrayOf(0x12, 0x34, 0x01, 0x00)
        val upstreamAnswer = dnsResponse(dnsQuery("fallback.example"))
        var forwardedPayload: ByteArray? = null
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = setOf(DomainName("blocked.example")),
            upstreamExchange = {
                forwardedPayload = it.copyOf()
                upstreamAnswer
            },
        )

        val result = processor.process(ipv4DnsPacket(malformedDns))

        assertArrayEquals(malformedDns, forwardedPayload)
        assertArrayEquals(upstreamAnswer, result.requireResponseDnsPayload())
    }

    @Test
    fun tunParseFailureRequestsFailOpenStopVpn() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(byteArrayOf(0x10, 0x20))

        assertEquals(
            DnsVpnDatagramProcessStopReason.TunPacketRejected,
            result.requireStopReason(),
        )
    }

    @Test
    fun upstreamNullRequestsFailOpenStopVpn() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { null },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamUnavailable,
            result.requireStopReason(),
        )
    }

    @Test
    fun responseBuildFailureRequestsFailOpenStopVpn() {
        val oversizedPayload = ByteArray(65_508) { 0x42 }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { oversizedPayload },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.ResponseBuildFailed,
            result.requireStopReason(),
        )
    }

    @Test
    fun processDoesNotMutateInputPacketOrReturnedPayload() {
        val query = dnsQuery("allowed.example")
        val input = ipv4DnsPacket(query)
        val originalInput = input.copyOf()
        val upstreamAnswer = dnsResponse(query)
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = {
                it[0] = 0x7F
                upstreamAnswer
            },
        )

        val result = processor.process(input)
        val response = (result as DnsVpnDatagramProcessResult.SendToTun).packet
        response.fill(0)

        assertArrayEquals(originalInput, input)
        assertFalse(response.contentEquals((processor.process(input) as DnsVpnDatagramProcessResult.SendToTun).packet))
    }

    private fun DnsVpnDatagramProcessResult.requireResponseDnsPayload(): ByteArray {
        this as DnsVpnDatagramProcessResult.SendToTun
        assertEquals(4, packet[0].toInt() ushr 4)
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        val udpLength = packet.readUnsignedShort(headerLength + 4)
        return packet.copyOfRange(headerLength + 8, headerLength + udpLength)
    }

    private fun DnsVpnDatagramProcessResult.requireStopReason(): DnsVpnDatagramProcessStopReason {
        this as DnsVpnDatagramProcessResult.FailOpenStopVpn
        return reason
    }

    private fun dnsQuery(name: String): ByteArray {
        val question = encodeName(name) + byteArrayOf(0x00, 0x01, 0x00, 0x01)
        return byteArrayOf(
            0x12,
            0x34,
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
        ) + question
    }

    private fun dnsResponse(query: ByteArray): ByteArray =
        query.copyOf().also {
            it[2] = 0x81.toByte()
            it[3] = 0x80.toByte()
        }

    private fun ipv4DnsPacket(dnsPayload: ByteArray): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        packet.writeUnsignedShort(2, totalLength)
        packet[8] = 64
        packet[9] = 17
        byteArrayOf(10, 0, 0, 2).copyInto(packet, 12)
        byteArrayOf(10, 0, 0, 1).copyInto(packet, 16)
        packet.writeUnsignedShort(10, internetChecksum(packet, 0, 20))
        packet.writeUnsignedShort(20, 42_000)
        packet.writeUnsignedShort(22, 53)
        packet.writeUnsignedShort(24, udpLength)
        dnsPayload.copyInto(packet, 28)
        return packet
    }

    private fun encodeName(name: String): ByteArray {
        val encoded = ArrayList<Byte>()
        name.split(".").forEach { label ->
            encoded += label.length.toByte()
            label.forEach { encoded += it.code.toByte() }
        }
        encoded += 0
        return encoded.toByteArray()
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.writeUnsignedShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun internetChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index < offset + length) {
            val high = packet[index].toInt() and 0xFF
            val low = if (index + 1 < offset + length) packet[index + 1].toInt() and 0xFF else 0
            sum += ((high shl 8) or low).toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        return sum.inv().toInt() and 0xFFFF
    }
}
