package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

/**
 * Auth-specific configuration properties.
 *
 * Extracted from [ConfigurationService] to keep each CDI bean's generated constructor
 * well under the JVM 64 KB method bytecode limit (Quarkus ArC generates one Supplier<T>
 * per @ConfigProperty constructor parameter in the *_Bean wrapper class).
 */
@ApplicationScoped
class AuthConfigService @Inject constructor(

    @ConfigProperty(name = "app.auth.refresh.rotation.grace-seconds", defaultValue = "60")
    private val refreshRotationGraceSecondsConfig: Long,

    @ConfigProperty(name = "app.auth.session-version.enabled", defaultValue = "true")
    private val authSessionVersionEnabledConfig: Boolean,

    @ConfigProperty(name = "app.auth.refresh.rotation.enabled", defaultValue = "true")
    private val authRefreshRotationEnabledConfig: Boolean,

    @ConfigProperty(name = "app.auth.refresh.reuse-detection.enabled", defaultValue = "true")
    private val authRefreshReuseDetectionEnabledConfig: Boolean,

    @ConfigProperty(name = "app.auth.refresh.strict-reuse-detection", defaultValue = "false")
    private val authRefreshStrictReuseDetectionConfig: Boolean,

    @ConfigProperty(name = "app.auth.access-token-default-minutes", defaultValue = "15")
    private val accessTokenDefaultMinutesConfig: Long,

    @ConfigProperty(name = "app.auth.refresh-token-default-days", defaultValue = "7")
    private val refreshTokenDefaultDaysConfig: Long,

    @ConfigProperty(name = "app.auth.session-max-default-hours", defaultValue = "168")
    private val sessionMaxDefaultHoursConfig: Long,

    @ConfigProperty(name = "app.auth.access-token-min-minutes", defaultValue = "5")
    private val accessTokenMinMinutesConfig: Long,

    @ConfigProperty(name = "app.auth.access-token-max-minutes", defaultValue = "60")
    private val accessTokenMaxMinutesConfig: Long,

    @ConfigProperty(name = "app.auth.refresh-token-min-days")
    private val refreshTokenMinDaysConfig: Long,

    @ConfigProperty(name = "app.auth.refresh-token-max-days", defaultValue = "30")
    private val refreshTokenMaxDaysConfig: Long,

    @ConfigProperty(name = "app.auth.session-max-min-hours")
    private val sessionMaxMinHoursConfig: Long,

    @ConfigProperty(name = "app.auth.session-max-max-hours", defaultValue = "720")
    private val sessionMaxMaxHoursConfig: Long,

    @ConfigProperty(name = "app.auth.csrf.enabled", defaultValue = "false")
    val csrfEnabled: Boolean,

    @ConfigProperty(name = "app.auth.csrf.require-origin-check", defaultValue = "true")
    val csrfRequireOriginCheck: Boolean,

    @ConfigProperty(name = "app.auth.oauth.state.ttl-seconds", defaultValue = "300")
    private val oauthStateTtlSecondsConfig: Long,

    @ConfigProperty(name = "app.oidc.allowed-clock-skew-seconds", defaultValue = "120")
    private val oidcAllowedClockSkewSecondsConfig: Long,

    @ConfigProperty(name = "app.auth.dpop.enabled", defaultValue = "false")
    private val dpopEnabledConfig: Boolean,

    @ConfigProperty(name = "app.auth.rate-limit.enabled", defaultValue = "true")
    private val authRateLimitEnabledConfig: Boolean,

    @ConfigProperty(name = "app.auth.rate-limit.lookup.per-minute", defaultValue = "60")
    private val authRateLimitLookupPerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.callback.per-minute", defaultValue = "60")
    private val authRateLimitCallbackPerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.refresh.per-minute", defaultValue = "30")
    private val authRateLimitRefreshPerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.logout.per-minute", defaultValue = "30")
    private val authRateLimitLogoutPerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.authorize.per-minute", defaultValue = "60")
    private val authRateLimitAuthorizePerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.sign-in-initiate.per-minute", defaultValue = "30")
    private val authRateLimitSignInInitiatePerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.sign-in-completion.per-minute", defaultValue = "30")
    private val authRateLimitSignInCompletionPerMinuteConfig: Long,

    @ConfigProperty(name = "app.auth.rate-limit.directory.per-minute", defaultValue = "60")
    private val authRateLimitDirectoryPerMinuteConfig: Long,
)
{
    fun getRefreshRotationGraceSeconds(): Long = refreshRotationGraceSecondsConfig
    fun isAuthSessionVersionEnabled(): Boolean = authSessionVersionEnabledConfig
    fun isAuthRefreshRotationEnabled(): Boolean = authRefreshRotationEnabledConfig
    fun isAuthRefreshReuseDetectionEnabled(): Boolean = authRefreshReuseDetectionEnabledConfig
    fun isAuthRefreshStrictReuseDetectionEnabled(): Boolean = authRefreshStrictReuseDetectionConfig
    fun getAccessTokenExpiryMinutes(): Long = accessTokenDefaultMinutesConfig
    fun getRefreshTokenExpiryDays(): Long = refreshTokenDefaultDaysConfig
    fun getDefaultSessionMaxDurationHours(): Long = sessionMaxDefaultHoursConfig
    fun getMinAccessTokenExpiryMinutes(): Long = accessTokenMinMinutesConfig
    fun getMaxAccessTokenExpiryMinutes(): Long = accessTokenMaxMinutesConfig
    fun getMinRefreshTokenExpiryDays(): Long = refreshTokenMinDaysConfig
    fun getMaxRefreshTokenExpiryDays(): Long = refreshTokenMaxDaysConfig
    fun getMinSessionMaxDurationHours(): Long = sessionMaxMinHoursConfig
    fun getMaxSessionMaxDurationHours(): Long = sessionMaxMaxHoursConfig
    fun isCsrfEnabled(): Boolean = csrfEnabled
    fun isCsrfRequireOriginCheckEnabled(): Boolean = csrfRequireOriginCheck
    fun getOauthStateTtlSeconds(): Long = oauthStateTtlSecondsConfig
    fun getOidcAllowedClockSkewSeconds(): Long = oidcAllowedClockSkewSecondsConfig
    fun isDpopEnabled(): Boolean = dpopEnabledConfig
    fun isAuthRateLimitEnabled(): Boolean = authRateLimitEnabledConfig
    fun getAuthRateLimitLookupPerMinute(): Long = authRateLimitLookupPerMinuteConfig
    fun getAuthRateLimitCallbackPerMinute(): Long = authRateLimitCallbackPerMinuteConfig
    fun getAuthRateLimitRefreshPerMinute(): Long = authRateLimitRefreshPerMinuteConfig
    fun getAuthRateLimitLogoutPerMinute(): Long = authRateLimitLogoutPerMinuteConfig
    fun getAuthRateLimitAuthorizePerMinute(): Long = authRateLimitAuthorizePerMinuteConfig
    fun getAuthRateLimitSignInInitiatePerMinute(): Long = authRateLimitSignInInitiatePerMinuteConfig
    fun getAuthRateLimitSignInCompletionPerMinute(): Long = authRateLimitSignInCompletionPerMinuteConfig
    fun getAuthRateLimitDirectoryPerMinute(): Long = authRateLimitDirectoryPerMinuteConfig
}
