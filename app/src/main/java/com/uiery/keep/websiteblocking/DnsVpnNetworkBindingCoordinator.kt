package com.uiery.keep.websiteblocking

internal class DnsVpnNetworkBindingCoordinator<Request : Any, Network : Any, DnsServer : Any> {
    private var request: Request? = null
    private var bestUpstream: DnsVpnUnderlyingNetwork<Network, DnsServer>? = null
    private var binding: DnsVpnNetworkBinding<Request, Network, DnsServer>? = null

    @Synchronized
    fun updateRequest(
        request: Request,
    ): DnsVpnNetworkBindingCommand<Request, Network, DnsServer>? {
        this.request = request
        return bindIfChanged(request, bestUpstream)
    }

    @Synchronized
    fun updateUpstream(
        network: Network,
        dnsServers: List<DnsServer>,
    ): DnsVpnNetworkBindingCommand<Request, Network, DnsServer>? {
        val upstream = DnsVpnUnderlyingNetwork(network, dnsServers)
        bestUpstream = upstream
        return bindIfChanged(request, upstream)
    }

    @Synchronized
    fun loseUpstream(
        network: Network,
    ): DnsVpnNetworkBindingCommand<Request, Network, DnsServer>? {
        if (bestUpstream?.network == network) {
            bestUpstream = null
        }
        if (binding?.upstream?.network != network) return null
        binding = null
        return DnsVpnNetworkBindingCommand.Suspend(network)
    }

    @Synchronized
    fun retryBinding(
        network: Network,
    ): DnsVpnNetworkBindingCommand<Request, Network, DnsServer>? {
        if (bestUpstream?.network != network || binding?.upstream?.network != network) return null
        binding = null
        return bindIfChanged(request, bestUpstream)
    }

    @Synchronized
    fun stop() {
        request = null
        bestUpstream = null
        binding = null
    }

    private fun bindIfChanged(
        request: Request?,
        upstream: DnsVpnUnderlyingNetwork<Network, DnsServer>?,
    ): DnsVpnNetworkBindingCommand<Request, Network, DnsServer>? {
        val activeRequest = request ?: return null
        val activeUpstream = upstream ?: return null
        if (activeUpstream.dnsServers.isEmpty()) return null
        val nextBinding = DnsVpnNetworkBinding(activeRequest, activeUpstream)
        if (binding == nextBinding) return null
        binding = nextBinding
        return DnsVpnNetworkBindingCommand.Bind(activeRequest, activeUpstream)
    }
}

internal data class DnsVpnUnderlyingNetwork<Network, DnsServer>(
    val network: Network,
    val dnsServers: List<DnsServer>,
)

private data class DnsVpnNetworkBinding<Request, Network, DnsServer>(
    val request: Request,
    val upstream: DnsVpnUnderlyingNetwork<Network, DnsServer>,
)

internal sealed interface DnsVpnNetworkBindingCommand<out Request, out Network, out DnsServer> {
    data class Bind<Request, Network, DnsServer>(
        val request: Request,
        val upstream: DnsVpnUnderlyingNetwork<Network, DnsServer>,
    ) : DnsVpnNetworkBindingCommand<Request, Network, DnsServer>

    data class Suspend<Network>(
        val network: Network,
    ) : DnsVpnNetworkBindingCommand<Nothing, Network, Nothing>
}
