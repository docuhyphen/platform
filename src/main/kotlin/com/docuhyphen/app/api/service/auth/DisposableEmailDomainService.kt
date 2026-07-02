package com.docuhyphen.app.api.service.auth

import io.quarkus.scheduler.Scheduled
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class DisposableEmailDomainService(
    @ConfigProperty(name = "app.auth.signup.disposable-email.enabled", defaultValue = "true")
    private val enabled: Boolean,
    @ConfigProperty(
        name = "app.auth.signup.disposable-email.list-url",
        defaultValue = "https://disposable.github.io/disposable-email-domains/domains.txt"
    )
    private val listUrl: String,
    @ConfigProperty(name = "app.auth.signup.disposable-email.connect-timeout-seconds", defaultValue = "5")
    private val connectTimeoutSeconds: Long,
    @ConfigProperty(name = "app.auth.signup.disposable-email.request-timeout-seconds", defaultValue = "10")
    private val requestTimeoutSeconds: Long,
)
{
    companion object
    {
        private const val MAX_LIST_BYTES = 5 * 1024 * 1024
        private val DOMAIN_PATTERN = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")
        private val logger = LoggerFactory.getLogger(DisposableEmailDomainService::class.java)

        internal fun parseDomainList(contents: String): Set<String> = contents
            .lineSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() && !it.startsWith("#") && DOMAIN_PATTERN.matches(it) }
            .toSet()

        internal fun isDisposableEmail(email: String, domains: Set<String>): Boolean
        {
            val domain = email.substringAfterLast('@', "").lowercase(Locale.ROOT)
            if (domain.isEmpty()) return false

            return generateSequence(domain) { current ->
                current.substringAfter('.', "").takeIf { it.isNotEmpty() }
            }.any(domains::contains)
        }
    }

    private val domains = AtomicReference<Set<String>>(emptySet())
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    @PostConstruct
    fun initialize()
    {
        refreshDomainList()
    }

    @Scheduled(every = "\${app.auth.signup.disposable-email.refresh-every:24h}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun refreshDomainList()
    {
        if (!enabled) return

        try
        {
            val request = HttpRequest.newBuilder(URI.create(listUrl))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Accept", "text/plain")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            check(response.statusCode() in 200..299) { "Domain list returned HTTP ${response.statusCode()}" }
            val responseBytes = response.body().use { it.readNBytes(MAX_LIST_BYTES + 1) }
            check(responseBytes.size <= MAX_LIST_BYTES) { "Domain list exceeds the maximum supported size" }

            val refreshedDomains = parseDomainList(responseBytes.toString(Charsets.UTF_8))
            check(refreshedDomains.isNotEmpty()) { "Domain list did not contain any valid domains" }

            domains.set(refreshedDomains)
            logger.info("Loaded {} disposable email domains", refreshedDomains.size)
        }
        catch (exception: Exception)
        {
            logger.warn("Could not refresh the disposable email domain list; retaining the previous list", exception)
        }
    }

    fun isDisposable(email: String): Boolean = enabled && isDisposableEmail(email, domains.get())
}
