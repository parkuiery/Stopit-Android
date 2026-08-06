package com.uiery.keep.websiteblocking

import com.uiery.keep.domain.websiteblocking.DnsTunIpVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsVpnUpstreamResponsePolicyTest {
    @Test
    fun receiveBufferAllowsOversizeSentinelWithoutTruncatingDecisionBoundary() {
        assertEquals(1_473, DnsVpnUpstreamResponsePolicy.receiveBufferSize(DnsTunIpVersion.IPv4))
        assertEquals(1_453, DnsVpnUpstreamResponsePolicy.receiveBufferSize(DnsTunIpVersion.IPv6))
    }

    @Test
    fun maxDnsPayloadKeepsTunPacketWithinMtu() {
        assertEquals(1_472, DnsVpnUpstreamResponsePolicy.maxDnsPayloadSize(DnsTunIpVersion.IPv4))
        assertEquals(1_452, DnsVpnUpstreamResponsePolicy.maxDnsPayloadSize(DnsTunIpVersion.IPv6))
    }
}
