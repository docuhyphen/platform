package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClientIpResolverTest
{
    private val configurationService = mock<ConfigurationService>()

    private fun resolver(
        trustedProxies: List<String> = listOf("10.0.0.0/8", "127.0.0.1/32", "::1/128"),
        forwardedHeadersEnabled: Boolean = true,
    ): ClientIpResolver
    {
        whenever(configurationService.getTrustedProxyCidrs()).thenReturn(trustedProxies)
        whenever(configurationService.isForwardedHeadersEnabled()).thenReturn(forwardedHeadersEnabled)
        return ClientIpResolver(configurationService)
    }

    @Test
    fun `a direct caller cannot spoof its address with a forwarded header`()
    {
        val resolved = resolver().resolve(
            peerAddress = "203.0.113.9",
            forwardedForHeader = "1.2.3.4",
            realIpHeader = "5.6.7.8",
        )

        assertEquals("203.0.113.9", resolved)
    }

    @Test
    fun `a forwarded header from a trusted proxy is honoured`()
    {
        val resolved = resolver().resolve(
            peerAddress = "10.1.2.3",
            forwardedForHeader = "198.51.100.7",
            realIpHeader = null,
        )

        assertEquals("198.51.100.7", resolved)
    }

    @Test
    fun `prepended untrusted hops are ignored in favour of the right-most untrusted hop`()
    {
        val resolved = resolver().resolve(
            peerAddress = "10.1.2.3",
            forwardedForHeader = "1.1.1.1, 198.51.100.7, 10.9.9.9",
            realIpHeader = null,
        )

        assertEquals("198.51.100.7", resolved)
    }

    @Test
    fun `a chain of only trusted proxies falls back to the peer`()
    {
        val resolved = resolver().resolve(
            peerAddress = "10.1.2.3",
            forwardedForHeader = "10.4.4.4, 10.9.9.9",
            realIpHeader = null,
        )

        assertEquals("10.1.2.3", resolved)
    }

    @Test
    fun `a hostname in a forwarded header is discarded rather than resolved`()
    {
        val resolved = resolver().resolve(
            peerAddress = "10.1.2.3",
            forwardedForHeader = "attacker.example.com",
            realIpHeader = null,
        )

        assertEquals("10.1.2.3", resolved)
    }

    @Test
    fun `forwarded headers are ignored entirely when the feature is disabled`()
    {
        val resolved = resolver(forwardedHeadersEnabled = false).resolve(
            peerAddress = "10.1.2.3",
            forwardedForHeader = "198.51.100.7",
            realIpHeader = null,
        )

        assertEquals("10.1.2.3", resolved)
    }

    @Test
    fun `a port suffix is stripped from the peer address`()
    {
        val resolved = resolver().resolve(
            peerAddress = "203.0.113.9:51234",
            forwardedForHeader = null,
            realIpHeader = null,
        )

        assertEquals("203.0.113.9", resolved)
    }

    @Test
    fun `an unknown peer address degrades to a placeholder rather than a client value`()
    {
        val resolved = resolver().resolve(
            peerAddress = null,
            forwardedForHeader = "198.51.100.7",
            realIpHeader = null,
        )

        assertEquals(ClientIpResolver.UNKNOWN_IP, resolved)
    }

    @Test
    fun `an IPv6 loopback peer is recognised as a trusted proxy`()
    {
        val resolved = resolver().resolve(
            peerAddress = "::1",
            forwardedForHeader = "198.51.100.7",
            realIpHeader = null,
        )

        assertEquals("198.51.100.7", resolved)
    }
}

