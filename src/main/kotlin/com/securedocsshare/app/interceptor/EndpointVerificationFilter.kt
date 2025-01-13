package com.securedocsshare.app.interceptor

import com.securedocsshare.app.api.model.*
import com.securedocsshare.app.service.AppUserService
import com.securedocsshare.app.service.AuthenticationService
import com.securedocsshare.app.service.ConfigurationService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

@RequestScoped
class AuthTokenContext
{
    private lateinit var _authToken: AuthToken

    var authToken: AuthToken
        get()
        {
            if (!::_authToken.isInitialized)
            {
                throw IllegalStateException("AuthToken is not initialized")
            }
            return _authToken
        }
        set(value)
        {
            _authToken = value
        }
}

class AuthTokenProducer
{
    @Inject
    private lateinit var authTokenContext: AuthTokenContext

    @Produces
    @RequestScoped
    fun produceAppUser(): AuthToken
    {
        return authTokenContext.authToken
    }
}


@Provider
class EndpointVerificationFilter @Inject constructor(
    val configurationService: ConfigurationService,
    val appUserService: AppUserService,
    val authenticationService: AuthenticationService,
) : ContainerRequestFilter
{
    private val logger = LoggerFactory.getLogger(EndpointVerificationFilter::class.java.name)

    private val excludedEndpoints = listOf(
        "/auth/sign-up/initiation",
        "/auth/sign-up/completion",
        "/auth/sign-up/otp-regeneration",
        "/auth/sign-in/initiate",
        "/auth/sign-in/completion"
    )

    @Inject
    private lateinit var authenticationContext: AuthTokenContext

    override fun filter(requestContext: ContainerRequestContext)
    {
        val requestUri = requestContext.uriInfo.path
        logger.info("Intercepted request to URI: $requestUri")

        if (excludedEndpoints.any { requestUri.contains(it) })
        {
            logger.info("Request to $requestUri is excluded from verification.")
            return
        }

        val authorizationHeader = requestContext.headers.getFirst("Authorization")
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer "))
        {
            logger.warn("Missing or invalid Authorization header.")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val token = authorizationHeader.removePrefix("Bearer ").trim()
        val authToken = authenticationService.authenticateToken(token)
        if (authToken == null)
        {
            logger.warn("Invalid token for request to $requestUri.")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        authenticationContext.authToken = authToken
        logger.info("Successfully authenticated")
    }

    private fun abortRequest(requestContext: ContainerRequestContext, message: String)
    {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(message)
                .build()
        )
    }
}