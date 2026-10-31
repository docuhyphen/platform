package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilities
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestCallerPartyResolver
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestCapabilityService @Inject constructor(
    private val standingService: InformationRequestExecutionStandingService,
    private val subscriptionAccessService: SubscriptionAccessService,
    private val callerParties: InformationRequestCallerPartyResolver,
    private val requestRepository: InformationRequestRepository,
)
{
    fun forCaller(access: RequestAccessContext): InformationRequestCapabilities
    {
        val person = SubscriptionContext.forUser(access.principal.id)
        val organization = access.authorization.activeOrgId?.let(SubscriptionContext::forOrganization)
        val personal = standingService.ownerStanding(person)
        val scope = organization?.let(standingService::ownerStanding) ?: personal
        val creation = standingService.creationStanding(organization ?: person)
        return InformationRequestCapabilities(
            scope = scope,
            quotas = creation.quotas,
            newWorkUnavailableReason = creation.unavailableReason,
            typedAnswersAvailable = organization?.let(::organizationTypedAnswersAvailable) ?: true,
            personalTemplatesAvailable = personal.newWorkAvailable || (organization != null && scope.newWorkAvailable),
            assignedWork = callerParties.activePartiesActedFor(access.principal).any { party ->
                requestRepository.findById(party.informationRequestId)?.state?.isTerminal == false
            },
            holdsRequests = organization
                ?.let { requestRepository.existsForOwner(InformationRequestOwnerType.ORGANIZATION, it.ownerId) }
                ?: requestRepository.existsForOwner(InformationRequestOwnerType.USER, access.principal.id),
        )
    }

    private fun organizationTypedAnswersAvailable(organization: SubscriptionContext): Boolean =
        !subscriptionAccessService.enforcementMode().refusesDeniedRequests ||
                subscriptionAccessService.isFeatureAvailable(organization, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
}
