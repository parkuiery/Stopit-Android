package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.DnsBlockDecision
import com.uiery.keep.domain.websiteblocking.DnsMessageCodec
import com.uiery.keep.domain.websiteblocking.DnsQueryParseResult
import com.uiery.keep.domain.websiteblocking.DnsTunPacketBuildResult
import com.uiery.keep.domain.websiteblocking.DnsTunPacketCodec
import com.uiery.keep.domain.websiteblocking.DnsTunIpVersion
import com.uiery.keep.domain.websiteblocking.DnsTunPacketParseResult
import com.uiery.keep.domain.websiteblocking.DomainName
import java.util.Locale

class DnsVpnDatagramProcessor(
    private val blockedDomains: Set<DomainName>,
    private val upstreamExchange: (ByteArray) -> ByteArray?,
) {
    fun process(packet: ByteArray): DnsVpnDatagramProcessResult =
        try {
            when (val tunResult = DnsTunPacketCodec.parseDnsUdpDatagram(packet.copyOf())) {
                is DnsTunPacketParseResult.Failure -> DnsVpnDatagramProcessResult.FailOpenStopVpn(
                    DnsVpnDatagramProcessStopReason.TunPacketRejected,
                )
                is DnsTunPacketParseResult.Parsed -> {
                    val request = tunResult.datagram
                    val dnsPayload = request.dnsPayload.copyOf()
                    val responsePayload = when (val dnsResult = DnsMessageCodec.parseQuery(dnsPayload)) {
                        is DnsQueryParseResult.Parsed -> {
                            when (DnsMessageCodec.decide(dnsResult.query, blockedDomains)) {
                                DnsBlockDecision.Block -> DnsMessageCodec.buildNxDomainResponse(dnsResult.query)
                                DnsBlockDecision.Allow -> {
                                    val upstreamResponse = upstreamExchange(dnsPayload.copyOf())
                                        ?: return DnsVpnDatagramProcessResult.FailOpenStopVpn(
                                            DnsVpnDatagramProcessStopReason.UpstreamUnavailable,
                                        )
                                    if (!DnsVpnUpstreamResponsePolicy.isValidResponseForQuery(
                                            requestPayload = dnsPayload,
                                            responsePayload = upstreamResponse,
                                            ipVersion = request.ipVersion,
                                        )
                                    ) {
                                        return DnsVpnDatagramProcessResult.FailOpenStopVpn(
                                            DnsVpnDatagramProcessStopReason.UpstreamRejected,
                                        )
                                    }
                                    upstreamResponse
                                }
                            }
                        }
                        is DnsQueryParseResult.Failure -> return DnsVpnDatagramProcessResult.FailOpenStopVpn(
                            DnsVpnDatagramProcessStopReason.UpstreamRejected,
                        )
                    } ?: return DnsVpnDatagramProcessResult.FailOpenStopVpn(
                        DnsVpnDatagramProcessStopReason.UpstreamUnavailable,
                    )

                    when (val buildResult = DnsTunPacketCodec.buildDnsUdpResponse(request, responsePayload.copyOf())) {
                        is DnsTunPacketBuildResult.Success -> DnsVpnDatagramProcessResult.SendToTun(
                            buildResult.packet.copyOf(),
                        )
                        is DnsTunPacketBuildResult.Failure -> DnsVpnDatagramProcessResult.FailOpenStopVpn(
                            DnsVpnDatagramProcessStopReason.ResponseBuildFailed,
                        )
                    }
                }
            }
        } catch (_: RuntimeException) {
            DnsVpnDatagramProcessResult.FailOpenStopVpn(DnsVpnDatagramProcessStopReason.ProcessorFailed)
        }
}

sealed interface DnsVpnDatagramProcessResult {
    data class SendToTun(val packet: ByteArray) : DnsVpnDatagramProcessResult

    data class FailOpenStopVpn(
        val reason: DnsVpnDatagramProcessStopReason,
    ) : DnsVpnDatagramProcessResult
}

enum class DnsVpnDatagramProcessStopReason {
    TunPacketRejected,
    UpstreamUnavailable,
    UpstreamRejected,
    ResponseBuildFailed,
    ProcessorFailed,
}

