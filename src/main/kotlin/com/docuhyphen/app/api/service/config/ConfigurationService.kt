package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Optional

/**
 * Central configuration facade.
 *
 * Core infrastructure properties (base URL, JWT, AWS, secrets rotation) are owned here.
 * Auth-specific properties are delegated to [AuthConfigService].
 * OAuth/OIDC/audit/SCIM properties are delegated to [OAuthConfigService].
 *
 * Splitting avoids the JVM 64 KB method-size limit that Quarkus ArC can hit when a single
 * class has many @ConfigProperty constructor parameters (each becomes a Supplier<T> in
 * the generated *_Bean constructor).
 */
@ApplicationScoped
class ConfigurationService @Inject constructor(
    private val awsSecretsManagerService: AwsSecretsManagerService,
    private val authConfig: AuthConfigService,
    private val oauthConfig: OAuthConfigService,

    @ConfigProperty(name = "app.base-url")
    val baseUrl: String,

    @ConfigProperty(name = "app.email.subject-title")
    val emailSubjectTitle: String,

    @ConfigProperty(name = "app.email.new-user-notification-address", defaultValue = "support@docuhyphen.com")
    private val newUserNotificationAddressConfig: String,

    @ConfigProperty(name = "app.email.new-org-notification-address", defaultValue = "sales@docuhyphen.com")
    private val newOrgNotificationAddressConfig: String,

    @ConfigProperty(name = "app.security.jwt.secret-provider")
    val jwtSecretProvider: String,

    @ConfigProperty(name = "app.security.jwt.local-secret")
    val localJwtSecret: String,

    @ConfigProperty(name = "app.security.aws.region")
    val awsRegion: String,

    @ConfigProperty(name = "app.security.jwt.aws-secret-id")
    val jwtAwsSecretId: Optional<String>,

    @ConfigProperty(name = "app.secrets.org-idp.region", defaultValue = "af-south-1")
    private val orgIdpSecretsRegionConfig: String,

    @ConfigProperty(name = "app.secrets.org-idp.prefix", defaultValue = "docuhyphen/org")
    private val orgIdpSecretsPrefixConfig: String,

    @ConfigProperty(name = "app.secrets.rotation.enabled", defaultValue = "true")
    private val secretsRotationEnabledConfig: Boolean,

    @ConfigProperty(name = "app.secrets.rotation.interval-days", defaultValue = "90")
    private val secretsRotationIntervalDaysConfig: Long,

    @ConfigProperty(name = "app.secrets.rotation.overlap-hours", defaultValue = "24")
    private val secretsRotationOverlapHoursConfig: Long,

    @ConfigProperty(name = "app.secrets.rotation.allow-previous-during-overlap", defaultValue = "true")
    private val secretsRotationAllowPreviousDuringOverlapConfig: Boolean,

    @ConfigProperty(name = "app.secrets.rotation.rollback.enabled", defaultValue = "true")
    private val secretsRotationRollbackEnabledConfig: Boolean,

    @ConfigProperty(name = "app.secrets.rotation.rollback.require-monitor-phase", defaultValue = "true")
    private val secretsRotationRollbackRequireMonitorPhaseConfig: Boolean,

    @ConfigProperty(name = "app.secrets.rotation.runtime.allowed-phases", defaultValue = "MONITOR,ACTIVATE")
    private val secretsRotationRuntimeAllowedPhasesConfig: String,

    @ConfigProperty(name = "app.security.app-admin.bootstrap-email")
    private val bootstrapAppAdminEmailConfig: Optional<String>,
)
{
    @Volatile
    private var cachedJwtSecret: String? = null

    companion object
    {
        /**
         * The checked-in placeholder used by `application.properties` and the local profile.
         * Any deployment that resolves this value is signing tokens with a publicly known key,
         * so [AuthSecurityStartupValidator] refuses to boot outside development.
         */
        const val DEVELOPMENT_JWT_SECRET =
            "myverysecurekeythatis32byteslong*)&GAS&G_A(&F9*FDA(&_FD_A(&F+(D&FA"

        /** Minimum key length for HS256/HS512 signing material. */
        const val MIN_JWT_SECRET_LENGTH = 32
    }

    // -------------------------------------------------------------------------
    // Core / general
    // -------------------------------------------------------------------------

    /**
     * Email of the user to auto-promote to APP_ADMIN at startup when no active App Admin exists
     * (dev/first-run bootstrap). Blank/absent disables bootstrapping. See [AppRoleAssignmentService].
     */
    fun getBootstrapAppAdminEmail(): String? =
        bootstrapAppAdminEmailConfig.orElse(null)?.trim()?.takeIf { it.isNotBlank() }

    /** Address that receives an internal notification whenever a new user completes registration. */
    fun getNewUserNotificationEmail(): String = newUserNotificationAddressConfig

    /** Address that receives an internal notification whenever a new organization registers. */
    fun getNewOrgNotificationEmail(): String = newOrgNotificationAddressConfig

    fun getMaxSignUpCompletionOtpAttempts(): Long = 3
    fun getSignUpOtpExpiryMins(): Long = 5
    fun getAppPhoneSubjectTitle() = emailSubjectTitle
    fun getSignInEmailOtpMFAExpiryMins(): Long = 5
    fun getSignInSmsOtpMFAExpiryMins(): Long = 5
    fun getMaxSignInAttempts(): Long = 3
    fun getPasswordResetOtpExpiryMins(): Long = 10
    fun getMaxOtpRequestsPerMinute() = 5L
    fun getSignInResendCooldownSeconds(): Long = 30

    // -------------------------------------------------------------------------
    // Org-IdP secrets / rotation
    // -------------------------------------------------------------------------

    fun getOrgIdpSecretsRegion(): String = orgIdpSecretsRegionConfig
    fun getOrgIdpSecretsPrefix(): String = orgIdpSecretsPrefixConfig
    fun isSecretsRotationEnabled(): Boolean = secretsRotationEnabledConfig
    fun getSecretsRotationIntervalDays(): Long = secretsRotationIntervalDaysConfig
    fun getSecretsRotationOverlapHours(): Long = secretsRotationOverlapHoursConfig
    fun isSecretsRotationAllowPreviousDuringOverlapEnabled(): Boolean = secretsRotationAllowPreviousDuringOverlapConfig
    fun isSecretsRotationRollbackEnabled(): Boolean = secretsRotationRollbackEnabledConfig
    fun isSecretsRotationRollbackRequireMonitorPhaseEnabled(): Boolean = secretsRotationRollbackRequireMonitorPhaseConfig
    fun getSecretsRotationRuntimeAllowedPhases(): Set<String> = secretsRotationRuntimeAllowedPhasesConfig
        .split(',')
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .toSet()

    /** JWT issuer and audience value for tokens produced by this deployment. */
    fun getJwtIssuer(): String = baseUrl

    // -------------------------------------------------------------------------
    // JWT secret resolution
    // -------------------------------------------------------------------------

    fun getJwtSecret(): String
    {
        cachedJwtSecret?.let { return it }

        val resolvedSecret = if (jwtSecretProvider.equals("aws", ignoreCase = true))
        {
            val secretId = jwtAwsSecretId.get().trim().orEmpty()

            if (secretId.isBlank())
            {
                throw IllegalStateException("app.security.jwt.aws-secret-id is required when JWT secret provider is aws")
            }

            awsSecretsManagerService.getSecretString(secretId, awsRegion)
        }
        else
        {
            localJwtSecret
        }

        if (resolvedSecret.isBlank())
        {
            throw IllegalStateException("JWT secret cannot be blank")
        }

        if (resolvedSecret.length < MIN_JWT_SECRET_LENGTH)
        {
            throw IllegalStateException(
                "JWT secret must be at least $MIN_JWT_SECRET_LENGTH characters"
            )
        }

        cachedJwtSecret = resolvedSecret
        return resolvedSecret
    }

    /** True when the resolved signing key is the checked-in development placeholder. */
    fun isUsingDevelopmentJwtSecret(): Boolean =
        runCatching { getJwtSecret() == DEVELOPMENT_JWT_SECRET }.getOrDefault(false)

    // -------------------------------------------------------------------------
    // Auth token / session,  delegated to AuthConfigService
    // -------------------------------------------------------------------------

    fun getAccessTokenExpiryMinutes(): Long = authConfig.getAccessTokenExpiryMinutes()
    fun getIdTokenExpiryMinutes(): Long = 15
    fun getRefreshTokenExpiryMinutes(): Long = authConfig.getRefreshTokenExpiryMinutes()
    fun getIdleTimeoutMinutes(): Long = authConfig.getIdleTimeoutMinutes()
    fun getLinkTokenExpiryMinutes(): Long = 5
    fun getRefreshRotationGraceSeconds(): Long = authConfig.getRefreshRotationGraceSeconds()
    fun isAuthSessionVersionEnabled(): Boolean = authConfig.isAuthSessionVersionEnabled()
    fun isAuthRefreshRotationEnabled(): Boolean = authConfig.isAuthRefreshRotationEnabled()
    fun isAuthRefreshReuseDetectionEnabled(): Boolean = authConfig.isAuthRefreshReuseDetectionEnabled()
    fun isAuthRefreshStrictReuseDetectionEnabled(): Boolean = authConfig.isAuthRefreshStrictReuseDetectionEnabled()
    fun getDefaultSessionMaxDurationHours(): Long = authConfig.getDefaultSessionMaxDurationHours()
    fun getMinAccessTokenExpiryMinutes(): Long = authConfig.getMinAccessTokenExpiryMinutes()
    fun getMaxAccessTokenExpiryMinutes(): Long = authConfig.getMaxAccessTokenExpiryMinutes()
    fun getMinRefreshTokenExpiryMinutes(): Long = authConfig.getMinRefreshTokenExpiryMinutes()
    fun getMaxRefreshTokenExpiryMinutes(): Long = authConfig.getMaxRefreshTokenExpiryMinutes()
    fun getMinSessionMaxDurationHours(): Long = authConfig.getMinSessionMaxDurationHours()
    fun getMaxSessionMaxDurationHours(): Long = authConfig.getMaxSessionMaxDurationHours()
    fun getMinIdleTimeoutMinutes(): Long = authConfig.getMinIdleTimeoutMinutes()
    fun getMaxIdleTimeoutMinutes(): Long = authConfig.getMaxIdleTimeoutMinutes()
    fun isCsrfEnabled(): Boolean = authConfig.isCsrfEnabled()
    fun isCsrfRequireOriginCheckEnabled(): Boolean = authConfig.isCsrfRequireOriginCheckEnabled()
    val csrfEnabled: Boolean get() = authConfig.csrfEnabled
    val csrfRequireOriginCheck: Boolean get() = authConfig.csrfRequireOriginCheck
    fun getOauthStateTtlSeconds(): Long = authConfig.getOauthStateTtlSeconds()
    fun getOidcAllowedClockSkewSeconds(): Long = authConfig.getOidcAllowedClockSkewSeconds()
    fun isDpopEnabled(): Boolean = authConfig.isDpopEnabled()
    fun isAuthRateLimitEnabled(): Boolean = authConfig.isAuthRateLimitEnabled()
    fun getAuthRateLimitLookupPerMinute(): Long = authConfig.getAuthRateLimitLookupPerMinute()
    fun getAuthRateLimitCallbackPerMinute(): Long = authConfig.getAuthRateLimitCallbackPerMinute()
    fun getAuthRateLimitRefreshPerMinute(): Long = authConfig.getAuthRateLimitRefreshPerMinute()
    fun getAuthRateLimitLogoutPerMinute(): Long = authConfig.getAuthRateLimitLogoutPerMinute()
    fun getAuthRateLimitAuthorizePerMinute(): Long = authConfig.getAuthRateLimitAuthorizePerMinute()
    fun getAuthRateLimitSignInInitiatePerMinute(): Long = authConfig.getAuthRateLimitSignInInitiatePerMinute()
    fun getAuthRateLimitSignInCompletionPerMinute(): Long = authConfig.getAuthRateLimitSignInCompletionPerMinute()
    fun getAuthRateLimitDirectoryPerMinute(): Long = authConfig.getAuthRateLimitDirectoryPerMinute()
    fun getAuthRateLimitOtpRegenerationPerMinute(): Long = authConfig.getAuthRateLimitOtpRegenerationPerMinute()
    fun isForwardedHeadersEnabled(): Boolean = authConfig.isForwardedHeadersEnabled()
    fun getTrustedProxyCidrs(): List<String> = authConfig.getTrustedProxyCidrs()
    fun getStepUpMaxAgeSeconds(): Long = authConfig.getStepUpMaxAgeSeconds()
    fun getPasswordMinLength(): Int = authConfig.getPasswordMinLength()
    fun getPasswordMaxLength(): Int = authConfig.getPasswordMaxLength()
    fun getPasswordBcryptCost(): Int = authConfig.getPasswordBcryptCost()

    // -------------------------------------------------------------------------
    // OAuth providers / OIDC / application tokens / audit / SCIM,  delegated to OAuthConfigService
    // -------------------------------------------------------------------------

    val microsoftOAuthClientId: String get() = oauthConfig.getMicrosoftOAuthClientId()
    val microsoftOAuthClientSecret: String get() = oauthConfig.getMicrosoftOAuthClientSecret()
    val microsoftOAuthTenantId: String get() = oauthConfig.getMicrosoftOAuthTenantId()
    val microsoftOAuthRedirectUri: String get() = oauthConfig.getMicrosoftOAuthRedirectUri()

    val googleOAuthClientId: String get() = oauthConfig.getGoogleOAuthClientId()
    val googleOAuthClientSecret: String get() = oauthConfig.getGoogleOAuthClientSecret()
    val googleOAuthRedirectUri: String get() = oauthConfig.getGoogleOAuthRedirectUri()

    fun isOidcRequireAzpWhenMultiAudEnabled(): Boolean = oauthConfig.isOidcRequireAzpWhenMultiAudEnabled()
    fun getOidcRequiredClaimsGoogle(): Set<String> = oauthConfig.getOidcRequiredClaimsGoogle()
    fun getOidcRequiredClaimsMicrosoft(): Set<String> = oauthConfig.getOidcRequiredClaimsMicrosoft()

    fun isMicrosoftMultiTenantAllowed(): Boolean = oauthConfig.isMicrosoftMultiTenantAllowed()
    fun isMicrosoftEmailDomainOwnerVerifiedRequired(): Boolean = oauthConfig.isMicrosoftEmailDomainOwnerVerifiedRequired()
    fun isMicrosoftPreferredUsernameAsEmailAllowed(): Boolean = oauthConfig.isMicrosoftPreferredUsernameAsEmailAllowed()

    fun getOidcHttpConnectTimeoutSeconds(): Long = oauthConfig.getOidcHttpConnectTimeoutSeconds()
    fun getOidcHttpRequestTimeoutSeconds(): Long = oauthConfig.getOidcHttpRequestTimeoutSeconds()
    fun getOidcJwksMinRefreshIntervalSeconds(): Long = oauthConfig.getOidcJwksMinRefreshIntervalSeconds()
    fun getOidcJwksUnknownKidNegativeCacheSeconds(): Long = oauthConfig.getOidcJwksUnknownKidNegativeCacheSeconds()

    fun getApplicationTokenDefaultScopes(): Set<String> = oauthConfig.getApplicationTokenDefaultScopes()
    fun getApplicationTokenRequiredScope(): String = oauthConfig.getApplicationTokenRequiredScope()
    fun getApplicationTokenIntegrationScope(): String = oauthConfig.getApplicationTokenIntegrationScope()
    fun getApplicationTokenServiceScope(): String = oauthConfig.getApplicationTokenServiceScope()
    fun getApplicationTokenAllowedEndpointPrefixes(): Set<String> = oauthConfig.getApplicationTokenAllowedEndpointPrefixes()


    fun getScimBearerToken(): String = oauthConfig.getScimBearerToken()
    fun getDirectoryLookupMaxResults(): Int = oauthConfig.getDirectoryLookupMaxResults()
    fun getDirectoryLookupMinQueryLength(): Int = oauthConfig.getDirectoryLookupMinQueryLength()
}
