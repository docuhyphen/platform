package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class StepUpRequest(val password: String? = null)

@Serializable
data class StepUpResponse(val fresh: Boolean, val message: String)

/**
 * Step-up authentication endpoint. Callers re-prove identity by re-entering their password.
 * On success, the current session's `lastAuthTime` is bumped to "now"; subsequent access tokens
 * minted from this session will have a fresh `auth_time` claim until the freshness window lapses.
 */
@Path("/auth/step-up")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class StepUpResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val stepUpAuthService: StepUpAuthService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(StepUpResource::class.java)
    }

    @POST
    fun stepUp(
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
        payload: StepUpRequest,
    ): Response
    {
        val appUser = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Not authenticated."))
                .build()

        val ip = request.remoteAddress()?.host() ?: "0.0.0.0"
        if (authRateLimitService.isLimited(
                key = "auth:step-up:${appUser.id}:$ip",
                maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
            ))
        {
            return Response.status(429).entity(ResponseError("Too many requests.")).build()
        }

        val password = payload.password?.takeIf { it.isNotBlank() }
            ?: return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Password is required."))
                .build()

        val storedHash = appUser.password
        val storedSalt = appUser.passwordSalt
        if (storedHash.isNullOrBlank() || storedSalt.isNullOrBlank() || !authenticationService.validatePassword(password, storedHash))
        {
            authAuditService.emit(
                action = "STEP_UP",
                outcome = "DENY",
                actorId = appUser.id,
                reasonCode = RevocationReasonCode.STEP_UP_REQUIRED,
                requestId = requestId,
                reason = "Password verification failed",
            )
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError("Invalid password."))
                .build()
        }

        stepUpAuthService.markFresh()
        authAuditService.emit(
            action = "STEP_UP",
            outcome = "SUCCESS",
            actorId = appUser.id,
            requestId = requestId,
        )
        return Response.ok(StepUpResponse(fresh = true, message = "Authentication refreshed.")).build()
    }
}
