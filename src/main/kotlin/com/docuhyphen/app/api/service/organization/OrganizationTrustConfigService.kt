package com.docuhyphen.app.api.service.organization

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

@ApplicationScoped
class OrganizationTrustConfigService @Inject constructor(
    @ConfigProperty(name = "app.organization-trust.request-expiry-days", defaultValue = "14")
    requestExpiryDays: Long,
    @ConfigProperty(name = "app.organization-trust.request-cooldown-days", defaultValue = "7")
    requestCooldownDays: Long,
    @ConfigProperty(name = "app.organization-trust.review-period-days", defaultValue = "365")
    reviewPeriodDays: Long,
    @ConfigProperty(name = "app.organization-trust.identity-resolution-expiry-minutes", defaultValue = "10")
    identityResolutionExpiryMinutes: Long,
    @ConfigProperty(name = "app.organization-trust.identity-resolution-retention-hours", defaultValue = "24")
    identityResolutionRetentionHours: Long,
)
{
    val requestExpiry: Duration = positiveDuration(requestExpiryDays, "request expiry")
    val requestCooldown: Duration = nonNegativeDuration(requestCooldownDays, "request cooldown")
    val reviewPeriod: Duration = positiveDuration(reviewPeriodDays, "review period")
    val identityResolutionExpiry: Duration = positiveMinutes(
        identityResolutionExpiryMinutes,
        "identity resolution expiry",
    )
    val identityResolutionRetention: Duration = positiveHours(
        identityResolutionRetentionHours,
        "identity resolution retention",
    )

    private fun positiveDuration(days: Long, name: String): Duration
    {
        require(days > 0) { "Organization trust $name must be greater than zero days" }
        return Duration.ofDays(days)
    }

    private fun nonNegativeDuration(days: Long, name: String): Duration
    {
        require(days >= 0) { "Organization trust $name cannot be negative" }
        return Duration.ofDays(days)
    }

    private fun positiveMinutes(minutes: Long, name: String): Duration
    {
        require(minutes > 0) { "Organization trust $name must be greater than zero minutes" }
        return Duration.ofMinutes(minutes)
    }

    private fun positiveHours(hours: Long, name: String): Duration
    {
        require(hours > 0) { "Organization trust $name must be greater than zero hours" }
        return Duration.ofHours(hours)
    }
}
