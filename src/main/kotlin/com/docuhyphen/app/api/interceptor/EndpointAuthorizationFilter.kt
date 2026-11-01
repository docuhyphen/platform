package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.AuthTokenType.ACCESS
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.ApplicationTokenBoundaryService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.OrganizationMembershipValidationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID
import kotlin.collections.ArrayDeque

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

    var clientIp: String? = null

    var shareLinkTokenHash: String? = null

    var activeOrganizationId: UUID? = null

    var activeMembershipId: UUID? = null

    var userSessionId: UUID? = null

    var serverTraceId: String? = null

    var correlationId: String? = null

    var causationId: String? = null

    var clientRequestIdHint: String? = null
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
    private val applicationTokenBoundaryService: ApplicationTokenBoundaryService,
    private val applicationService: ApplicationService,
    private val appUserService: AppUserService,
    private val userSessionService: com.docuhyphen.app.api.service.auth.UserSessionService,
    private val sessionRevocationCache: com.docuhyphen.app.api.service.auth.SessionRevocationCache,
    private val dpopValidationService: com.docuhyphen.app.api.service.auth.DpopValidationService,
    private val organizationMembershipValidationService: OrganizationMembershipValidationService,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
    private val configurationService: ConfigurationService,
    private val authSessionPolicyService: com.docuhyphen.app.api.service.auth.AuthSessionPolicyService,
    private val authAuditService: com.docuhyphen.app.api.service.auth.AuthAuditService,
) : ContainerRequestFilter
{
    private val logger = LoggerFactory.getLogger(EndpointVerificationFilter::class.java.name)

    private val excludedEndpoints = listOf(
        "/auth/sign-up/initiation",
        "/auth/sign-up/completion",
        "/auth/sign-up/otp-regeneration",
        "/auth/sign-up/email-confirm",
        "/auth/sign-in/initiate",
        "/auth/sign-in/otp-regeneration",
        "/auth/sign-in/completion",
        "/auth/sign-in/lookup",
        "/auth/sign-in/mfa-sessions/",
        "/auth/password-reset/initiation",
        "/auth/password-reset/completion",
        "/auth/token/refresh",
        "/auth/oauth/",
        "/auth/application/token",
        "/no-auth/exchanges",
        "/no-auth/sales-enquiries",
        "/no-auth/information-request-access-links/",
        "/no-auth/information-requests/",
        "/scim/",
    )

    @Inject
    private lateinit var authenticationContext: AuthTokenContext

    @Inject
    private lateinit var clientIpResolver: com.docuhyphen.app.api.service.auth.ClientIpResolver

    @Inject
    private lateinit var currentVertxRequest: io.quarkus.vertx.http.runtime.CurrentVertxRequest

    override fun filter(requestContext: ContainerRequestContext)
    {
        val requestUri = normalizePath(requestContext.uriInfo.path)
        logger.info("Intercepted request to URI: $requestUri")

        authenticationContext.clientIp = clientIpResolver.resolve(
            peerAddress = resolvePeerAddress(),
            forwardedForHeader = requestContext.getHeaderString("X-Forwarded-For"),
            realIpHeader = requestContext.getHeaderString("X-Real-IP"),
        )

        val rawLinkToken = requestContext.getHeaderString("X-Share-Link-Token")?.trim()
        if (!rawLinkToken.isNullOrBlank())
        {
            authenticationContext.shareLinkTokenHash = sha256Hex(rawLinkToken)
        }

        if (excludedEndpoints.any { requestUri.startsWith(it) })
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

        val tokenType = (claims["token_type"] as? String)?.trim()?.uppercase().orEmpty()
        if (tokenType != ACCESS.name)
        {
            logger.warn("Non-access token used for protected request uri={}", requestUri)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        if (applicationTokenBoundaryService.isApplicationPrincipal(claims))
        {
            val applicationId = runCatching { UUID.fromString(claims.subject) }.getOrNull()
            if (applicationId == null)
            {
                logger.warn("Application token has an invalid subject for uri={}", requestUri)
                abortRequest(requestContext, "Unauthorized request")
                return
            }
            val scopes = applicationTokenBoundaryService.extractScopes(claims)
            if (!applicationTokenBoundaryService.isApplicationTokenAllowedForPath(requestUri, scopes))
            {
                logger.warn("Application token denied for uri={} scopes={}", requestUri, scopes)
                abortRequest(requestContext, "Unauthorized request")
                return
            }

            val application = applicationService.findActive(applicationId)
            if (application == null)
            {
                logger.warn(
                    "Application token references unknown or inactive application={} uri={}",
                    applicationId,
                    requestUri
                )
                abortRequest(requestContext, "Unauthorized request")
                return
            }

            val virtualToken = AuthToken().apply {
                this.token = token
                this.tokenType = ACCESS
                this.applicationId = applicationId
                this.application = application
            }

            authenticationContext.authToken = virtualToken
            logger.info(
                "Successfully authorized application token for application={} uri={}",
                applicationId,
                requestUri
            )
            return
        }

        if (applicationTokenBoundaryService.isApplicationEndpoint(requestUri))
        {
            logger.warn("User-session token denied from application-only endpoint uri={}", requestUri)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val userId = try
        {
            UUID.fromString(claims.subject)
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

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            logger.warn("Inactive or deprovisioned user attempted access user={}", userId)
            abortRequest(requestContext, "User is inactive", RevocationReasonCode.DEPROVISIONED)
            return
        }

        val membershipValidation = organizationMembershipValidationService.validateForSessionAccess(appUser)
        if (!membershipValidation.valid)
        {
            logger.warn(
                "Inactive organization/membership attempted access user={} reason={}",
                userId,
                membershipValidation.reasonCode,
            )
            abortRequest(
                requestContext,
                membershipValidation.message ?: "Organization membership is inactive",
                membershipValidation.reasonCode ?: RevocationReasonCode.SECURITY_POLICY,
            )
            return
        }

        if (configurationService.isAuthSessionVersionEnabled())
        {
            val tokenSessionVersion = (claims["exchange_version"] as? Number)?.toLong() ?: 0L
            if (tokenSessionVersion != appUser.sessionVersion)
            {
                logger.warn(
                    "Session version mismatch for user={} tokenVersion={} currentVersion={}",
                    userId,
                    tokenSessionVersion,
                    appUser.sessionVersion
                )
                abortRequest(
                    requestContext,
                    "Session version is no longer current",
                    RevocationReasonCode.EXCHANGE_VERSION_MISMATCH,
                )
                return
            }
        }

        val sessionIdRaw = claims["exchange_id"] as? String
        if (sessionIdRaw.isNullOrBlank())
        {
            logger.warn("Missing exchange_id claim for user={}", userId)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        val sessionId = try
        {
            UUID.fromString(sessionIdRaw)
        }
        catch (_: Exception)
        {
            logger.warn("Invalid exchange_id claim format for user={}", userId)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        if (sessionRevocationCache.isRevoked(sessionId))
        {
            logger.warn("Revoked session (Redis) sessionId={} user={}", sessionId, userId)
            abortRequest(
                requestContext,
                "Session is no longer active",
                sessionRevocationCache.reason(sessionId) ?: RevocationReasonCode.REFRESH_INVALID,
            )
            return
        }

        if (!userSessionService.isActiveSession(sessionId, userId))
        {
            logger.warn("Inactive or missing user session sessionId={} user={}", sessionId, userId)
            abortRequest(
                requestContext,
                "Session is no longer active",
                userSessionService.endReason(sessionId) ?: RevocationReasonCode.REFRESH_INVALID,
            )
            return
        }

        val sessionRecord = userSessionService.findSession(sessionId)
        val lastSeen = sessionRecord?.lastSeenAt?.toInstant()
        if (lastSeen != null)
        {
            val idlePolicy = runCatching { authSessionPolicyService.resolveForAppUser(appUser) }.getOrNull()
            val idleLimitMinutes = idlePolicy?.idleTimeoutMinutes ?: configurationService.getIdleTimeoutMinutes()
            val idleSeconds = java.time.Duration.between(lastSeen, java.time.Instant.now()).seconds
            if (idleSeconds > idleLimitMinutes * 60)
            {
                logger.warn(
                    "Idle timeout exceeded sessionId={} user={} idleSeconds={} limitSeconds={}",
                    sessionId, userId, idleSeconds, idleLimitMinutes * 60,
                )
                userSessionService.revokeSession(
                    sessionId,
                    RevocationReasonCode.INACTIVITY_TIMEOUT,
                )
                authAuditService.emit(
                    action = "REQUEST_AUTH",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.INACTIVITY_TIMEOUT,
                    actorId = userId,
                    sessionId = sessionId.toString(),
                    reason = "Idle timeout exceeded (idleSeconds=$idleSeconds, limit=${idleLimitMinutes * 60})",
                )
                abortRequest(
                    requestContext,
                    "Session timed out due to inactivity",
                    RevocationReasonCode.INACTIVITY_TIMEOUT,
                )
                return
            }
        }

        authenticationContext.userSessionId = sessionId

        if (configurationService.isDpopEnabled())
        {
            val cnf = claims["cnf"] as? Map<*, *>
            val expectedJkt = cnf?.get("jkt") as? String
            if (!expectedJkt.isNullOrBlank())
            {
                val dpopHeader = requestContext.getHeaderString("DPoP")
                val httpMethod = requestContext.method ?: "GET"
                val requestUrl = requestContext.uriInfo.requestUri.toString()
                val verify = dpopValidationService.verify(dpopHeader, httpMethod, requestUrl)
                if (!verify.valid || verify.jwkThumbprint != expectedJkt)
                {
                    logger.warn("DPoP verification failed user={} reason={}", userId, verify.reason)
                    abortRequest(requestContext, "DPoP proof required")
                    return
                }
            }
        }

        val rawActiveOrgHeader = requestContext.getHeaderString("X-Active-Organization-Id")?.trim()
        if (!rawActiveOrgHeader.isNullOrBlank())
        {
            val requestedOrgId = runCatching { UUID.fromString(rawActiveOrgHeader) }.getOrNull()
            if (requestedOrgId == null)
            {
                logger.warn("Malformed X-Active-Organization-Id header user={}", userId)
                abortWithForbidden(requestContext, "Invalid organization ID")
                return
            }

            val membership = organizationMembershipRepository.findActiveByUserAndOrg(userId, requestedOrgId)
            if (membership == null)
            {
                logger.warn(
                    "User={} requested active org={} but has no active membership; ignoring stale " +
                            "header and proceeding in personal-product mode",
                    userId,
                    requestedOrgId,
                )
            }
            else
            {
                authenticationContext.activeOrganizationId = requestedOrgId
                authenticationContext.activeMembershipId = membership.id
            }
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

    private fun abortRequest(
        requestContext: ContainerRequestContext,
        message: String,
        reasonCode: RevocationReasonCode,
    )
    {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(ResponseError(message, reasonCode.name))
                .build()
        )
    }

    private fun abortWithForbidden(requestContext: ContainerRequestContext, message: String)
    {
        requestContext.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                .entity(message)
                .build()
        )
    }

    private fun normalizePath(rawPath: String): String
    {
        val segments = rawPath.replace('\\', '/').split('/')
        val resolved = ArrayDeque<String>()

        for (segment in segments)
        {
            when (segment)
            {
                "", "." -> Unit
                ".." -> resolved.removeLastOrNull()
                else -> resolved.addLast(segment)
            }
        }

        val normalized = "/" + resolved.joinToString("/")
        return if (rawPath.endsWith("/") && !normalized.endsWith("/")) "$normalized/" else normalized
    }

    private fun resolvePeerAddress(): String? =
        currentVertxRequest.current?.request()?.remoteAddress()?.host()

    private fun sha256Hex(raw: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
