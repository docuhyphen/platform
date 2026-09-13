package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.*

object InformationRequestParentPolicy
{
    val readActions = setOf(Action.INFORMATION_REQUEST_VIEW, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
        Action.INFORMATION_REQUEST_EVIDENCE_VIEW)

    fun evaluate(request: ResourcePolicyRequest, parent: InformationRequestParentSnapshot): ResourcePolicyOutcome
    {
        val owner = request.resourceContext.ownerContext
        val isOwner = Capability.INFORMATION_REQUEST_ADMIN in request.capabilities ||
            Capability.EXCHANGE_ADMIN in request.capabilities ||
            (owner is OwnerContext.Personal && request.principal.kind == PrincipalKind.USER &&
                owner.userId == request.principal.id)
        val decision = if (request.action in readActions)
            InformationRequestTransitionMatrix.canRead(parent,
                if (isOwner) InformationRequestReadActor.OWNER else InformationRequestReadActor.PARTY)
        else if (parent.deleted || parent.status !in setOf(ExchangeStatus.INITIATED, ExchangeStatus.ACCEPTED_STARTED))
            InformationRequestPolicyDecision.Deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        else InformationRequestPolicyDecision.Allow()
        return when (decision)
        {
            is InformationRequestPolicyDecision.Allow -> ResourcePolicyOutcome.Permit()
            is InformationRequestPolicyDecision.Deny -> ResourcePolicyOutcome.Deny(decision.reasonCode,
                "Parent Exchange does not permit this Information Request action")
        }
    }
}
