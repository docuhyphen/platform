package com.docuhyphen.app.api.realtime

import jakarta.websocket.server.ServerEndpointConfig
import org.eclipse.microprofile.config.ConfigProvider

class RealtimeOriginConfigurator : ServerEndpointConfig.Configurator()
{
    override fun checkOrigin(originHeaderValue: String?): Boolean
    {
        if (originHeaderValue.isNullOrBlank()) return false
        val configuredOrigins = ConfigProvider.getConfig()
            .getOptionalValue("quarkus.http.cors.origins", String::class.java)
            .orElse("")
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        if ("*" in configuredOrigins) return true
        val origin = originHeaderValue.trimEnd('/')
        return configuredOrigins.any { it.trimEnd('/') == origin }
    }
}
