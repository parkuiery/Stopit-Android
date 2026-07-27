package com.uiery.keep.websiteblocking

private const val IPV4_HEADER_MIN_LENGTH = 20
private const val IPV6_HEADER_LENGTH = 40
private const val TCP_HEADER_MIN_LENGTH = 20
private const val TCP_PROTOCOL = 6
private const val IPV6_HOP_BY_HOP_OPTIONS = 0
private const val IPV6_EXTENSION_MIN_LENGTH = 8
private const val IPV4_MORE_FRAGMENTS = 0x2000
private const val IPV4_FRAGMENT_OFFSET_MASK = 0x1FFF
private const val TCP_FLAG_FIN = 0x01
private const val TCP_FLAG_SYN = 0x02
private const val TCP_FLAG_RST = 0x04
private const val TCP_FLAG_ACK = 0x10
private const val RESPONSE_TTL_OR_HOP_LIMIT = 64

object DnsTunTcpResetCodec {
    fun buildReset(packet: ByteArray): DnsTunTcpResetResult =
        when (packet.firstOrNull()?.unsigned()?.ushr(4)) {
            4 -> buildIpv4Reset(packet)
            6 -> buildIpv6Reset(packet)
            else -> DnsTunTcpResetResult.NotResettable
        }

    private fun buildIpv4Reset(packet: ByteArray): DnsTunTcpResetResult {
        if (packet.size < IPV4_HEADER_MIN_LENGTH) return DnsTunTcpResetResult.NotResettable
        val ipHeaderLength = (packet[0].unsigned() and 0x0F) * 4
        if (ipHeaderLength < IPV4_HEADER_MIN_LENGTH || packet.size < ipHeaderLength) {
            return DnsTunTcpResetResult.NotResettable
        }
        val totalLength = packet.readUnsignedShort(2)
        if (totalLength != packet.size || totalLength < ipHeaderLength + TCP_HEADER_MIN_LENGTH) {
            return DnsTunTcpResetResult.NotResettable
        }
        if (packet[9].unsigned() != TCP_PROTOCOL) return DnsTunTcpResetResult.NotResettable
        val fragmentBits = packet.readUnsignedShort(6)
        if ((fragmentBits and (IPV4_MORE_FRAGMENTS or IPV4_FRAGMENT_OFFSET_MASK)) != 0) {
            return DnsTunTcpResetResult.NotResettable
        }

        val tcpOffset = ipHeaderLength
        if ((packet[tcpOffset + 13].unsigned() and TCP_FLAG_RST) != 0) {
            return DnsTunTcpResetResult.IgnoreReset
        }
        val resetHeader = buildTcpResetHeader(packet, tcpOffset, totalLength - tcpOffset)
            ?: return DnsTunTcpResetResult.NotResettable
        val sourceAddress = packet.copyOfRange(12, 16)
        val destinationAddress = packet.copyOfRange(16, 20)
        val response = ByteArray(IPV4_HEADER_MIN_LENGTH + TCP_HEADER_MIN_LENGTH)
        response[0] = 0x45
        response.writeUnsignedShort(2, response.size)
        response[8] = RESPONSE_TTL_OR_HOP_LIMIT.toByte()
        response[9] = TCP_PROTOCOL.toByte()
        destinationAddress.copyInto(response, 12)
        sourceAddress.copyInto(response, 16)
        response.writeUnsignedShort(10, internetChecksum(response, 0, IPV4_HEADER_MIN_LENGTH))
        resetHeader.copyInto(response, IPV4_HEADER_MIN_LENGTH)
        response.writeUnsignedShort(
            IPV4_HEADER_MIN_LENGTH + 16,
            tcpChecksumIpv4(destinationAddress, sourceAddress, resetHeader),
        )
        return DnsTunTcpResetResult.Success(response)
    }

