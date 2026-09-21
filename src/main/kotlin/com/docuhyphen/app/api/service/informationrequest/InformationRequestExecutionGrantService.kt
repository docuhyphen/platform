package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionGrantRepository
import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Freezes the commercial position an Information Request is issued under.
 *
 * Issuance is the one moment the owner's live plan is allowed to decide anything about a request:
 * once [issueGrant] has run, every later continuation of that same request answers to the grant it
 * wrote, never to the owner's plan as it happens to stand later. This is what lets a paid lapse or
 * trial expiry block new or expanding work while an already-issued request still lets its assigned
 * respondents and reviewers finish within what was already granted. A Template that records a typed
 * Field answer on an organization-owned request also requires the Business Fields feature at this
 * same moment, since Field writes against an issued request answer to this grant rather than to a
 * live Fields subscription check. Personal Information Requests include their typed response data.
 */
@ApplicationScoped
class InformationRequestExecutionGrantService @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val grantRepository: RequestExecutionGrantRepository,
    private val requirementBindingRepository: InformationRequestTemplateRequirementBindingRepository,
)
{
    /** The frozen grant issued for a request, or null before it has ever been issued. */
    fun findForRequest(requestId: UUID): RequestExecutionGrant? = grantRepository.findByRequestId(requestId)

    /**
     * Idempotent: a request already holding a grant keeps its original one rather than being
     * re-priced against whatever the owner's plan has since become.
     */
    fun issueGrant(request: InformationRequest, exchange: Exchange): RequestExecutionGrant
    {
        grantRepository.findByRequestId(request.id)?.let { return it }

        val context = owner(exchange)
        if (context.ownerType == SubscriptionOwnerType.ORGANIZATION &&
            hasFieldBoundRequirements(request.templateVersionId))
        {
            subscriptionAccessService.requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
        }
        val subscription = subscriptionAccessService.resolve(context)
        val now = Timestamp.from(Instant.now())

        val grant = RequestExecutionGrant().apply {
            requestId = request.id
            ownerType = context.ownerType.name
            when (context.ownerType)
            {
                SubscriptionOwnerType.ORGANIZATION -> ownerOrganizationId = context.ownerId
                SubscriptionOwnerType.USER -> ownerUserId = context.ownerId
            }
            planCode = subscription.planCode.name
            subscriptionStatus = subscription.status.name
            enforcementMode = subscriptionAccessService.enforcementMode().name
            trialExpiresAt = subscription.currentPeriodEnd
                ?.takeIf { subscription.status == SubscriptionStatus.TRIALING }
                ?.let(Timestamp::from)
            mutationAllowanceExpiresAt = mutationAllowanceExpiry(subscription)
            additionalRecipientCap = subscription.limits.maxAdditionalParticipantsPerExchange
            issuedAt = now
            createdAt = now
        }

        return grantRepository.save(grant)
    }

    /**
     * Explicitly revokes an already-issued grant, distinct from [issueGrant]'s own idempotence: a
     * request with no grant at all cannot be revoked, since there is nothing yet for a
     * continuation mutation to answer to. Idempotent by first revocation -- a request revoked
     * twice keeps its original reason and timestamp rather than overwriting the record of when
     * and why it actually happened.
     */
    fun revoke(requestId: UUID, reason: String): RequestExecutionGrant
    {
        val grant = grantRepository.findByRequestId(requestId)
            ?: throw IllegalStateException("No execution grant issued for request $requestId to revoke")
        if (grant.revokedAt == null)
        {
            grant.revokedAt = Timestamp.from(Instant.now())
            grant.revokedReason = reason
            grantRepository.update(grant)
        }
        return grant
    }

    private fun mutationAllowanceExpiry(subscription: EffectiveSubscription): Timestamp? = when
    {
        subscription.status == SubscriptionStatus.TRIALING -> subscription.currentPeriodEnd
        subscription.status.permitsMutationsWhileInGracePeriod -> subscription.gracePeriodEnd
        subscription.status == SubscriptionStatus.CANCELED -> subscription.currentPeriodEnd
        else -> null
    }?.let(Timestamp::from)

    private fun owner(exchange: Exchange): SubscriptionContext
    {
        exchange.ownerOrganizationId?.let { return SubscriptionContext.forOrganization(it) }
        exchange.ownerUserId?.let { return SubscriptionContext.forUser(it) }
        throw IllegalStateException("Exchange ${exchange.id} has no owner to freeze an execution grant for")
    }

    /** True when at least one Requirement in this Template Version records a typed Field answer. */
    private fun hasFieldBoundRequirements(templateVersionId: UUID): Boolean =
        requirementBindingRepository.findOrdered(templateVersionId).any { it.collectedFieldDefinitionId != null }
}
