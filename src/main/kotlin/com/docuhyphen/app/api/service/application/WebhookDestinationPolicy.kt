package com.docuhyphen.app.api.service.application

import jakarta.enterprise.context.ApplicationScoped
import java.net.InetAddress
import java.net.URI

/**
 * Validates outbound webhook target URLs against an egress policy that prevents SSRF.
 *
 * Denied destinations:
 * - Loopback: 127.0.0.0/8, ::1
 * - Link-local: 169.254.0.0/16, fe80::/10
 * - Private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7
 * - AWS EC2 metadata service: 169.254.169.254
 * - Non-http(s) schemes
 * - Blank or unparseable URLs
 *
 * DNS rebinding is mitigated by resolving the hostname at registration time. Callers
 * should also re-validate before each delivery attempt.
 */
@ApplicationScoped
class WebhookDestinationPolicy
{
    data class ValidationResult(val allowed: Boolean, val reason: String?)

    fun validate(rawUrl: String): ValidationResult
    {
        if (rawUrl.isBlank())
        {
            return ValidationResult(false, "Target URL is required")
        }

        val uri = runCatching { URI(rawUrl.trim()) }.getOrElse {
            return ValidationResult(false, "Target URL is not a valid URI")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http")
        {
            return ValidationResult(false, "Target URL must use http or https scheme")
        }

        val host = uri.host
        if (host.isNullOrBlank())
        {
            return ValidationResult(false, "Target URL must have a host")
        }

        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse {
            return ValidationResult(false, "Target URL host could not be resolved: $host")
        }

        for (addr in addresses)
        {
            val reason = denyReason(addr)
            if (reason != null)
            {
                return ValidationResult(false, reason)
            }
        }

        return ValidationResult(true, null)
    }

    private fun denyReason(addr: InetAddress): String?
    {
        if (addr.isLoopbackAddress)
        {
            return "Target URL resolves to a loopback address, which is not permitted"
        }
        if (addr.isLinkLocalAddress)
        {
            return "Target URL resolves to a link-local address, which is not permitted"
        }
        if (addr.isSiteLocalAddress)
        {
            return "Target URL resolves to a private/site-local address, which is not permitted"
        }
        if (isEc2MetadataService(addr))
        {
            return "Target URL resolves to the cloud metadata service address, which is not permitted"
        }
        return null
    }

    private fun isEc2MetadataService(addr: InetAddress): Boolean
    {
        val raw = addr.address
        return raw.size == 4 &&
            (raw[0].toInt() and 0xFF) == 169 &&
            (raw[1].toInt() and 0xFF) == 254 &&
            (raw[2].toInt() and 0xFF) == 169 &&
            (raw[3].toInt() and 0xFF) == 254
    }
}
