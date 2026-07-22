package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.DnsBlockDecision
import com.uiery.keep.domain.websiteblocking.DnsMessageCodec
import com.uiery.keep.domain.websiteblocking.DnsQueryParseResult
import com.uiery.keep.domain.websiteblocking.DnsTunPacketBuildResult
import com.uiery.keep.domain.websiteblocking.DnsTunPacketCodec
import com.uiery.keep.domain.websiteblocking.DnsTunPacketParseResult
import com.uiery.keep.domain.websiteblocking.DomainName

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
                    val responsePayload = when (val dnsResult = DnsMessageCodec.parseQuery(request.dnsPayload.copyOf())) {
                        is DnsQueryParseResult.Parsed -> {
                            when (DnsMessageCodec.decide(dnsResult.query, blockedDomains)) {
                                DnsBlockDecision.Block -> DnsMessageCodec.buildNxDomainResponse(dnsResult.query)
                                DnsBlockDecision.Allow -> upstreamExchange(request.dnsPayload.copyOf())
                            }
                        }
                        is DnsQueryParseResult.Failure -> upstreamExchange(request.dnsPayload.copyOf())
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
    ResponseBuildFailed,
    ProcessorFailed,
}
