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
    fun failedEndpointIsRecreatedOnNextQuery() {
        val primaryEndpoints = mutableListOf<FakeEndpoint>()
        var primaryAttempt = 0
        val pool = DnsUpstreamEndpointPool(
            addresses = listOf("primary", "secondary"),
            endpointFactory = { address ->
                FakeEndpoint(address).also {
                    if (address == "primary") primaryEndpoints += it
                }
            },
            exchange = { endpoint, payload ->
                if (endpoint.address == "primary" && primaryAttempt++ == 0) {
                    throw IOException("timeout")
                }
                payload
            },
        )

        assertArrayEquals(byteArrayOf(1), pool.exchange(byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(2), pool.exchange(byteArrayOf(2)))
        assertEquals(2, primaryEndpoints.size)
        assertTrue(primaryEndpoints.first().closed)
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
