package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.DnsTunPacketFailureReason
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
    fun allowedQueryRejectsUpstreamResponseWithMismatchedTransactionId() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it).withTransactionId(0x4321) },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
            result.requireStopReason(),
        )
    }

    @Test
    fun allowedQueryRejectsUpstreamResponseThatIsStillAQuery() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it).also { response -> response[2] = 0x01 } },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
            result.requireStopReason(),
        )
    }

    @Test
    fun allowedQueryRejectsUpstreamResponseWithMismatchedQuestion() {
        val query = dnsQuery("allowed.example")
        val mismatchedResponse = dnsResponse(dnsQuery("other.example")).withTransactionId(query.readUnsignedShort(0))
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { mismatchedResponse },
        )

        val result = processor.process(ipv4DnsPacket(query))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
            result.requireStopReason(),
        )
    }

    @Test
    fun allowedQueryRejectsUpstreamResponseTooLargeForIpv4Mtu() {
        val oversizedResponse = dnsResponse(dnsQuery("allowed.example")) + ByteArray(1_473)
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { oversizedResponse },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
            result.requireStopReason(),
        )
    }

    @Test
    fun malformedDnsInsideValidUdpEnvelopeRequestsFailOpenStopVpn() {
        val malformedDns = byteArrayOf(0x12, 0x34, 0x01, 0x00)
        var upstreamCalls = 0
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = setOf(DomainName("blocked.example")),
            upstreamExchange = {
                upstreamCalls += 1
                dnsResponse(dnsQuery("fallback.example"))
            },
        )

        val result = processor.process(ipv4DnsPacket(malformedDns))

        assertEquals(0, upstreamCalls)
        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
            result.requireStopReason(),
        )
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
        assertEquals(
            DnsTunPacketFailureReason.UnsupportedProtocol,
            result.requireTunFailureReason(),
        )
    }

    @Test
    fun ipv6ControlPacketIsIgnoredAndReportsProtocolMetadataWithoutPayload() {
        val ipv6IcmpPacket = ByteArray(40).apply {
            this[0] = 0x60
            this[6] = 58
        }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(ipv6IcmpPacket)

        result as DnsVpnDatagramProcessResult.IgnorePacket
        assertEquals(6, result.tunIpVersion)
        assertEquals(58, result.tunProtocol)
    }

    @Test
    fun ipv6ControlPacketAfterHopByHopHeaderIsIgnored() {
        val ipv6HopByHopIcmpPacket = ByteArray(48).apply {
            this[0] = 0x60
            this[4] = 0x00
            this[5] = 0x08
            this[6] = 0
            this[40] = 58
            this[41] = 0
        }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(ipv6HopByHopIcmpPacket)

        result as DnsVpnDatagramProcessResult.IgnorePacket
        assertEquals(6, result.tunIpVersion)
        assertEquals(58, result.tunProtocol)
    }

    @Test
    fun ipv4TcpSynIsExplicitlyRejectedWithReset() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(ipv4TcpSynPacket())

        val response = (result as DnsVpnDatagramProcessResult.SendToTun).packet
        assertEquals(4, response[0].toInt() ushr 4)
        assertEquals(6, response[9].toInt())
        assertEquals(53, response.readUnsignedShort(20))
        assertEquals(42_000, response.readUnsignedShort(22))
        assertEquals(0x14, response[33].toInt() and 0xFF)
        assertEquals(0x1020_3041, response.readInt(28))
        assertEquals(0, internetChecksum(response, 0, 20))
        assertEquals(
            0,
            tcpChecksumIpv4(
                sourceAddress = response.copyOfRange(12, 16),
                destinationAddress = response.copyOfRange(16, 20),
                tcpSegment = response.copyOfRange(20, 40),
            ),
        )
    }

    @Test
    fun ipv6TcpSynIsExplicitlyRejectedWithReset() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(ipv6TcpSynPacket())

        val response = (result as DnsVpnDatagramProcessResult.SendToTun).packet
        assertEquals(6, response[0].toInt() ushr 4)
        assertEquals(6, response[6].toInt())
        assertEquals(53, response.readUnsignedShort(40))
        assertEquals(42_000, response.readUnsignedShort(42))
        assertEquals(0x14, response[53].toInt() and 0xFF)
        assertEquals(0x1020_3041, response.readInt(48))
        assertEquals(
            0,
            tcpChecksumIpv6(
                sourceAddress = response.copyOfRange(8, 24),
                destinationAddress = response.copyOfRange(24, 40),
                tcpSegment = response.copyOfRange(40, 60),
            ),
        )
    }

    @Test
    fun tcpSynAndFinBothConsumeSequenceSpaceInResetAcknowledgement() {
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(ipv4TcpSynPacket(flags = 0x03))

        val response = (result as DnsVpnDatagramProcessResult.SendToTun).packet
        assertEquals(0x1020_3042, response.readInt(28))
    }

    @Test
    fun incomingTcpResetIsIgnoredWithoutCreatingAResetLoop() {
        val packet = ipv4TcpSynPacket().apply {
            this[33] = 0x04
        }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(packet)

        result as DnsVpnDatagramProcessResult.IgnorePacket
        assertEquals(4, result.tunIpVersion)
        assertEquals(6, result.tunProtocol)
    }

    @Test
    fun malformedTcpPacketRequestsFailOpen() {
        val packet = ByteArray(20).apply {
            this[0] = 0x45
            writeUnsignedShort(2, size)
            this[9] = 6
        }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { dnsResponse(it) },
        )

        val result = processor.process(packet)

        assertEquals(
            DnsVpnDatagramProcessStopReason.TunPacketRejected,
            result.requireStopReason(),
        )
        assertEquals(
            DnsTunPacketFailureReason.UnsupportedProtocol,
            result.requireTunFailureReason(),
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
    fun oversizedUpstreamResponseRequestsFailOpenStopVpnBeforeResponseBuild() {
        val oversizedPayload = ByteArray(65_508) { 0x42 }
        val processor = DnsVpnDatagramProcessor(
            blockedDomains = emptySet(),
            upstreamExchange = { oversizedPayload },
        )

        val result = processor.process(ipv4DnsPacket(dnsQuery("allowed.example")))

        assertEquals(
            DnsVpnDatagramProcessStopReason.UpstreamRejected,
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

    private fun DnsVpnDatagramProcessResult.requireTunFailureReason(): DnsTunPacketFailureReason {
        this as DnsVpnDatagramProcessResult.FailOpenStopVpn
        return tunFailureReason ?: error("Expected TUN failure reason")
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

    private fun ByteArray.withTransactionId(transactionId: Int): ByteArray =
        copyOf().also { response ->
            response[0] = (transactionId ushr 8).toByte()
            response[1] = transactionId.toByte()
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

    private fun ipv4TcpSynPacket(flags: Int = 0x02): ByteArray =
        ByteArray(40).apply {
            this[0] = 0x45
            writeUnsignedShort(2, size)
            this[8] = 64
            this[9] = 6
            byteArrayOf(10, 0, 0, 2).copyInto(this, 12)
            byteArrayOf(10, 0, 0, 1).copyInto(this, 16)
            writeUnsignedShort(20, 42_000)
            writeUnsignedShort(22, 53)
            writeInt(24, 0x1020_3040)
            this[32] = 0x50
            this[33] = flags.toByte()
        }

    private fun ipv6TcpSynPacket(): ByteArray =
        ByteArray(60).apply {
            this[0] = 0x60
            writeUnsignedShort(4, 20)
            this[6] = 6
            this[7] = 64
            ByteArray(16) { index -> (index + 1).toByte() }.copyInto(this, 8)
            ByteArray(16) { index -> (index + 17).toByte() }.copyInto(this, 24)
            writeUnsignedShort(40, 42_000)
            writeUnsignedShort(42, 53)
            writeInt(44, 0x1020_3040)
            this[52] = 0x50
            this[53] = 0x02
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

    private fun ByteArray.readInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.writeUnsignedShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.writeInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
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

    private fun tcpChecksumIpv4(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        tcpSegment: ByteArray,
    ): Int {
        val pseudoPacket = ByteArray(12 + tcpSegment.size)
        sourceAddress.copyInto(pseudoPacket, 0)
        destinationAddress.copyInto(pseudoPacket, 4)
        pseudoPacket[9] = 6
        pseudoPacket.writeUnsignedShort(10, tcpSegment.size)
        tcpSegment.copyInto(pseudoPacket, 12)
        return internetChecksum(pseudoPacket, 0, pseudoPacket.size)
    }

    private fun tcpChecksumIpv6(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        tcpSegment: ByteArray,
    ): Int {
        val pseudoPacket = ByteArray(40 + tcpSegment.size)
        sourceAddress.copyInto(pseudoPacket, 0)
        destinationAddress.copyInto(pseudoPacket, 16)
        pseudoPacket.writeInt(32, tcpSegment.size)
        pseudoPacket[39] = 6
        tcpSegment.copyInto(pseudoPacket, 40)
        return internetChecksum(pseudoPacket, 0, pseudoPacket.size)
    }
}
