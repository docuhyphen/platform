package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCapabilities
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestCapabilityService @Inject constructor(
    private val standingService: InformationRequestExecutionStandingService,
    private val subscriptionAccessService: SubscriptionAccessService,
    private val shareService: ShareService,
    private val requestRepository: InformationRequestRepository,
)
{
    fun forCaller(access: RequestAccessContext): InformationRequestCapabilities
    {
        val person = SubscriptionContext.forUser(access.principal.id)
        val organization = access.authorization.activeOrgId?.let(SubscriptionContext::forOrganization)
        val personal = standingService.ownerStanding(person)
        val scope = organization?.let(standingService::ownerStanding) ?: personal
        return InformationRequestCapabilities(
            scope = scope,
            typedAnswersAvailable = organization?.let(::organizationTypedAnswersAvailable) ?: true,
            personalTemplatesAvailable = personal.newWorkAvailable || (organization != null && scope.newWorkAvailable),
            assignedWork = shareService.holdsActiveShareOn(ResourceType.INFORMATION_REQUEST, access.principal),
            holdsRequests = organization
                ?.let { requestRepository.existsForOwner(InformationRequestOwnerType.ORGANIZATION, it.ownerId) }
                ?: requestRepository.existsForOwner(InformationRequestOwnerType.USER, access.principal.id),
        )
    }

    private fun organizationTypedAnswersAvailable(organization: SubscriptionContext): Boolean =
        !subscriptionAccessService.enforcementMode().refusesDeniedRequests ||
            subscriptionAccessService.isFeatureAvailable(organization, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
}
