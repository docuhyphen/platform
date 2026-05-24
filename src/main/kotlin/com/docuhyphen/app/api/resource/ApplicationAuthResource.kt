package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.model.entity.ApplicationType
import com.docuhyphen.app.api.resource.model.ApplicationTokenRequest
import com.docuhyphen.app.api.resource.model.ApplicationTokenResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

typealias ApplicationEntity = Application

@Path("/auth/application")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ApplicationAuthResource @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val configurationService: ConfigurationService,
    private val entityManager: EntityManager,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationAuthResource::class.java)
    }

    @POST
    @Path("/token")
    fun getApplicationToken(
        payload: ApplicationTokenRequest,
    ): Response
    {
        return try
        {
            if (payload.apiKey.isNullOrBlank() || payload.apiSecret.isNullOrBlank())
            {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("API key and secret are required"))
                    .build()
            }

            //ToDo: use ApplicationService
            val application = entityManager.createQuery(
                "SELECT a FROM Application a WHERE a.apiKey = :apiKey AND a.isActive = true",
                ApplicationEntity::class.java
            )
                .setParameter("apiKey", payload.apiKey)
                .resultList
                .firstOrNull()

            if (application == null)
            {
                logger.warn("Application not found for apiKey: ${payload.apiKey}")
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid API credentials"))
                    .build()
            }

            if (application.apiSecret != payload.apiSecret)
            {
                logger.warn("Invalid API secret for application: ${application.name}")
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("Invalid API credentials"))
                    .build()
            }

            // Update last access date
            application.lastAccessDate = Timestamp.from(Instant.now())
            entityManager.merge(application)

            val accessToken = authenticationService.generateApplicationAccessToken(
                applicationId = application.id,
                scopes = resolveScopesForApplication(application),
            )

            Response.ok(ApplicationTokenResponse(accessToken)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error generating application token", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to generate application token"))
                .build()
        }
    }

    private fun resolveScopesForApplication(application: Application): Set<String>
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

