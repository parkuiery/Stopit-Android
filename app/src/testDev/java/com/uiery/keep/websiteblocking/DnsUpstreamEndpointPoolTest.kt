package com.uiery.keep.websiteblocking

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUpstreamEndpointPoolTest {
    @Test
    fun successfulEndpointIsReusedAcrossQueries() {
        val createdEndpoints = mutableListOf<FakeEndpoint>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary"),
            endpointFactory = { address ->
                FakeEndpoint(address).also(createdEndpoints::add)
            },
            exchange = { _, payload -> payload + 1 },
        )

        assertArrayEquals(byteArrayOf(7, 1), pool.exchange(byteArrayOf(7)))
        assertArrayEquals(byteArrayOf(8, 1), pool.exchange(byteArrayOf(8)))
        assertEquals(1, createdEndpoints.size)
    }

    @Test
    fun failedEndpointIsClosedBeforeFallingBack() {
        val createdEndpoints = mutableListOf<FakeEndpoint>()
        val failures = mutableListOf<String>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = { address ->
                FakeEndpoint(address).also(createdEndpoints::add)
            },
            exchange = { endpoint, payload ->
                if (endpoint.address == "primary") throw IOException("timeout")
                payload + 2
            },
            onFailure = { address, _ -> failures += address },
        )

        assertArrayEquals(byteArrayOf(9, 2), pool.exchange(byteArrayOf(9)))
        assertTrue(createdEndpoints.single { it.address == "primary" }.closed)
        assertEquals(listOf("primary"), failures)
    }

    @Test
    fun failedEndpointRemainsInCooldownWhileFallbackIsHealthy() {
        val primaryEndpoints = mutableListOf<FakeEndpoint>()
        val exchangeOrder = mutableListOf<String>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = { address ->
                FakeEndpoint(address).also {
                    if (address == "primary") primaryEndpoints += it
                }
            },
            exchange = { endpoint, payload ->
                exchangeOrder += endpoint.address
                if (endpoint.address == "primary") {
                    throw IOException("timeout")
                }
                payload
            },
            failureCooldownMillis = 30_000,
            nowMillis = { 1_000 },
        )

        assertArrayEquals(byteArrayOf(1), pool.exchange(byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(2), pool.exchange(byteArrayOf(2)))
        assertEquals(listOf("primary", "secondary", "secondary"), exchangeOrder)
        assertEquals(1, primaryEndpoints.size)
        assertTrue(primaryEndpoints.first().closed)
    }

    @Test
    fun coolingEndpointIsSkippedWhenPreferredFallbackFails() {
        val exchangeOrder = mutableListOf<String>()
        var secondaryAttempts = 0
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary", "tertiary"),
            endpointFactory = ::FakeEndpoint,
            exchange = { endpoint, payload ->
                exchangeOrder += endpoint.address
                when {
                    endpoint.address == "primary" -> throw IOException("primary timeout")
                    endpoint.address == "secondary" && secondaryAttempts++ > 0 ->
                        throw IOException("secondary timeout")
                    else -> payload
                }
            },
            failureCooldownMillis = 30_000,
            nowMillis = { 1_000 },
        )

        assertArrayEquals(byteArrayOf(1), pool.exchange(byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(2), pool.exchange(byteArrayOf(2)))

        assertEquals(
            listOf("primary", "secondary", "secondary", "tertiary"),
            exchangeOrder,
        )
    }

    @Test
    fun cooledEndpointCanBeRetriedAfterCooldownExpires() {
        var nowMillis = 1_000L
        var primaryAttempts = 0
        var secondaryAttempts = 0
        val exchangeOrder = mutableListOf<String>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = ::FakeEndpoint,
            exchange = { endpoint, payload ->
                exchangeOrder += endpoint.address
                when {
                    endpoint.address == "primary" && primaryAttempts++ == 0 ->
                        throw IOException("primary timeout")
                    endpoint.address == "secondary" && secondaryAttempts++ > 0 ->
                        throw IOException("secondary timeout")
                    else -> payload
                }
            },
            failureCooldownMillis = 30_000,
            nowMillis = { nowMillis },
        )

        assertArrayEquals(byteArrayOf(1), pool.exchange(byteArrayOf(1)))
        nowMillis += 30_001
        assertArrayEquals(byteArrayOf(2), pool.exchange(byteArrayOf(2)))

        assertEquals(
            listOf("primary", "secondary", "secondary", "primary"),
            exchangeOrder,
        )
    }

    @Test
    fun coolingEndpointIsUsedAsLastResortWhenFallbackFails() {
        var primaryAttempts = 0
        var secondaryAttempts = 0
        val exchangeOrder = mutableListOf<String>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = ::FakeEndpoint,
            exchange = { endpoint, payload ->
                exchangeOrder += endpoint.address
                when {
                    endpoint.address == "primary" && primaryAttempts++ == 0 ->
                        throw IOException("primary timeout")
                    endpoint.address == "secondary" && secondaryAttempts++ > 0 ->
                        throw IOException("secondary timeout")
                    else -> payload
                }
            },
            failureCooldownMillis = 30_000,
            nowMillis = { 1_000 },
        )

        assertArrayEquals(byteArrayOf(1), pool.exchange(byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(2), pool.exchange(byteArrayOf(2)))

        assertEquals(
            listOf("primary", "secondary", "secondary", "primary"),
            exchangeOrder,
        )
    }

    @Test
    fun closeClosesEveryCachedEndpoint() {
        val createdEndpoints = mutableListOf<FakeEndpoint>()
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = { address ->
                FakeEndpoint(address).also(createdEndpoints::add)
            },
            exchange = { endpoint, payload ->
                if (endpoint.address == "primary") throw IOException("timeout")
                payload
            },
        )
        pool.exchange(byteArrayOf(1))

        pool.close()

        assertTrue(createdEndpoints.all(FakeEndpoint::closed))
    }

    private class FakeEndpoint(
        val address: String,
        var closed: Boolean = false,
    ) : AutoCloseable {
        override fun close() {
            closed = true
        }
    }
}
