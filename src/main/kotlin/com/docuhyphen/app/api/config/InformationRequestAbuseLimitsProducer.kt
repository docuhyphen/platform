package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

@ApplicationScoped
class InformationRequestAbuseLimitsProducer @Inject constructor(
    @ConfigProperty(name = "app.information-request.no-auth.challenges-per-minute", defaultValue = "10")
    private val noAuthChallengesPerMinute: Long,

    @ConfigProperty(name = "app.information-request.no-auth.sessions-per-minute", defaultValue = "20")
    private val noAuthSessionsPerMinute: Long,

    @ConfigProperty(name = "app.information-request.access-link.default-lifetime", defaultValue = "P30D")
    private val accessLinkLifetime: Duration,

    @ConfigProperty(name = "app.information-request.access-link.default-uses", defaultValue = "25")
    private val accessLinkUses: Int,

    @ConfigProperty(name = "app.information-request.reminder.cooldown", defaultValue = "PT24H")
    private val reminderCooldown: Duration,

    @ConfigProperty(name = "app.information-request.export.daily-ceiling", defaultValue = "100")
    private val exportDailyCeiling: Long,

    @ConfigProperty(name = "app.information-request.export.max-bytes", defaultValue = "26214400")
    private val exportMaxBytes: Long = 25L * 1024 * 1024,
)
{
    @Produces
    @Singleton
    fun abuseLimits(): InformationRequestAbuseLimits {
        requirePositive(noAuthChallengesPerMinute, "app.information-request.no-auth.challenges-per-minute")
        requirePositive(noAuthSessionsPerMinute, "app.information-request.no-auth.sessions-per-minute")
        requirePositive(accessLinkLifetime.toSeconds(), "app.information-request.access-link.default-lifetime")
        requirePositive(accessLinkUses.toLong(), "app.information-request.access-link.default-uses")
        require(!reminderCooldown.isNegative) { "app.information-request.reminder.cooldown must not be negative" }
        requirePositive(exportDailyCeiling, "app.information-request.export.daily-ceiling")
        requirePositive(exportMaxBytes, "app.information-request.export.max-bytes")
        return InformationRequestAbuseLimits(
            noAuthChallengesPerMinute = noAuthChallengesPerMinute,
            noAuthSessionsPerMinute = noAuthSessionsPerMinute,
            accessLinkLifetime = accessLinkLifetime,
            accessLinkUses = accessLinkUses,
            reminderCooldown = reminderCooldown,
            exportDailyCeiling = exportDailyCeiling,
            exportMaxBytes = exportMaxBytes,
        )
    }

    private fun requirePositive(value: Long, property: String) {
        require(value > 0) { "$property must be more than zero" }
    }
}
