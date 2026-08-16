package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.model.entity.ApplicationType
import com.docuhyphen.app.api.repository.application.ApplicationRepository
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ApplicationService @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val authenticationService: AuthenticationService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationService::class.java)
    }

    /**
     * Authenticates a registered application by API key and secret, then issues a JWT.
     * Returns the access token string on success, or null if credentials are invalid or
     * the application is inactive.
     */
    @Transactional
    fun authenticateAndIssueToken(apiKey: String, apiSecret: String): String?
    {
        val application = applicationRepository.findByApiKey(apiKey)
        if (application == null)
        {
            logger.warn("Application token request: no application found for the supplied API key")
            return null
        }
        if (!application.isActive)
        {
            logger.warn("Application token request: application {} is inactive", application.id)
            return null
        }
        if (!BCrypt.checkpw(apiSecret, application.apiSecretHash))
        {
            logger.warn("Application token request: invalid secret for application {}", application.id)
            return null
        }

        application.lastAccessDate = Timestamp.from(Instant.now())
        applicationRepository.update(application)

        return authenticationService.generateApplicationAccessToken(
            applicationId = application.id,
            scopes = resolveScopes(application),
        )
    }

    fun findActive(id: UUID): Application? = applicationRepository.findActiveById(id)

    fun hashSecret(rawSecret: String): String = BCrypt.hashpw(rawSecret, BCrypt.gensalt())

    private fun resolveScopes(application: Application): Set<String>
    {
        val defaultScopes = configurationService.getApplicationTokenDefaultScopes()
        return when (application.applicationType)
        {
            ApplicationType.INTEGRATION -> defaultScopes + setOf("application:integration")
            ApplicationType.SERVICE -> defaultScopes + setOf("application:service")
            else -> defaultScopes
        }
    }
}
