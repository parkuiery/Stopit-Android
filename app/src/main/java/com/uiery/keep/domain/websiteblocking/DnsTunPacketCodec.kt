package com.uiery.keep.domain.websiteblocking

enum class DnsTunIpVersion {
    IPv4,
    IPv6,
}

data class DnsTunDatagram(
    val ipVersion: DnsTunIpVersion,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsPayload: ByteArray,
)

sealed interface DnsTunPacketParseResult {
    data class Parsed(val datagram: DnsTunDatagram) : DnsTunPacketParseResult
    data class Failure(val reason: DnsTunPacketFailureReason) : DnsTunPacketParseResult
}

enum class DnsTunPacketFailureReason {
    PacketTooShort,
    MalformedPacket,
    UnsupportedProtocol,
    FragmentedPacket,
    NonDnsPort,
}

object DnsTunPacketCodec {
    private const val IPV4_MIN_HEADER_LENGTH = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val UDP_HEADER_LENGTH = 8
    private const val DNS_PORT = 53
    private const val UDP_PROTOCOL = 17
    private const val IPV4_FLAG_MORE_FRAGMENTS = 0x2000
    private const val IPV4_FRAGMENT_OFFSET_MASK = 0x1FFF
    private const val RESPONSE_TTL_OR_HOP_LIMIT = 64

