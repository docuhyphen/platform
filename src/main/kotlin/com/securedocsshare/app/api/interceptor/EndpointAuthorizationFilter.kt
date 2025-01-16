package com.securedocsshare.app.api.interceptor

import com.securedocsshare.app.api.model.AuthToken
import com.securedocsshare.app.api.service.AuthenticationService
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory

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
    fun produceAuthToken(): AuthToken
    {
        return authTokenContext.authToken
    }
}

@Provider
class EndpointVerificationFilter @Inject constructor(
    private val authenticationService: AuthenticationService,
) : ContainerRequestFilter
{
    private val logger = LoggerFactory.getLogger(EndpointVerificationFilter::class.java.name)

    private val excludedEndpoints = listOf(
        "/auth/sign-up/initiation",
        "/auth/sign-up/completion",
        "/auth/sign-up/otp-regeneration",
        "/auth/sign-in/initiate",
        "/auth/sign-in/completion",
        "/auth/password-reset/initiation",
        "/auth/password-reset/completion"
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
        val authToken = try
        {
            authenticationService.authenticateToken(token)
        }
        catch (e: Exception)
        {
            logger.warn("Invalid token for request to $requestUri: ${e.message}")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

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