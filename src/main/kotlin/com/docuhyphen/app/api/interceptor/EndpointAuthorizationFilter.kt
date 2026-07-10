package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.AuthTokenType.ACCESS
import com.docuhyphen.app.api.service.AppUserService
import java.security.MessageDigest
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.ApplicationTokenBoundaryService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.OrganizationMembershipValidationService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory
import java.util.UUID

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

    /** Best-effort client IP extracted from X-Forwarded-For / X-Real-IP request headers. */
    var clientIp: String? = null

    /**
     * SHA-256 hex of the raw share-link token from the X-Share-Link-Token header.
     * Set for all requests (including no-auth) when the header is present.
     * Used by [com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService]
     * to resolve a PUBLIC_LINK grant without storing or logging the raw token.
     */
    var shareLinkTokenHash: String? = null

    /**
     * The organization the caller has explicitly selected for this session, extracted from the
     * X-Active-Organization-Id header after the filter validates membership. Null when the
     * caller has not selected an organization (personal-product flows).
     */
    var activeOrganizationId: UUID? = null

    /** The OrganizationMembership row ID corresponding to [activeOrganizationId]. */
    var activeMembershipId: UUID? = null

    /**
     * Server-generated trace ID for this request. Always set by [CorrelationContextFilter],
     * never derived from a client-supplied header, so every request (authenticated or not) has a
     * trustworthy identifier for log/audit correlation.
     */
    var serverTraceId: String? = null

    /**
     * Correlation ID grouping this request with related requests/events across a multi-step or
     * multi-service flow. Reused from an incoming `X-Correlation-Id` header when present and
     * well-formed, otherwise generated fresh. Safe to trust for grouping only; it grants no
     * authorization and is never taken from the untrusted `X-Request-Id` hint.
     */
    var correlationId: String? = null

    /**
     * Identifies the event/request that caused this one, when the caller supplies a well-formed
     * `X-Causation-Id` header. Null when there is no known cause (this request is the origin of
     * its own chain).
     */
    var causationId: String? = null

    /**
     * Raw `X-Request-Id` header value, if present. This is an untrusted client-supplied hint kept
     * only for correlating with client-side logs; it must never be used as the trace or
     * correlation ID and must never be trusted for authorization or idempotency decisions.
     */
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
        "/auth/password-reset/initiation",
        "/auth/password-reset/completion",
        "/auth/token/refresh",
        "/auth/oauth/",
        "/auth/application/token",
        "/no-auth/exchanges",
        "/scim/", // SCIM endpoints use their own static bearer token, validated in the resource.
    )

    @Inject
    private lateinit var authenticationContext: AuthTokenContext

    override fun filter(requestContext: ContainerRequestContext)
    {
        val requestUri = requestContext.uriInfo.path
        logger.info("Intercepted request to URI: $requestUri")

        authenticationContext.clientIp = requestContext.getHeaderString("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim()
            ?: requestContext.getHeaderString("X-Real-IP")?.trim()

        val rawLinkToken = requestContext.getHeaderString("X-Share-Link-Token")?.trim()
        if (!rawLinkToken.isNullOrBlank())
        {
            authenticationContext.shareLinkTokenHash = sha256Hex(rawLinkToken)
        }

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
                logger.warn("Application token references unknown or inactive application={} uri={}", applicationId, requestUri)
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
            logger.info("Successfully authorized application token for application={} uri={}", applicationId, requestUri)
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
            abortRequest(requestContext, "Unauthorized request")
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
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        if (configurationService.isAuthSessionVersionEnabled())
        {
            val tokenSessionVersion = (claims["exchange_version"] as? Number)?.toLong() ?: 0L
            if (tokenSessionVersion != appUser.sessionVersion)
            {
                logger.warn("Session version mismatch for user={} tokenVersion={} currentVersion={}", userId, tokenSessionVersion, appUser.sessionVersion)
                abortRequest(requestContext, "Unauthorized request")
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

        // O(1) Redis check first,  covers in-flight revocations between DB writes and cache eviction.
        if (sessionRevocationCache.isRevoked(sessionId))
        {
            logger.warn("Revoked session (Redis) sessionId={} user={}", sessionId, userId)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        if (!userSessionService.isActiveSession(sessionId, userId))
        {
            logger.warn("Inactive or missing user session sessionId={} user={}", sessionId, userId)
            abortRequest(requestContext, "Unauthorized request")
            return
        }

        // Sliding inactivity (idle) timeout enforcement at request time.
        // Distinct from the absolute refresh-token / session expiry: an authenticated
        // request after a long pause must be rejected even if the access token is still
        // valid, so a forgotten/lost device cannot be reused later.
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
                    com.docuhyphen.app.api.service.auth.RevocationReasonCode.SECURITY_POLICY,
                )
                authAuditService.emit(
                    action = "REQUEST_AUTH",
                    outcome = "DENY",
                    reasonCode = com.docuhyphen.app.api.service.auth.RevocationReasonCode.SECURITY_POLICY,
                    actorId = userId,
                    sessionId = sessionId.toString(),
                    reason = "Idle timeout exceeded (idleSeconds=$idleSeconds, limit=${idleLimitMinutes * 60})",
                )
                abortRequest(requestContext, "Session timed out due to inactivity")
                return
            }
        }

        userSessionService.touchSession(sessionId)

        // DPoP (RFC 9449) sender-constraint check. Required when enabled and the access token
        // carries a `cnf.jkt` claim. The proof's JWK thumbprint must match.
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

    private fun abortWithForbidden(requestContext: ContainerRequestContext, message: String)
    {
        requestContext.abortWith(
            Response.status(Response.Status.FORBIDDEN)
                .entity(message)
                .build()
        )
    }

    private fun sha256Hex(raw: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
