package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestEntitlementSource
import com.docuhyphen.app.api.model.subscription.EffectiveSubscription
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.repository.informationrequest.execution.RequestExecutionGrantRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.subscription.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestExecutionGrantService @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val grantRepository: RequestExecutionGrantRepository,
    private val requirementBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val trialService: SubscriptionTrialService,
)
{
    fun findForRequest(requestId: UUID): RequestExecutionGrant? = grantRepository.findByRequestId(requestId)

    fun findForRequests(requestIds: Collection<UUID>): Map<UUID, RequestExecutionGrant> =
        grantRepository.findByRequestIds(requestIds).associateBy { it.requestId }

    fun committedEvidenceBytes(owner: SubscriptionContext): Long =
        grantRepository.committedEvidenceBytes(owner.ownerType, owner.ownerId)

    fun issueGrant(request: InformationRequest, exchange: Exchange): RequestExecutionGrant
    {
        grantRepository.findByRequestId(request.id)?.let { return it }

        val context = owner(exchange)
        val fieldBound = context.ownerType == SubscriptionOwnerType.ORGANIZATION &&
                hasFieldBoundRequirements(request.templateVersionId)
        if (fieldBound)
        {
            subscriptionAccessService.requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
        }
        val subscription = subscriptionAccessService.resolve(context)
        subscriptionAccessService.requireCommittedEvidenceCapacity(context) {
            grantRepository.committedEvidenceBytes(context.ownerType, context.ownerId)
        }
        val mode = subscriptionAccessService.enforcementMode()
        val limits = subscription.limits.takeIf { mode.refusesDeniedRequests }
        val now = Timestamp.from(Instant.now())
        val source = entitlementSourceOf(subscription, mode)

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
            enforcementMode = mode.name
            trialExpiresAt = subscription.currentPeriodEnd
                ?.takeIf { subscription.status == SubscriptionStatus.TRIALING }
                ?.let(Timestamp::from)
            mutationAllowanceExpiresAt = mutationAllowanceExpiry(subscription)
            actingPartyCap = limits?.maxActingPartiesPerInformationRequest
            evidenceFileAllowance = limits?.maxEvidenceFilesPerInformationRequest
            evidenceByteAllowance = limits?.maxEvidenceBytesPerInformationRequest
            policyVersion = PlanCatalog.POLICY_VERSION
            entitlementSource = source
            trialGrantId = if (source == InformationRequestEntitlementSource.TRIAL)
                trialService.activeTrialGrantId(context.ownerType, context.ownerId, now.toInstant())
            else null
            grantedFeatures = grantedFeaturesOf(context, subscription, fieldBound)
            continuationActions = InformationRequestContinuationAction.entries.map { it.name }.sorted().toTypedArray()
            issuedAt = now
            createdAt = now
        }

        return grantRepository.insertNow(grant)
    }

    private fun entitlementSourceOf(
        subscription: EffectiveSubscription,
        mode: SubscriptionEnforcementMode,
    ): InformationRequestEntitlementSource = when {
        !mode.refusesDeniedRequests -> InformationRequestEntitlementSource.UNENFORCED
        subscription.status == SubscriptionStatus.TRIALING -> InformationRequestEntitlementSource.TRIAL
        PlanCatalog.definitionOf(subscription.planCode).includes(PlanFeature.INFORMATION_REQUESTS) ->
            InformationRequestEntitlementSource.PAID

        else -> InformationRequestEntitlementSource.OVERRIDE
    }

    private fun grantedFeaturesOf(
        context: SubscriptionContext,
        subscription: EffectiveSubscription,
        fieldBound: Boolean,
    ): Array<String> {
        val typedAnswers = context.ownerType == SubscriptionOwnerType.USER || fieldBound ||
                subscription.hasFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
        return listOfNotNull(
            PlanFeature.INFORMATION_REQUESTS,
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS.takeIf { typedAnswers },
        ).map { it.name }.sorted().toTypedArray()
    }

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

    private fun hasFieldBoundRequirements(templateVersionId: UUID): Boolean =
        requirementBindingRepository.findOrdered(templateVersionId).any { it.collectedFieldDefinitionId != null }
}
