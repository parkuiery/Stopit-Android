package com.uiery.keep.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsVpnNetworkBindingCoordinatorTest {
    @Test
    fun requestBindsToLatestBestUpstream() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream(
            network = "wifi",
            dnsServers = listOf("1.1.1.1"),
        )

        val command = coordinator.updateRequest("block-example")

        assertEquals(
            DnsVpnNetworkBindingCommand.Bind(
                request = "block-example",
                upstream = DnsVpnUnderlyingNetwork(
                    network = "wifi",
                    dnsServers = listOf("1.1.1.1"),
                ),
            ),
            command,
        )
    }

    @Test
    fun betterUpstreamRebindsActiveRequest() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream("cellular", listOf("8.8.8.8"))
        coordinator.updateRequest("block-example")

        val command = coordinator.updateUpstream(
            network = "wifi",
            dnsServers = listOf("1.1.1.1"),
        )

        assertEquals(
            DnsVpnNetworkBindingCommand.Bind(
                request = "block-example",
                upstream = DnsVpnUnderlyingNetwork(
                    network = "wifi",
                    dnsServers = listOf("1.1.1.1"),
                ),
            ),
            command,
        )
    }

    @Test
    fun repeatedPropertiesForBoundUpstreamDoNotRestartSession() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream("wifi", listOf("1.1.1.1"))
        coordinator.updateRequest("block-example")

        val command = coordinator.updateUpstream(
            network = "wifi",
            dnsServers = listOf("1.1.1.1"),
        )

        assertNull(command)
    }

    @Test
    fun losingBoundUpstreamSuspendsBeforeDnsTimeout() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream("wifi", listOf("1.1.1.1"))
        coordinator.updateRequest("block-example")

        val command = coordinator.loseUpstream("wifi")

        assertEquals(
            DnsVpnNetworkBindingCommand.Suspend<String>("wifi"),
            command,
        )
    }

    @Test
    fun transientFailureCanRetryCurrentBinding() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream("cellular", listOf("8.8.8.8"))
        coordinator.updateRequest("block-example")

        val command = coordinator.retryBinding("cellular")

        assertEquals(
            DnsVpnNetworkBindingCommand.Bind(
                request = "block-example",
                upstream = DnsVpnUnderlyingNetwork(
                    network = "cellular",
                    dnsServers = listOf("8.8.8.8"),
                ),
            ),
            command,
        )
    }

    @Test
    fun staleFailureCannotRetryReplacementBinding() {
        val coordinator = DnsVpnNetworkBindingCoordinator<String, String, String>()
        coordinator.updateUpstream("cellular", listOf("8.8.8.8"))
        coordinator.updateRequest("block-example")
        coordinator.updateUpstream("wifi", listOf("1.1.1.1"))

        val command = coordinator.retryBinding("cellular")

        assertNull(command)
    }
}