    fun parseDnsUdpDatagram(packet: ByteArray): DnsTunPacketParseResult =
        try {
            if (packet.isEmpty()) {
                DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
            } else {
                when (packet[0].unsigned() ushr 4) {
                    4 -> parseIpv4DnsUdpDatagram(packet)
                    6 -> parseIpv6DnsUdpDatagram(packet)
                    else -> DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.UnsupportedProtocol)
                }
            }
        } catch (_: RuntimeException) {
            DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }

    fun buildDnsUdpResponse(request: DnsTunDatagram, dnsPayload: ByteArray): ByteArray =
        when (request.ipVersion) {
            DnsTunIpVersion.IPv4 -> buildIpv4UdpResponse(request, dnsPayload.copyOf())
            DnsTunIpVersion.IPv6 -> buildIpv6UdpResponse(request, dnsPayload.copyOf())
        }

    private fun parseIpv4DnsUdpDatagram(packet: ByteArray): DnsTunPacketParseResult {
        if (packet.size < IPV4_MIN_HEADER_LENGTH) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        val headerLength = (packet[0].unsigned() and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER_LENGTH) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }
        if (packet.size < headerLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        val totalLength = packet.readUnsignedShort(2)
        if (totalLength < headerLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }
        if (packet.size < totalLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        if (packet[9].unsigned() != UDP_PROTOCOL) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.UnsupportedProtocol)
        }

        val fragmentFlagsAndOffset = packet.readUnsignedShort(6)
        if ((fragmentFlagsAndOffset and IPV4_FLAG_MORE_FRAGMENTS) != 0 ||
            (fragmentFlagsAndOffset and IPV4_FRAGMENT_OFFSET_MASK) != 0
        ) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.FragmentedPacket)
        }

        if (totalLength - headerLength < UDP_HEADER_LENGTH) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        val udpLength = packet.readUnsignedShort(headerLength + 4)
        if (udpLength < UDP_HEADER_LENGTH || headerLength + udpLength > totalLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }

        val destinationPort = packet.readUnsignedShort(headerLength + 2)
        if (destinationPort != DNS_PORT) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.NonDnsPort)
        }

        return DnsTunPacketParseResult.Parsed(
            DnsTunDatagram(
                ipVersion = DnsTunIpVersion.IPv4,
                sourceAddress = packet.copyOfRange(12, 16),
                destinationAddress = packet.copyOfRange(16, 20),
                sourcePort = packet.readUnsignedShort(headerLength),
                destinationPort = destinationPort,
                dnsPayload = packet.copyOfRange(headerLength + UDP_HEADER_LENGTH, headerLength + udpLength),
            ),
        )
    }

    private fun parseIpv6DnsUdpDatagram(packet: ByteArray): DnsTunPacketParseResult {
        if (packet.size < IPV6_HEADER_LENGTH) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        val payloadLength = packet.readUnsignedShort(4)
        val declaredLength = IPV6_HEADER_LENGTH + payloadLength
        if (packet.size < declaredLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.PacketTooShort)
        }

        if (packet[6].unsigned() != UDP_PROTOCOL) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.UnsupportedProtocol)
        }
        if (payloadLength < UDP_HEADER_LENGTH) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }

        val udpLength = packet.readUnsignedShort(IPV6_HEADER_LENGTH + 4)
        if (udpLength < UDP_HEADER_LENGTH || udpLength > payloadLength) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.MalformedPacket)
        }

        val destinationPort = packet.readUnsignedShort(IPV6_HEADER_LENGTH + 2)
        if (destinationPort != DNS_PORT) {
            return DnsTunPacketParseResult.Failure(DnsTunPacketFailureReason.NonDnsPort)
        }

        return DnsTunPacketParseResult.Parsed(
            DnsTunDatagram(
                ipVersion = DnsTunIpVersion.IPv6,
                sourceAddress = packet.copyOfRange(8, 24),
                destinationAddress = packet.copyOfRange(24, 40),
                sourcePort = packet.readUnsignedShort(IPV6_HEADER_LENGTH),
                destinationPort = destinationPort,
                dnsPayload = packet.copyOfRange(
                    IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH,
                    IPV6_HEADER_LENGTH + udpLength,
                ),
            ),
        )
    }

    private fun buildIpv4UdpResponse(request: DnsTunDatagram, dnsPayload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        val totalLength = IPV4_MIN_HEADER_LENGTH + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet.writeUnsignedShort(2, totalLength)
        packet[8] = RESPONSE_TTL_OR_HOP_LIMIT.toByte()
        packet[9] = UDP_PROTOCOL.toByte()
        request.destinationAddress.copyInto(packet, 12, 0, 4)
        request.sourceAddress.copyInto(packet, 16, 0, 4)
        packet.writeUnsignedShort(10, internetChecksum(packet, 0, IPV4_MIN_HEADER_LENGTH))

        val udpOffset = IPV4_MIN_HEADER_LENGTH
        packet.writeUnsignedShort(udpOffset, request.destinationPort)
        packet.writeUnsignedShort(udpOffset + 2, request.sourcePort)
        packet.writeUnsignedShort(udpOffset + 4, udpLength)
        dnsPayload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        val udpChecksum = udpChecksumIpv4(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            udpSegment = packet.copyOfRange(udpOffset, packet.size),
        )
        packet.writeUnsignedShort(udpOffset + 6, udpChecksum)

        return packet
    }

    private fun buildIpv6UdpResponse(request: DnsTunDatagram, dnsPayload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsPayload.size
        val packet = ByteArray(IPV6_HEADER_LENGTH + udpLength)

        packet[0] = 0x60
        packet.writeUnsignedShort(4, udpLength)
        packet[6] = UDP_PROTOCOL.toByte()
        packet[7] = RESPONSE_TTL_OR_HOP_LIMIT.toByte()
        request.destinationAddress.copyInto(packet, 8, 0, 16)
        request.sourceAddress.copyInto(packet, 24, 0, 16)

        val udpOffset = IPV6_HEADER_LENGTH
        packet.writeUnsignedShort(udpOffset, request.destinationPort)
        packet.writeUnsignedShort(udpOffset + 2, request.sourcePort)
        packet.writeUnsignedShort(udpOffset + 4, udpLength)
        dnsPayload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        val udpChecksum = udpChecksumIpv6(
            sourceAddress = packet.copyOfRange(8, 24),
            destinationAddress = packet.copyOfRange(24, 40),
            udpSegment = packet.copyOfRange(udpOffset, packet.size),
        )
        packet.writeUnsignedShort(udpOffset + 6, udpChecksum)

        return packet
    }

    private fun udpChecksumIpv4(sourceAddress: ByteArray, destinationAddress: ByteArray, udpSegment: ByteArray): Int {
        val pseudoHeader = sourceAddress +
            destinationAddress +
            byteArrayOf(0, UDP_PROTOCOL.toByte()) +
            byteArrayOf((udpSegment.size ushr 8).toByte(), udpSegment.size.toByte())
        return udpChecksum(pseudoHeader + udpSegment)
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
                UDP_PROTOCOL.toByte(),
            )
        return udpChecksum(pseudoHeader + udpSegment)
    }

    private fun internetChecksum(packet: ByteArray, offset: Int = 0, length: Int = packet.size): Int {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index < end) {
            val high = packet[index].unsigned()
            val low = if (index + 1 < end) packet[index + 1].unsigned() else 0
            sum += ((high shl 8) or low).toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun udpChecksum(checksumInput: ByteArray): Int {
        val checksum = internetChecksum(checksumInput)
        return if (checksum == 0) 0xFFFF else checksum
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun ByteArray.writeUnsignedShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
