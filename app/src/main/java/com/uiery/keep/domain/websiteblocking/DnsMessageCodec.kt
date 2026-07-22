package com.uiery.keep.domain.websiteblocking

import java.util.Locale

data class DnsQuery(
    val transactionId: Int,
    val flags: Int,
    val queryName: String,
    val qtype: Int,
    val qclass: Int,
)

sealed interface DnsQueryParseResult {
    data class Parsed(val query: DnsQuery) : DnsQueryParseResult
    data class Failure(val reason: DnsQueryFailureReason) : DnsQueryParseResult
}

enum class DnsQueryFailureReason {
    PacketTooShort,
    ResponsePacket,
    QuestionCountNotOne,
    TruncatedQuestion,
    ReservedLabelEncoding,
    PointerOutOfBounds,
    PointerLoop,
    ExcessivePointerHops,
    NameTooLong,
    InvalidEmptyLabel,
    NonAsciiLabel,
    InvalidLabelCharacter,
}

enum class DnsBlockDecision {
    Allow,
    Block,
}

object DnsMessageCodec {
    private const val DNS_HEADER_LENGTH = 12
    private const val MAX_NAME_LENGTH = 253
    private const val MAX_POINTER_HOPS = 16
    private const val QR_MASK = 0x8000
    private const val OPCODE_MASK = 0x7800
    private const val RD_MASK = 0x0100
    private const val RA_MASK = 0x0080
    private const val CD_MASK = 0x0010
    private const val RCODE_NXDOMAIN = 0x0003

    fun parseQuery(packet: ByteArray): DnsQueryParseResult {
        if (packet.size < DNS_HEADER_LENGTH) {
            return DnsQueryParseResult.Failure(DnsQueryFailureReason.PacketTooShort)
        }

        val flags = packet.readUnsignedShort(2)
        if ((flags and QR_MASK) != 0) {
            return DnsQueryParseResult.Failure(DnsQueryFailureReason.ResponsePacket)
        }

        if (packet.readUnsignedShort(4) != 1) {
            return DnsQueryParseResult.Failure(DnsQueryFailureReason.QuestionCountNotOne)
        }

        val nameResult = decodeName(packet, DNS_HEADER_LENGTH)
        if (nameResult is NameDecodeResult.Failure) {
            return DnsQueryParseResult.Failure(nameResult.reason)
        }
        val decoded = nameResult as NameDecodeResult.Decoded
        val qtypeOffset = decoded.nextOffset
        if (qtypeOffset + 4 > packet.size) {
            return DnsQueryParseResult.Failure(DnsQueryFailureReason.TruncatedQuestion)
        }

        return DnsQueryParseResult.Parsed(
            DnsQuery(
                transactionId = packet.readUnsignedShort(0),
                flags = flags,
                queryName = decoded.name,
                qtype = packet.readUnsignedShort(qtypeOffset),
                qclass = packet.readUnsignedShort(qtypeOffset + 2),
            ),
        )
    }

    fun decide(query: DnsQuery, blockedDomains: Set<DomainName>): DnsBlockDecision =
        if (blockedDomains.any { blockedDomain -> DomainNamePolicy.matches(blockedDomain, query.queryName) }) {
            DnsBlockDecision.Block
        } else {
            DnsBlockDecision.Allow
        }

    fun buildNxDomainResponse(query: DnsQuery): ByteArray {
        val question = encodeName(query.queryName) + byteArrayOf(
            (query.qtype ushr 8).toByte(),
            query.qtype.toByte(),
            (query.qclass ushr 8).toByte(),
            query.qclass.toByte(),
        )
        val responseFlags = QR_MASK or
            (query.flags and OPCODE_MASK) or
            (query.flags and RD_MASK) or
            RA_MASK or
            (query.flags and CD_MASK) or
            RCODE_NXDOMAIN

        return byteArrayOf(
            (query.transactionId ushr 8).toByte(),
            query.transactionId.toByte(),
            (responseFlags ushr 8).toByte(),
            responseFlags.toByte(),
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

    private fun decodeName(packet: ByteArray, startOffset: Int): NameDecodeResult {
        val labels = mutableListOf<String>()
        val visitedOffsets = mutableSetOf<Int>()
        var offset = startOffset
        var nextOffset: Int? = null
        var pointerHops = 0

        while (true) {
            if (offset >= packet.size) {
                return NameDecodeResult.Failure(DnsQueryFailureReason.TruncatedQuestion)
            }
            if (!visitedOffsets.add(offset)) {
                return NameDecodeResult.Failure(DnsQueryFailureReason.PointerLoop)
            }

            val lengthByte = packet[offset].unsigned()
            when (lengthByte and 0xC0) {
                0x00 -> {
                    offset += 1
                    if (lengthByte == 0) {
                        val name = labels.joinToString(".").lowercase(Locale.US)
                        if (name.length > MAX_NAME_LENGTH) {
                            return NameDecodeResult.Failure(DnsQueryFailureReason.NameTooLong)
                        }
                        return NameDecodeResult.Decoded(
                            name = name,
                            nextOffset = nextOffset ?: offset,
                        )
                    }
                    if (offset + lengthByte > packet.size) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.TruncatedQuestion)
                    }

                    val labelBytes = packet.copyOfRange(offset, offset + lengthByte)
                    if (labelBytes.any { it == 0.toByte() }) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.InvalidEmptyLabel)
                    }
                    if (labelBytes.any { it.unsigned() > 0x7F }) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.NonAsciiLabel)
                    }
                    val label = labelBytes.toString(Charsets.US_ASCII)
                    if (!label.isValidHostnameLabel()) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.InvalidLabelCharacter)
                    }
                    labels += label
                    val currentLength = labels.sumOf { it.length } + labels.size - 1
                    if (currentLength > MAX_NAME_LENGTH) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.NameTooLong)
                    }
                    offset += lengthByte
                }
                0xC0 -> {
                    if (offset + 1 >= packet.size) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.TruncatedQuestion)
                    }
                    if (nextOffset == null) {
                        nextOffset = offset + 2
                    }
                    pointerHops += 1
                    if (pointerHops > MAX_POINTER_HOPS) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.ExcessivePointerHops)
                    }

                    val pointerOffset = ((lengthByte and 0x3F) shl 8) or packet[offset + 1].unsigned()
                    if (pointerOffset >= packet.size) {
                        return NameDecodeResult.Failure(DnsQueryFailureReason.PointerOutOfBounds)
                    }
                    offset = pointerOffset
                }
                else -> {
                    return NameDecodeResult.Failure(DnsQueryFailureReason.ReservedLabelEncoding)
                }
            }
        }
    }

    private fun encodeName(name: String): ByteArray {
        if (name.isEmpty()) {
            return byteArrayOf(0)
        }
        val labels = name.split(".")
        val encoded = ArrayList<Byte>(name.length + 2)
        labels.forEach { label ->
            encoded += label.length.toByte()
            label.forEach { char -> encoded += char.code.toByte() }
        }
        encoded += 0
        return encoded.toByteArray()
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun String.isValidHostnameLabel(): Boolean =
        all { char -> char.isAsciiLetterOrDigit() || char == '-' } &&
            first() != '-' &&
            last() != '-'

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private sealed interface NameDecodeResult {
        data class Decoded(
            val name: String,
            val nextOffset: Int,
        ) : NameDecodeResult

        data class Failure(
            val reason: DnsQueryFailureReason,
        ) : NameDecodeResult
    }
}