    private fun buildIpv6Reset(packet: ByteArray): DnsTunTcpResetResult {
        if (packet.size < IPV6_HEADER_LENGTH) return DnsTunTcpResetResult.NotResettable
        val declaredLength = IPV6_HEADER_LENGTH + packet.readUnsignedShort(4)
        if (declaredLength != packet.size) return DnsTunTcpResetResult.NotResettable

        var nextHeader = packet[6].unsigned()
        var tcpOffset = IPV6_HEADER_LENGTH
        if (nextHeader == IPV6_HOP_BY_HOP_OPTIONS) {
            if (declaredLength - tcpOffset < IPV6_EXTENSION_MIN_LENGTH) {
                return DnsTunTcpResetResult.NotResettable
            }
            nextHeader = packet[tcpOffset].unsigned()
            val extensionLength = (packet[tcpOffset + 1].unsigned() + 1) * 8
            if (extensionLength > declaredLength - tcpOffset) {
                return DnsTunTcpResetResult.NotResettable
            }
            tcpOffset += extensionLength
        }
        if (nextHeader != TCP_PROTOCOL || declaredLength < tcpOffset + TCP_HEADER_MIN_LENGTH) {
            return DnsTunTcpResetResult.NotResettable
        }

        if ((packet[tcpOffset + 13].unsigned() and TCP_FLAG_RST) != 0) {
            return DnsTunTcpResetResult.IgnoreReset
        }
        val resetHeader = buildTcpResetHeader(packet, tcpOffset, declaredLength - tcpOffset)
            ?: return DnsTunTcpResetResult.NotResettable
        val sourceAddress = packet.copyOfRange(8, 24)
        val destinationAddress = packet.copyOfRange(24, 40)
        val response = ByteArray(IPV6_HEADER_LENGTH + TCP_HEADER_MIN_LENGTH)
        response[0] = 0x60
        response.writeUnsignedShort(4, TCP_HEADER_MIN_LENGTH)
        response[6] = TCP_PROTOCOL.toByte()
        response[7] = RESPONSE_TTL_OR_HOP_LIMIT.toByte()
        destinationAddress.copyInto(response, 8)
        sourceAddress.copyInto(response, 24)
        resetHeader.copyInto(response, IPV6_HEADER_LENGTH)
        response.writeUnsignedShort(
            IPV6_HEADER_LENGTH + 16,
            tcpChecksumIpv6(destinationAddress, sourceAddress, resetHeader),
        )
        return DnsTunTcpResetResult.Success(response)
    }

    private fun buildTcpResetHeader(
        packet: ByteArray,
        tcpOffset: Int,
        segmentLength: Int,
    ): ByteArray? {
        if (segmentLength < TCP_HEADER_MIN_LENGTH || tcpOffset + segmentLength > packet.size) return null
        val tcpHeaderLength = (packet[tcpOffset + 12].unsigned() ushr 4) * 4
        if (tcpHeaderLength < TCP_HEADER_MIN_LENGTH || tcpHeaderLength > segmentLength) return null
        val incomingFlags = packet[tcpOffset + 13].unsigned()
        val response = ByteArray(TCP_HEADER_MIN_LENGTH)
        response.writeUnsignedShort(0, packet.readUnsignedShort(tcpOffset + 2))
        response.writeUnsignedShort(2, packet.readUnsignedShort(tcpOffset))
        if ((incomingFlags and TCP_FLAG_ACK) != 0) {
            response.writeInt(4, packet.readInt(tcpOffset + 8))
            response[13] = TCP_FLAG_RST.toByte()
        } else {
            val payloadLength = segmentLength - tcpHeaderLength
            val synLength = if ((incomingFlags and TCP_FLAG_SYN) != 0) 1 else 0
            val finLength = if ((incomingFlags and TCP_FLAG_FIN) != 0) 1 else 0
            val sequenceSpaceLength =
                payloadLength + synLength + finLength
            response.writeInt(8, packet.readInt(tcpOffset + 4) + sequenceSpaceLength)
            response[13] = (TCP_FLAG_RST or TCP_FLAG_ACK).toByte()
        }
        response[12] = 0x50
        return response
    }

    private fun tcpChecksumIpv4(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        tcpHeader: ByteArray,
    ): Int {
        val pseudoPacket = ByteArray(12 + tcpHeader.size)
        sourceAddress.copyInto(pseudoPacket, 0)
        destinationAddress.copyInto(pseudoPacket, 4)
        pseudoPacket[9] = TCP_PROTOCOL.toByte()
        pseudoPacket.writeUnsignedShort(10, tcpHeader.size)
        tcpHeader.copyInto(pseudoPacket, 12)
        return internetChecksum(pseudoPacket, 0, pseudoPacket.size)
    }

    private fun tcpChecksumIpv6(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        tcpHeader: ByteArray,
    ): Int {
        val pseudoPacket = ByteArray(40 + tcpHeader.size)
        sourceAddress.copyInto(pseudoPacket, 0)
        destinationAddress.copyInto(pseudoPacket, 16)
        pseudoPacket.writeInt(32, tcpHeader.size)
        pseudoPacket[39] = TCP_PROTOCOL.toByte()
        tcpHeader.copyInto(pseudoPacket, 40)
        return internetChecksum(pseudoPacket, 0, pseudoPacket.size)
    }

    private fun internetChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index < offset + length) {
            val high = packet[index].unsigned()
            val low = if (index + 1 < offset + length) packet[index + 1].unsigned() else 0
            sum += ((high shl 8) or low).toLong()
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun ByteArray.readInt(offset: Int): Int =
        (this[offset].unsigned() shl 24) or
            (this[offset + 1].unsigned() shl 16) or
            (this[offset + 2].unsigned() shl 8) or
            this[offset + 3].unsigned()

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

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}

sealed interface DnsTunTcpResetResult {
    data class Success(val packet: ByteArray) : DnsTunTcpResetResult

    data object IgnoreReset : DnsTunTcpResetResult

    data object NotResettable : DnsTunTcpResetResult
}
