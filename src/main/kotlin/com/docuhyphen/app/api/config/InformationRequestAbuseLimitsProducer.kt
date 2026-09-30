package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.model.informationrequest.InformationRequestAbuseLimits
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
)
{
    @Produces
    @Singleton
    fun abuseLimits(): InformationRequestAbuseLimits =
        InformationRequestAbuseLimits(
            noAuthChallengesPerMinute = noAuthChallengesPerMinute,
            noAuthSessionsPerMinute = noAuthSessionsPerMinute,
            accessLinkLifetime = accessLinkLifetime,
            accessLinkUses = accessLinkUses,
            reminderCooldown = reminderCooldown,
            exportDailyCeiling = exportDailyCeiling,
        )
}
