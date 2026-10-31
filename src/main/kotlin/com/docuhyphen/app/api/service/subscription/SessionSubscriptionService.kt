package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.SubscriptionDtoMapper
import com.docuhyphen.app.api.model.dto.EffectiveSubscriptionDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class SessionSubscriptionService @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SessionSubscriptionService::class.java)
    }

    fun describe(appUserId: UUID, activeOrganizationId: UUID?): EffectiveSubscriptionDto?
    {
        return runCatching {
            val subscription = subscriptionAccessService.resolveForSession(appUserId, activeOrganizationId)
            val usage = subscriptionAccessService.measureUsage(subscription)
            SubscriptionDtoMapper.toDto(
                subscription = subscription,
                usage = usage,
                enforcementMode = subscriptionAccessService.enforcementMode(),
            )
        }.getOrElse { failure ->
            logger.error(
                "event=subscription_resolution_failure source=session appUserId={} activeOrganizationId={} failureType={}",
                appUserId,
                activeOrganizationId,
                failure.javaClass.simpleName,
                failure,
            )
            null
        }
    }
}