object DnsVpnUpstreamResponsePolicy {
    private const val TUN_MTU = 1_500
    private const val IPV4_HEADER_LENGTH = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val UDP_HEADER_LENGTH = 8
    private const val DNS_HEADER_LENGTH = 12
    private const val QR_MASK = 0x8000
    private const val EXPECTED_QUESTION_COUNT = 1
    private const val MAX_POINTER_HOPS = 16

    fun maxDnsPayloadSize(ipVersion: DnsTunIpVersion): Int =
        when (ipVersion) {
            DnsTunIpVersion.IPv4 -> TUN_MTU - IPV4_HEADER_LENGTH - UDP_HEADER_LENGTH
            DnsTunIpVersion.IPv6 -> TUN_MTU - IPV6_HEADER_LENGTH - UDP_HEADER_LENGTH
            DnsTunIpVersion.Unsupported -> 0
        }

    fun receiveBufferSize(ipVersion: DnsTunIpVersion): Int =
        maxDnsPayloadSize(ipVersion) + 1

    fun isValidResponseForQuery(
        requestPayload: ByteArray,
        responsePayload: ByteArray,
        ipVersion: DnsTunIpVersion,
    ): Boolean {
        if (responsePayload.size > maxDnsPayloadSize(ipVersion)) return false

        val requestQuestion = parseQuestion(requestPayload, expectResponse = false) ?: return false
        val responseQuestion = parseQuestion(responsePayload, expectResponse = true) ?: return false

        return requestQuestion == responseQuestion
    }

    private fun parseQuestion(packet: ByteArray, expectResponse: Boolean): DnsQuestionFingerprint? {
        if (packet.size < DNS_HEADER_LENGTH) return null
        val flags = packet.readUnsignedShort(2)
        val isResponse = (flags and QR_MASK) != 0
        if (isResponse != expectResponse) return null
        if (packet.readUnsignedShort(4) != EXPECTED_QUESTION_COUNT) return null

        val name = decodeName(packet, DNS_HEADER_LENGTH) ?: return null
        val qtypeOffset = name.nextOffset
        if (qtypeOffset + 4 > packet.size) return null

        return DnsQuestionFingerprint(
            transactionId = packet.readUnsignedShort(0),
            queryName = name.value,
            qtype = packet.readUnsignedShort(qtypeOffset),
            qclass = packet.readUnsignedShort(qtypeOffset + 2),
        )
    }

    private fun decodeName(packet: ByteArray, startOffset: Int): DecodedName? {
        val labels = mutableListOf<String>()
        val visitedOffsets = mutableSetOf<Int>()
        var offset = startOffset
        var nextOffset: Int? = null
        var pointerHops = 0

        while (true) {
            if (offset >= packet.size || !visitedOffsets.add(offset)) return null

            val lengthByte = packet[offset].unsigned()
            when (lengthByte and 0xC0) {
                0x00 -> {
                    offset += 1
                    if (lengthByte == 0) {
                        return DecodedName(
                            value = labels.joinToString(".").lowercase(Locale.US),
                            nextOffset = nextOffset ?: offset,
                        )
                    }
                    if (offset + lengthByte > packet.size) return null
                    val labelBytes = packet.copyOfRange(offset, offset + lengthByte)
                    if (labelBytes.any { it.unsigned() > 0x7F }) return null
                    labels += labelBytes.toString(Charsets.US_ASCII)
                    offset += lengthByte
                }
                0xC0 -> {
                    if (offset + 1 >= packet.size) return null
                    if (nextOffset == null) nextOffset = offset + 2
                    pointerHops += 1
                    if (pointerHops > MAX_POINTER_HOPS) return null
                    val pointerOffset = ((lengthByte and 0x3F) shl 8) or packet[offset + 1].unsigned()
                    if (pointerOffset >= packet.size) return null
                    offset = pointerOffset
                }
                else -> return null
            }
        }
    }

    private data class DecodedName(
        val value: String,
        val nextOffset: Int,
    )

    private data class DnsQuestionFingerprint(
        val transactionId: Int,
        val queryName: String,
        val qtype: Int,
        val qclass: Int,
    )

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
