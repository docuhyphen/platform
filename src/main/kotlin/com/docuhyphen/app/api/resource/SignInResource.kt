package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SignInService
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.auth.SecurityIncidentService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Path("/auth/sign-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SignInResource @Inject constructor(
    private val signInService: SignInService,
    private val appUserService: AppUserService,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val tokenIssuanceService: TokenIssuanceService,
    private val configurationService: ConfigurationService,
    private val oauthStateService: OAuthStateService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignInResource::class.java)
    }

    @POST
    @Path("/lookup")
    fun lookupSignInMethod(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInLookupRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1500) { try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:lookup:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitLookupPerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_LOOKUP,
                    severity = SecurityIncidentSeverity.MEDIUM,
                    requestId = requestId,
                    details = "ip=$clientIp",
                )
                authAuditService.emit(
                    action = "SIGN_IN_LOOKUP",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val email = payload.email?.trim()?.lowercase()
            if (email.isNullOrBlank() || email.length < 5)
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
                    severity = SecurityIncidentSeverity.LOW,
                    requestId = requestId,
                    details = "ip=$clientIp;reason=invalid_email_shape;len=${email?.length ?: 0}",
                )
                authAuditService.emit(
                    action = "SIGN_IN_LOOKUP",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                    reason = "Invalid lookup input",
                )
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Email is required"))
                    .build()
            }

            // If client already selected an org (second lookup call after MULTIPLE_ORGS picker), resolve that org directly
            val selectedOrgId = payload.orgId?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            if (selectedOrgId != null)
            {
                val org = organizationIdentityPolicyService.findOrganizationById(selectedOrgId)
                if (org != null)
                {
                    val orgProviders = organizationIdentityPolicyService.findActiveProviderConfigsForOrganization(org.id)
                    val preferredExternal = orgProviders.firstOrNull { !it.provider.equals("INTERNAL", ignoreCase = true) }
                    if (preferredExternal != null)
                    {
                        val providerType = runCatching { IdentityProviderType.valueOf(preferredExternal.provider.uppercase()) }.getOrNull()
                        if (providerType != null && providerType != IdentityProviderType.INTERNAL)
                        {
                            val redirectUrl = buildAuthorizeUrl(
                                providerType = providerType,
                                flow = "signin",
                                orgIdpConfigId = preferredExternal.id.toString(),
                            )
                            return Response.ok(
                                SignInLookupResponse(
                                    authMethod = providerType.name,
                                    redirectUrl = redirectUrl,
                                    outcome = "ORG_FOUND",
                                    fallbackAuthMethod = "INTERNAL",
                                    organizations = listOf(SignInLookupOrganizationOption(id = org.id.toString(), name = org.name)),
                                    availableProviders = listOf(providerType.name, "INTERNAL"),
                                )
                            ).build()
                        }
                    }
                    return Response.ok(
                        SignInLookupResponse(
                            authMethod = "INTERNAL",
                            outcome = "ORG_FOUND",
                            fallbackAuthMethod = "INTERNAL",
                            organizations = listOf(SignInLookupOrganizationOption(id = org.id.toString(), name = org.name)),
                            availableProviders = listOf("INTERNAL"),
                        )
                    ).build()
                }
            }

            val domainOrganizations = organizationIdentityPolicyService.resolveOrganizationsForEmail(email)

            if (domainOrganizations.size > 1)
            {
                if (domainOrganizations.size >= 5)
                {
                    securityIncidentService.record(
                        incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
                        severity = SecurityIncidentSeverity.MEDIUM,
                        requestId = requestId,
                        details = "ip=$clientIp;reason=high_org_fanout;count=${domainOrganizations.size}",
                    )
                }

                val organizations = domainOrganizations
                    .map { org -> SignInLookupOrganizationOption(id = org.id.toString(), name = org.name) }

                return Response.ok(
                    SignInLookupResponse(
                        authMethod = "INTERNAL",
                        outcome = "MULTIPLE_ORGS",
                        organizations = organizations,
                        availableProviders = listOf("INTERNAL"),
                    )
                ).build()
            }

            if (domainOrganizations.size == 1)
            {
                val organization = domainOrganizations.first()
                val orgProviders = organizationIdentityPolicyService.findActiveProviderConfigsForOrganization(organization.id)
                val preferredExternal = orgProviders.firstOrNull { !it.provider.equals("INTERNAL", ignoreCase = true) }

                if (preferredExternal != null)
                {
                    val providerType = runCatching { IdentityProviderType.valueOf(preferredExternal.provider.uppercase()) }.getOrNull()
                    if (providerType != null && providerType != IdentityProviderType.INTERNAL)
                    {
                        val redirectUrl = buildAuthorizeUrl(
                            providerType = providerType,
                            flow = "signin",
                            orgIdpConfigId = preferredExternal.id.toString(),
                        )
                        return Response.ok(
                            SignInLookupResponse(
                                authMethod = providerType.name,
                                redirectUrl = redirectUrl,
                                outcome = "ORG_FOUND",
                                fallbackAuthMethod = "INTERNAL",
                                organizations = listOf(SignInLookupOrganizationOption(id = organization.id.toString(), name = organization.name)),
                                availableProviders = listOf(providerType.name, "INTERNAL"),
                            )
                        ).build()
                    }
                }

                return Response.ok(
                    SignInLookupResponse(
                        authMethod = "INTERNAL",
                        outcome = "ORG_FOUND",
                        fallbackAuthMethod = "INTERNAL",
                        organizations = listOf(SignInLookupOrganizationOption(id = organization.id.toString(), name = organization.name)),
                        availableProviders = listOf("INTERNAL"),
                    )
                ).build()
            }

            val platformProviders = identityProviderRegistry.getAllProviders()
                .map { it.getProviderType() }
                .filter { it != IdentityProviderType.INTERNAL }
                .map { it.name }
                .sorted()

            Response.ok(
                SignInLookupResponse(
                    authMethod = "INTERNAL",
                    outcome = "NO_ORG",
                    availableProviders = listOf("INTERNAL") + platformProviders,
                )
            ).build()
                .also {
                    authAuditService.emit(
                        action = "SIGN_IN_LOOKUP",
                        outcome = "SUCCESS",
                        requestId = requestId,
                    )
                }
        }
        catch (exception: Exception)
        {
            logger.error("Error during sign-in lookup", exception)
            authAuditService.emit(
                action = "SIGN_IN_LOOKUP",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An error occurred during sign-in lookup."))
                .build()
        }
        }
    }

    private fun buildAuthorizeUrl(providerType: IdentityProviderType, flow: String, orgIdpConfigId: String?): String
    {
        val base = configurationService.baseUrl.trimEnd('/')
        val provider = providerType.name.lowercase()
        val flowEncoded = URLEncoder.encode(flow, StandardCharsets.UTF_8)
        val orgIdpPart = orgIdpConfigId
            ?.takeIf { it.isNotBlank() }
            ?.let { "&orgIdpConfigId=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
            .orEmpty()

        return "$base/auth/oauth/$provider/authorize?flow=$flowEncoded$orgIdpPart"
    }

    @POST
    @Path("/initiate")
    fun signIn(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(2000) { try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:initiate:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitSignInInitiatePerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_INITIATE,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "ip=$clientIp",
                )
                authAuditService.emit(
                    action = "SIGN_IN_INITIATE",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val mfaSession = with(payload) {

                signInService.initiateSignIn(email, password, clientIp)
            }

            val signInResponse = SignInResponse("", mfaSession.id.toString())
            Response.ok(signInResponse).build()
                .also {
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "SUCCESS",
                        requestId = requestId,
                    )
                }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is TooManyRequestsException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(429).entity(responseError).build()
                }

                is InvalidSignInCredentialsException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                is InactiveAccountException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.DEPROVISIONED,
                        requestId = requestId,
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is PasswordChangeRequiredException,
                is TemporaryPasswordExpiredException ->
                {
                    val reasonCode = when (exception)
                    {
                        is PasswordChangeRequiredException -> "PASSWORD_CHANGE_REQUIRED"
                        else -> "TEMP_PASSWORD_EXPIRED"
                    }
                    val responseError = ResponseError(
                        errorMessage = exception.message,
                        reasonCode = reasonCode,
                    )
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                        reason = "Temporary password requires reset",
                    )
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error initiating sign in", exception)
                    authAuditService.emit(
                        action = "SIGN_IN_INITIATE",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    val responseError = ResponseError("A server error occurred while signing in.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
    }

    @POST
    @Path("/completion")
    fun completeSignIn(
        @Context request: io.vertx.core.http.HttpServerRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: SignInCompletionRequest
    ): Response
    {
        return ResourceEndpointDelayHelper.withFixedFloor(1200) { try
        {
            val clientIp = getClientIpAddress(request)
            if (authRateLimitService.isLimited(
                    key = "auth:sign-in:completion:$clientIp",
                    maxPerMinute = configurationService.getAuthRateLimitSignInCompletionPerMinute(),
                ))
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_SIGNIN_COMPLETION,
                    severity = SecurityIncidentSeverity.HIGH,
                    requestId = requestId,
                    details = "ip=$clientIp",
                )
                authAuditService.emit(
                    action = "SIGN_IN_COMPLETION",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    requestId = requestId,
                )
                return Response.status(429).entity(ResponseError("Too many requests. Please try again later.")).build()
            }

            val tokenTriple = with(payload) {
                signInService.completeSignIn(email, otp, mfaSessionId,
                    userAgent = request.getHeader("User-Agent"),
                    ipAddress = clientIp,
                )
            }

            val signInCompletionResponse = SignInCompletionResponse(
                accessToken = tokenTriple.accessToken,
                idToken = tokenTriple.idToken,
            )
            val policyUser = payload.email
                ?.trim()
                ?.lowercase()
                ?.let { appUserService.findByEmail(it) }
            val refreshCookie = if (policyUser != null)
            {
                tokenIssuanceService.buildRefreshTokenCookieWithPolicy(tokenTriple.refreshToken, policyUser)
            }
            else
            {
                tokenIssuanceService.buildRefreshTokenCookie(tokenTriple.refreshToken)
            }
            val csrfToken = tokenIssuanceService.generateCsrfToken()
            val csrfCookie = tokenIssuanceService.buildCsrfTokenCookie(csrfToken)
            Response.ok(signInCompletionResponse).cookie(refreshCookie, csrfCookie).build()
                .also {
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "SUCCESS",
                        requestId = requestId,
                    )
                }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OTPExpiredException,
                is EmailRequiredException,
                is MaxAttemptsOTPExceededException,
                is InvalidOtpException ->
                {
                    val responseError = ResponseError(exception.message)
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to complete sign-in.")
                    logger.error("Error completing sign-in", exception)
                    authAuditService.emit(
                        action = "SIGN_IN_COMPLETION",
                        outcome = "DENY",
                        reasonCode = RevocationReasonCode.SECURITY_POLICY,
                        requestId = requestId,
                    )
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
    }

    @POST
    @Path("/otp-regeneration")
    fun resendOtp(
        payload: ResendOtpRequest
    ): Response
    {
        // ...existing otp-regeneration code unchanged...
        return ResourceEndpointDelayHelper.withFixedFloor(1200) { try
        {
            val mfaSession = with(payload) {
                signInService.redoMfa(email, mfaSessionId)
            }

            val resendOtpResponse = SignInResponse("A new verification has been sent to your email", mfaSession.id.toString())
            Response.ok(resendOtpResponse).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is TooManyRequestsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(429).entity(responseError).build()
                }

                is InvalidSignInCredentialsException,
                is MaxAttemptsOTPExceededException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Something went wrong while trying to resend verification code.")
                    logger.error("Error resending OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
        }
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

        if (!ipAddress.isNullOrBlank() && ipAddress.contains(","))
        {
            ipAddress = ipAddress.split(",")[0].trim()
        }

        return ipAddress
    }
}
