package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.AuthTokenType.ACCESS
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthenticationService
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
    private val appUserService: AppUserService,
) : ContainerRequestFilter
{
    private val logger = LoggerFactory.getLogger(EndpointVerificationFilter::class.java.name)

    private val excludedEndpoints = listOf(
        "/auth/sign-up/initiation",
        "/auth/sign-up/completion",
        "/auth/sign-up/otp-regeneration",
        "/auth/sign-in/initiate",
        "/auth/sign-in/otp-regeneration",
        "/auth/sign-in/completion",
        "/auth/sign-in/lookup",
        "/auth/password-reset/initiation",
        "/auth/password-reset/completion",
        "/auth/token/refresh",
        "/auth/oauth/",
        "/auth/application/token",
        "/no-auth/sharing-sessions",
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

        val claims = authenticationService.verifyAccessToken(token)
        if (claims == null)
        {
            logger.warn("Invalid access token for request to $requestUri")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val userId = try
        {
            java.util.UUID.fromString(claims.subject)
        }
        catch (e: Exception)
        {
            logger.warn("Invalid subject in access token")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val appUser = appUserService.getById(userId)
        if (appUser == null)
        {
            logger.warn("User not found for access token subject=$userId")
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val virtualToken = AuthToken().apply {
            this.appUser = appUser
            this.token = token
            this.tokenType = ACCESS
        }

        authenticationContext.authToken = virtualToken
        logger.info("Successfully authenticated user={}", userId)
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