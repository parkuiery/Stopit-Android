package com.uiery.keep.websiteblocking

import java.io.IOException

internal class DnsUpstreamEndpointPool<Address, Endpoint : AutoCloseable>(
    private val addresses: List<Address>,
    private val endpointFactory: (Address) -> Endpoint?,
    private val exchange: (Endpoint, ByteArray) -> ByteArray,
    private val onFailure: (Address, IOException) -> Unit = { _, _ -> },
    private val failureCooldownMillis: Long = DEFAULT_FAILURE_COOLDOWN_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : AutoCloseable {
    private val endpoints = mutableMapOf<Address, Endpoint>()
    private val cooldownUntilMillis = mutableMapOf<Address, Long>()
    private var preferredAddress: Address? = null

    fun exchange(payload: ByteArray): ByteArray? {
        candidateAddresses().forEach { address ->
            val endpoint = endpoints[address] ?: endpointFactory(address)?.also {
                endpoints[address] = it
            } ?: return@forEach

            try {
                return exchange(endpoint, payload).also {
                    preferredAddress = address
                    cooldownUntilMillis.remove(address)
                }
            } catch (error: IOException) {
                endpoints.remove(address)
                endpoint.close()
                cooldownUntilMillis[address] = nowMillis() + failureCooldownMillis
                onFailure(address, error)
            }
        }
        return null
    }

    private fun candidateAddresses(): List<Address> {
        val now = nowMillis()
        val readyAddresses = addresses.filter { address ->
            cooldownUntilMillis.getOrDefault(address, Long.MIN_VALUE) <= now
        }
        val coolingAddresses = addresses
            .filterNot(readyAddresses::contains)
            .sortedBy { address ->
                cooldownUntilMillis.getOrDefault(address, Long.MIN_VALUE)
            }
        val preferred = preferredAddress
        val prioritizedReadyAddresses = if (preferred != null && preferred in readyAddresses) {
            listOf(preferred) + readyAddresses.filterNot { it == preferred }
        } else {
            readyAddresses
        }
        return prioritizedReadyAddresses + coolingAddresses
    }

    override fun close() {
        endpoints.values.forEach(AutoCloseable::close)
        endpoints.clear()
    }

    private companion object {
        const val DEFAULT_FAILURE_COOLDOWN_MILLIS = 30_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
