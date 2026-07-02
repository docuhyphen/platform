package com.docuhyphen.app.api.service.application

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Webhook destination SSRF policy.
 *
 * All tests use raw IP addresses as the target host to avoid DNS lookups in CI.
 * InetAddress.getAllByName("x.x.x.x") parses the IP directly without a resolver call.
 *
 * Denied categories:
 *  - Loopback (127.x.x.x, ::1)
 *  - Link-local (169.254.x.x)
 *  - AWS EC2 metadata service (169.254.169.254) - a specific link-local address
 *  - Private / site-local (10.x, 172.16-31.x, 192.168.x.x)
 *  - Non-http(s) scheme
 *  - Blank or malformed URL
 *
 * Permitted:
 *  - Valid HTTPS URL to a routable public address
 *  - HTTP URL to a routable public address (http allowed; TLS enforcement is deployment policy)
 */
class WebhookDestinationPolicyTest
{
    private val policy = WebhookDestinationPolicy()

    // --- valid destinations ---

    @Test
    fun `valid https URL with public IP is allowed`()
    {
        val result = policy.validate("https://8.8.8.8/webhook")
        assertTrue(result.allowed) { "A valid HTTPS URL with a public IP must be allowed; reason: ${result.reason}" }
    }

    @Test
    fun `valid http URL with public IP is allowed`()
    {
        val result = policy.validate("http://8.8.8.8/webhook")
        assertTrue(result.allowed) { "A valid HTTP URL with a public IP must be allowed; reason: ${result.reason}" }
    }

    // --- loopback ---

    @Test
    fun `loopback IPv4 127_0_0_1 is denied`()
    {
        val result = policy.validate("https://127.0.0.1/webhook")
        assertFalse(result.allowed)
        assertNotNull(result.reason)
    }

    @Test
    fun `loopback IPv4 127_1_2_3 is denied`()
    {
        val result = policy.validate("https://127.1.2.3/webhook")
        assertFalse(result.allowed)
    }

    // --- link-local ---

    @Test
    fun `link-local address 169_254_1_1 is denied`()
    {
        val result = policy.validate("https://169.254.1.1/webhook")
        assertFalse(result.allowed)
    }

    @Test
    fun `AWS EC2 metadata service address 169_254_169_254 is denied`()
    {
        val result = policy.validate("https://169.254.169.254/latest/meta-data/")
        assertFalse(result.allowed)
        assertNotNull(result.reason) { "Metadata service denial must include a reason" }
    }

    // --- private / site-local ---

    @Test
    fun `private 10_x_x_x is denied`()
    {
        val result = policy.validate("https://10.0.0.1/webhook")
        assertFalse(result.allowed)
    }

    @Test
    fun `private 172_16_x_x is denied`()
    {
        val result = policy.validate("https://172.16.0.1/webhook")
        assertFalse(result.allowed)
    }

    @Test
    fun `private 192_168_x_x is denied`()
    {
        val result = policy.validate("https://192.168.1.1/webhook")
        assertFalse(result.allowed)
    }

    // --- scheme ---

    @Test
    fun `ftp scheme is denied`()
    {
        val result = policy.validate("ftp://8.8.8.8/webhook")
        assertFalse(result.allowed)
    }

    @Test
    fun `file scheme is denied`()
    {
        val result = policy.validate("file:///etc/passwd")
        assertFalse(result.allowed)
    }

    // --- blank / malformed ---

    @Test
    fun `blank URL is denied`()
    {
        val result = policy.validate("")
        assertFalse(result.allowed)
    }

    @Test
    fun `whitespace-only URL is denied`()
    {
        val result = policy.validate("   ")
        assertFalse(result.allowed)
    }

    @Test
    fun `URL without host is denied`()
    {
        val result = policy.validate("https:///path")
        assertFalse(result.allowed)
    }
}
