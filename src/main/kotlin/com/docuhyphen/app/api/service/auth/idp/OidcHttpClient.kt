package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@ApplicationScoped
class OidcHttpClient @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(configurationService.getOidcHttpConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }

    private val requestTimeout: Duration
        get() = Duration.ofSeconds(configurationService.getOidcHttpRequestTimeoutSeconds())

    fun postForm(url: String, formBody: String): HttpResponse<String>
    {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun getJson(url: String): HttpResponse<String>
    {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(requestTimeout)
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

