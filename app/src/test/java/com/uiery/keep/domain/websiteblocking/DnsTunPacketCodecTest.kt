package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsTunPacketCodecTest {
    @Test
    fun parsesMinimalIpv4DnsUdpQuery() {
        val dnsPayload = byteArrayOf(0x12, 0x34, 0x01, 0x00)
        val packet = ipv4UdpPacket(
            sourceAddress = byteArrayOf(10, 0, 0, 2),
            destinationAddress = byteArrayOf(8, 8, 8, 8),
            sourcePort = 43000,
            destinationPort = 53,
            payload = dnsPayload,
        )

        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()

        assertEquals(DnsTunIpVersion.IPv4, datagram.ipVersion)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), datagram.sourceAddress)
        assertArrayEquals(byteArrayOf(8, 8, 8, 8), datagram.destinationAddress)
        assertEquals(43000, datagram.sourcePort)
        assertEquals(53, datagram.destinationPort)
        assertArrayEquals(dnsPayload, datagram.dnsPayload)
    }

    @Test
    fun parsesMinimalIpv6DnsUdpQuery() {
        val source = bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val destination = bytes(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x88)
        val dnsPayload = byteArrayOf(0xCA.toByte(), 0xFE.toByte())
        val packet = ipv6UdpPacket(
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = 54000,
            destinationPort = 53,
            payload = dnsPayload,
        )

        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()

        assertEquals(DnsTunIpVersion.IPv6, datagram.ipVersion)
        assertArrayEquals(source, datagram.sourceAddress)
        assertArrayEquals(destination, datagram.destinationAddress)
        assertEquals(54000, datagram.sourcePort)
        assertEquals(53, datagram.destinationPort)
        assertArrayEquals(dnsPayload, datagram.dnsPayload)
    }

    @Test
    fun parsesIpv4PacketWithOptions() {
        val options = byteArrayOf(1, 1, 1, 1)
        val packet = ipv4UdpPacket(
            sourceAddress = byteArrayOf(10, 0, 0, 2),
            destinationAddress = byteArrayOf(1, 1, 1, 1),
            sourcePort = 12345,
            destinationPort = 53,
            payload = byteArrayOf(0x01, 0x02, 0x03),
            options = options,
        )

        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), datagram.dnsPayload)
    }

    @Test
    fun ignoresTrailingBytesBeyondDeclaredIpv4TotalLength() {
        val payload = byteArrayOf(0x01, 0x02)
        val packet = ipv4UdpPacket(payload = payload) + byteArrayOf(0x55, 0x66, 0x77)

        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()

        assertArrayEquals(payload, datagram.dnsPayload)
    }

    @Test
    fun ignoresTrailingBytesBeyondDeclaredIpv6PayloadLength() {
        val payload = byteArrayOf(0x03, 0x04)
        val packet = ipv6UdpPacket(payload = payload) + byteArrayOf(0x55, 0x66, 0x77)

        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()

        assertArrayEquals(payload, datagram.dnsPayload)
    }

    @Test
    fun buildsIpv4UdpDnsResponseWithSwappedEndpointsAndValidChecksums() {
        val requestPayload = byteArrayOf(0x10, 0x20, 0x30)
        val requestPacket = ipv4UdpPacket(
            sourceAddress = byteArrayOf(10, 0, 0, 9),
            destinationAddress = byteArrayOf(8, 8, 4, 4),
            sourcePort = 53000,
            destinationPort = 53,
            payload = requestPayload,
        )
        val request = DnsTunPacketCodec.parseDnsUdpDatagram(requestPacket).requireParsed()
        val originalRequestPacket = requestPacket.copyOf()
        val originalRequestPayload = request.dnsPayload.copyOf()
        val responsePayload = byteArrayOf(0x44, 0x55, 0x66, 0x77)

        val response = DnsTunPacketCodec.buildDnsUdpResponse(request, responsePayload)

        assertArrayEquals(originalRequestPacket, requestPacket)
        assertArrayEquals(originalRequestPayload, request.dnsPayload)
        assertEquals(4, response[0].unsigned() ushr 4)
        assertEquals(20 + 8 + responsePayload.size, response.readUnsignedShort(2))
        assertEquals(64, response[8].unsigned())
        assertEquals(17, response[9].unsigned())
        assertEquals(0, rawInternetChecksum(response, 0, 20))
        assertArrayEquals(byteArrayOf(8, 8, 4, 4), response.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 0, 0, 9), response.copyOfRange(16, 20))
        assertEquals(53, response.readUnsignedShort(20))
        assertEquals(53000, response.readUnsignedShort(22))
        assertEquals(8 + responsePayload.size, response.readUnsignedShort(24))
        assertArrayEquals(responsePayload, response.copyOfRange(28, 28 + responsePayload.size))
        assertEquals(
            response.readUnsignedShort(26),
            udpChecksumIpv4(
                sourceAddress = byteArrayOf(8, 8, 4, 4),
                destinationAddress = byteArrayOf(10, 0, 0, 9),
                udpSegment = response.copyOfRange(20, response.size).withZeroedUdpChecksum(),
            ),
        )
    }

    @Test
    fun buildsIpv6UdpDnsResponseWithSwappedEndpointsAndValidChecksum() {
        val source = bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val destination = bytes(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x88)
        val requestPacket = ipv6UdpPacket(
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = 53001,
            destinationPort = 53,
            payload = byteArrayOf(0x10, 0x11),
        )
        val request = DnsTunPacketCodec.parseDnsUdpDatagram(requestPacket).requireParsed()
        val responsePayload = byteArrayOf(0x21, 0x22, 0x23)

        val response = DnsTunPacketCodec.buildDnsUdpResponse(request, responsePayload)

        assertEquals(6, response[0].unsigned() ushr 4)
        assertEquals(8 + responsePayload.size, response.readUnsignedShort(4))
        assertEquals(17, response[6].unsigned())
        assertEquals(64, response[7].unsigned())
        assertArrayEquals(destination, response.copyOfRange(8, 24))
        assertArrayEquals(source, response.copyOfRange(24, 40))
        assertEquals(53, response.readUnsignedShort(40))
        assertEquals(53001, response.readUnsignedShort(42))
        assertEquals(8 + responsePayload.size, response.readUnsignedShort(44))
        assertArrayEquals(responsePayload, response.copyOfRange(48, 48 + responsePayload.size))
        assertEquals(
            response.readUnsignedShort(46),
            udpChecksumIpv6(
                sourceAddress = destination,
                destinationAddress = source,
                udpSegment = response.copyOfRange(40, response.size).withZeroedUdpChecksum(),
            ),
        )
    }

    @Test
    fun copiedModelBytesAreImmutableFromInputAndResponsePayload() {
        val packet = ipv4UdpPacket(
            sourceAddress = byteArrayOf(10, 0, 0, 2),
            destinationAddress = byteArrayOf(9, 9, 9, 9),
            payload = byteArrayOf(0x01, 0x02, 0x03),
        )
        val datagram = DnsTunPacketCodec.parseDnsUdpDatagram(packet).requireParsed()
        val responsePayload = byteArrayOf(0x04, 0x05)

        packet[12] = 127
        packet[28] = 0x7F
        val response = DnsTunPacketCodec.buildDnsUdpResponse(datagram, responsePayload)
        responsePayload[0] = 0x7F

        assertArrayEquals(byteArrayOf(10, 0, 0, 2), datagram.sourceAddress)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), datagram.dnsPayload)
        assertArrayEquals(byteArrayOf(0x04, 0x05), response.copyOfRange(28, 30))
    }

    @Test
    fun rejectsMalformedPacketsWithTypedFailures() {
        assertFailure(DnsTunPacketFailureReason.PacketTooShort, ByteArray(0))
        assertFailure(DnsTunPacketFailureReason.PacketTooShort, bytes(0x45, 0, 0, 19))
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv4UdpPacket().also { it[0] = 0x44 })
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv4UdpPacket().also { it.writeUnsignedShort(2, 19) })
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv4UdpPacket().also { it.writeUnsignedShort(24, 7) })
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv4UdpPacket().also { it.writeUnsignedShort(24, 2000) })
        assertFailure(DnsTunPacketFailureReason.FragmentedPacket, ipv4UdpPacket().also { it.writeUnsignedShort(6, 0x2000) })
        assertFailure(DnsTunPacketFailureReason.FragmentedPacket, ipv4UdpPacket().also { it.writeUnsignedShort(6, 0x0001) })
        assertFailure(DnsTunPacketFailureReason.UnsupportedProtocol, ipv4UdpPacket(protocol = 6))
        assertFailure(DnsTunPacketFailureReason.NonDnsPort, ipv4UdpPacket(destinationPort = 54))
        assertFailure(DnsTunPacketFailureReason.PacketTooShort, bytes(0x60, 0, 0, 0, 0, 8, 17, 64))
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv6UdpPacket().also { it.writeUnsignedShort(4, 7) })
        assertFailure(DnsTunPacketFailureReason.MalformedPacket, ipv6UdpPacket().also { it.writeUnsignedShort(44, 2000) })
        assertFailure(DnsTunPacketFailureReason.UnsupportedProtocol, ipv6UdpPacket(nextHeader = 6))
        assertFailure(DnsTunPacketFailureReason.UnsupportedProtocol, ipv6UdpPacket(nextHeader = 0))
        assertFailure(DnsTunPacketFailureReason.NonDnsPort, ipv6UdpPacket(destinationPort = 5353))
    }

    @Test
    fun parsesMaximumLegalUdpPayloadsWithoutIntegerOverflowOrOutOfBoundsAccess() {
        val ipv4Payload = ByteArray(65_507) { (it and 0xFF).toByte() }
        val ipv6Payload = ByteArray(65_527) { ((it + 1) and 0xFF).toByte() }

        val ipv4 = DnsTunPacketCodec.parseDnsUdpDatagram(ipv4UdpPacket(payload = ipv4Payload)).requireParsed()
        val ipv6 = DnsTunPacketCodec.parseDnsUdpDatagram(ipv6UdpPacket(payload = ipv6Payload)).requireParsed()

        assertEquals(65_507, ipv4.dnsPayload.size)
        assertEquals(65_527, ipv6.dnsPayload.size)
        assertArrayEquals(ipv4Payload, ipv4.dnsPayload)
        assertArrayEquals(ipv6Payload, ipv6.dnsPayload)
    }

    private fun assertFailure(expected: DnsTunPacketFailureReason, packet: ByteArray) {
        val failure = DnsTunPacketCodec.parseDnsUdpDatagram(packet) as? DnsTunPacketParseResult.Failure
        requireNotNull(failure) { "Expected failure $expected" }
        assertEquals(expected, failure.reason)
    }

    private fun DnsTunPacketParseResult.requireParsed(): DnsTunDatagram {
        val parsed = this as? DnsTunPacketParseResult.Parsed
        requireNotNull(parsed) { "Expected parsed datagram but was $this" }
        return parsed.datagram
    }

    private fun ipv4UdpPacket(
        sourceAddress: ByteArray = byteArrayOf(10, 0, 0, 2),
        destinationAddress: ByteArray = byteArrayOf(8, 8, 8, 8),
        sourcePort: Int = 40000,
        destinationPort: Int = 53,
        protocol: Int = 17,
        payload: ByteArray = byteArrayOf(0x01),
        options: ByteArray = byteArrayOf(),
    ): ByteArray {
        require(options.size % 4 == 0)
        val headerLength = 20 + options.size
        val totalLength = headerLength + 8 + payload.size
        val packet = ByteArray(totalLength)
        packet[0] = ((4 shl 4) or (headerLength / 4)).toByte()
        packet.writeUnsignedShort(2, totalLength)
        packet[8] = 64
        packet[9] = protocol.toByte()
        sourceAddress.copyInto(packet, 12)
        destinationAddress.copyInto(packet, 16)
        options.copyInto(packet, 20)
        val udpOffset = headerLength
        packet.writeUnsignedShort(udpOffset, sourcePort)
        packet.writeUnsignedShort(udpOffset + 2, destinationPort)
        packet.writeUnsignedShort(udpOffset + 4, 8 + payload.size)
        payload.copyInto(packet, udpOffset + 8)
        packet.writeUnsignedShort(udpOffset + 6, udpChecksumIpv4(sourceAddress, destinationAddress, packet.copyOfRange(udpOffset, packet.size)))
        packet.writeUnsignedShort(10, internetChecksum(packet, 0, headerLength))
        return packet
    }

    private fun ipv6UdpPacket(
        sourceAddress: ByteArray = bytes(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
        destinationAddress: ByteArray = bytes(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x88),
        sourcePort: Int = 40000,
        destinationPort: Int = 53,
        nextHeader: Int = 17,
        payload: ByteArray = byteArrayOf(0x01),
    ): ByteArray {
        val udpLength = 8 + payload.size
        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60
        packet.writeUnsignedShort(4, udpLength)
        packet[6] = nextHeader.toByte()
        packet[7] = 64
        sourceAddress.copyInto(packet, 8)
        destinationAddress.copyInto(packet, 24)
        packet.writeUnsignedShort(40, sourcePort)
        packet.writeUnsignedShort(42, destinationPort)
        packet.writeUnsignedShort(44, udpLength)
        payload.copyInto(packet, 48)
        packet.writeUnsignedShort(46, udpChecksumIpv6(sourceAddress, destinationAddress, packet.copyOfRange(40, packet.size)))
        return packet
    }

    private fun udpChecksumIpv4(sourceAddress: ByteArray, destinationAddress: ByteArray, udpSegment: ByteArray): Int {
        val pseudoHeader = sourceAddress +
            destinationAddress +
            byteArrayOf(0, 17) +
            byteArrayOf((udpSegment.size ushr 8).toByte(), udpSegment.size.toByte())
        return finalizedChecksum(pseudoHeader + udpSegment)
    }

    private fun udpChecksumIpv6(sourceAddress: ByteArray, destinationAddress: ByteArray, udpSegment: ByteArray): Int {
        val udpLength = udpSegment.size
        val pseudoHeader = sourceAddress +
            destinationAddress +
            byteArrayOf(
                (udpLength ushr 24).toByte(),
                (udpLength ushr 16).toByte(),
                (udpLength ushr 8).toByte(),
                udpLength.toByte(),
                0,
                0,
                0,
                17,
            )
        return finalizedChecksum(pseudoHeader + udpSegment)
    }

    private fun internetChecksum(packet: ByteArray, offset: Int, length: Int): Int =
        finalizedChecksum(packet.copyOfRange(offset, offset + length))

    private fun rawInternetChecksum(packet: ByteArray, offset: Int, length: Int): Int =
        rawFinalizedChecksum(packet.copyOfRange(offset, offset + length))

    private fun finalizedChecksum(bytes: ByteArray): Int {
        val checksum = rawFinalizedChecksum(bytes)
        return if (checksum == 0) 0xFFFF else checksum
    }

    private fun rawFinalizedChecksum(bytes: ByteArray): Int {
        var sum = 0L
        var index = 0
        while (index < bytes.size) {
            val high = bytes[index].unsigned()
            val low = if (index + 1 < bytes.size) bytes[index + 1].unsigned() else 0
            sum += ((high shl 8) or low).toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun ByteArray.withZeroedUdpChecksum(): ByteArray =
        copyOf().also { it.writeUnsignedShort(6, 0) }

    private fun bytes(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun ByteArray.writeUnsignedShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
