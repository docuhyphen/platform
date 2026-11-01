package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.model.application.WebhookDestinationValidationResult
import jakarta.enterprise.context.ApplicationScoped
import java.net.InetAddress
import java.net.URI

@ApplicationScoped
class WebhookDestinationPolicy
{
    fun validate(rawUrl: String): WebhookDestinationValidationResult
    {
        if (rawUrl.isBlank())
        {
            return WebhookDestinationValidationResult(false, "Target URL is required")
        }

        val uri = runCatching { URI(rawUrl.trim()) }.getOrElse {
            return WebhookDestinationValidationResult(false, "Target URL is not a valid URI")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http")
        {
            return WebhookDestinationValidationResult(false, "Target URL must use http or https scheme")
        }

        val host = uri.host
        if (host.isNullOrBlank())
        {
            return WebhookDestinationValidationResult(false, "Target URL must have a host")
        }

        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse {
            return WebhookDestinationValidationResult(false, "Target URL host could not be resolved: $host")
        }

        for (addr in addresses)
        {
            val reason = denyReason(addr)
            if (reason != null)
            {
                return WebhookDestinationValidationResult(false, reason)
            }
        }

        return WebhookDestinationValidationResult(true, null)
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
