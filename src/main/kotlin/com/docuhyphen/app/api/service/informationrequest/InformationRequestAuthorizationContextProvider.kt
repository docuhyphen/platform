package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.model.informationrequest.InformationRequestParentPolicyFacts

@ApplicationScoped
class InformationRequestAuthorizationContextProvider @Inject constructor(
    private val repository: InformationRequestRepository,
    private val parentState: ExchangeLifecycleStateService,
) : ResourceAuthorizationContextProvider
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext? =
        repository.findById(resourceId)?.let { request ->
            ResourceAuthorizationContext(
                ownerContext = request.ownerContext() ?: return null,
                isArchived = request.state in terminalStates,
                parentRef = ResourceRef.exchange(request.exchangeId),
                policyFacts = InformationRequestParentPolicyFacts(parentState.snapshot(request.exchangeId) ?: return null),
            )
        }

    companion object
    {
        private val terminalStates = setOf(
            InformationRequestState.CLOSED,
            InformationRequestState.CANCELLED,
            InformationRequestState.SUPERSEDED,
            InformationRequestState.EXPIRED,
        )
    }
}

internal fun InformationRequest.ownerContext(): OwnerContext? = when (ownerType)
{
    InformationRequestOwnerType.ORGANIZATION ->
        ownerOrganizationId?.let(OwnerContext::Organization)
    InformationRequestOwnerType.USER ->
        ownerUserId?.let(OwnerContext::Personal)
}
