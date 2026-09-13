package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.SubscriptionDtoMapper
import com.docuhyphen.app.api.model.dto.EffectiveSubscriptionDto
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Builds the commercial part of the session contract.
 *
 * The paying subject follows the caller's context: the selected organization when one is
 * active, otherwise the authenticated user acting personally. Because this is recomputed on
 * every session fetch, switching organization immediately changes the plan the app presents.
 *
 * A resolution failure returns null rather than failing the session. The session is an identity
 * and capability contract first, and a missing commercial summary must never widen access or
 * lock a customer out of the application.
 *
 * The reported features are the ones the caller can actually reach, which excludes a capability
 * the owner is entitled to but that this deployment has not released to them. The client decides
 * what to offer from this list, so it must not name something every call site would refuse.
 */
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
                availableFeatures = subscriptionAccessService.availableFeatures(subscription),
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

