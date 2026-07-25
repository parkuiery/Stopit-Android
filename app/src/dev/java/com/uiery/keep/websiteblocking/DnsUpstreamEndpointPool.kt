package com.uiery.keep.websiteblocking

import java.io.IOException

internal class DnsUpstreamEndpointPool<Address, Endpoint : AutoCloseable>(
    private val addresses: List<Address>,
    private val endpointFactory: (Address) -> Endpoint?,
    private val exchange: (Endpoint, ByteArray) -> ByteArray,
    private val onFailure: (Address, IOException) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val endpoints = mutableMapOf<Address, Endpoint>()

    fun exchange(payload: ByteArray): ByteArray? {
        addresses.forEach { address ->
            val endpoint = endpoints[address] ?: endpointFactory(address)?.also {
                endpoints[address] = it
            } ?: return@forEach

            try {
                return exchange(endpoint, payload)
            } catch (error: IOException) {
                endpoints.remove(address)
                endpoint.close()
                onFailure(address, error)
            }
        }
        return null
    }

    override fun close() {
        endpoints.values.forEach(AutoCloseable::close)
        endpoints.clear()
    }
}
