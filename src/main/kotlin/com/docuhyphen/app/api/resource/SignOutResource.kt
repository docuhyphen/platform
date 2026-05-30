package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.LogoutPropagationInfoResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.CsrfProtectionService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SignOutService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("/auth/sign-out")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignOutResource @Inject constructor(
    private val signOutService: SignOutService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val csrfProtectionService: CsrfProtectionService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignOutResource::class.java)
    }

    @POST
    fun signOut(
        @QueryParam("outOfAllDevices") outOfAllDevices: Boolean = false,
        @CookieParam("csrf_token") csrfCookie: Cookie?,
        @HeaderParam("X-CSRF-Token") csrfHeader: String?,
        @HeaderParam("Origin") originHeader: String?,
        @HeaderParam("Referer") refererHeader: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
        @Context request: io.vertx.core.http.HttpServerRequest,
    ): Response
    {
        return try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:logout:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitLogoutPerMinute(),
                ))
            {
                authAuditService.emit(
                    action = "SIGN_OUT",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return Response.status(429)
                    .entity(ResponseError("Too many requests. Please try again later."))
                    .build()
            }

            if (!csrfProtectionService.verify(csrfCookie?.value, csrfHeader, originHeader, refererHeader))
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError("CSRF validation failed"))
                    .build()
            }

            signOutService.signOut(outOfAllDevices, requestId)
            val clearCookie = tokenIssuanceService.buildClearRefreshTokenCookie()
            val clearCsrfCookie = tokenIssuanceService.buildClearCsrfTokenCookie()
            Response.ok().cookie(clearCookie, clearCsrfCookie).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error during sign-out.", exception)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred.")).build()
        }
    }

    @GET
    @Path("/propagation-info")
    fun getLogoutPropagationInfo(): Response
    {
        return Response.ok(
            LogoutPropagationInfoResponse(
                platformLogoutAuthoritative = true,
                idpGlobalLogoutEquivalent = false,
                message = "Signing out from an identity provider is treated as an external signal and does not automatically revoke platform sessions.",
                exposureBounds = listOf(
                    "Access tokens are short-lived by policy.",
                    "Refresh requests enforce active user, organization membership, and session checks.",
                    "Session/version revocation can invalidate all active device sessions.",
                ),
            )
        ).build()
    }

    private fun getClientIpAddress(request: io.vertx.core.http.HttpServerRequest): String
    {
        var ipAddress = request.getHeader("X-Forwarded-For")

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.getHeader("Proxy-Client-IP")
        }

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.getHeader("X-Real-IP")
        }

        if (ipAddress.isNullOrBlank() || "unknown".equals(ipAddress, ignoreCase = true))
        {
            ipAddress = request.remoteAddress()?.host() ?: "0.0.0.0"
        }

        if (ipAddress.contains(","))
        {
            ipAddress = ipAddress.split(",")[0].trim()
        }

        return ipAddress
    }
}