package com.uiery.keep.websiteblocking

import android.net.Network
import java.net.DatagramSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class DnsVpnUpstreamSocketTest {
    @Test
    fun protectedSocketIsBoundToTheCapturedUnderlyingNetwork() {
        val network = mock(Network::class.java)
        val socket = mock(DatagramSocket::class.java)

        val prepared = prepareUpstreamSocket(socket, network) { true }

        assertTrue(prepared)
        verify(network).bindSocket(socket)
    }

    @Test
    fun socketIsNotBoundWhenVpnProtectionFails() {
        val network = mock(Network::class.java)
        val socket = mock(DatagramSocket::class.java)

        val prepared = prepareUpstreamSocket(socket, network) { false }

        assertFalse(prepared)
        verify(network, never()).bindSocket(socket)
    }
}
