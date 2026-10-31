package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestReadActor
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix

object InformationRequestParentPolicy
{
    val readActions = setOf(
        Action.INFORMATION_REQUEST_VIEW,
        Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
        Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
        Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
        Action.INFORMATION_REQUEST_VIEW_REVIEWS,
        Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
        Action.INFORMATION_REQUEST_EXPORT,
        Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
    )

    private val exportActions = setOf(Action.INFORMATION_REQUEST_EXPORT, Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT)

    private val retainedRecordActions = setOf(Action.INFORMATION_REQUEST_REVOKE_FACT)

    val closedRecordActions = retainedRecordActions + setOf(
        Action.INFORMATION_REQUEST_PROMOTE_FACT, Action.INFORMATION_REQUEST_RECORD_DECISION,
        Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES, Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES,
        Action.INFORMATION_REQUEST_SUPERSEDE, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT,
    )

    fun evaluate(request: ResourcePolicyRequest, parent: InformationRequestParentSnapshot): ResourcePolicyOutcome
    {
        val owner = request.resourceContext.ownerContext
        val isOwner = Capability.INFORMATION_REQUEST_ADMIN in request.capabilities ||
                Capability.EXCHANGE_ADMIN in request.capabilities ||
                Capability.INFORMATION_REQUEST_RETAINED_OWNER_READ in request.capabilities ||
                (request.action in exportActions && Capability.INFORMATION_REQUEST_EXPORT in request.capabilities) ||
                (owner is OwnerContext.Personal && request.principal.kind == PrincipalKind.USER &&
                        owner.userId == request.principal.id)
        val decision = if (request.action in readActions)
            InformationRequestTransitionMatrix.canRead(
                parent,
                if (isOwner) InformationRequestReadActor.OWNER else InformationRequestReadActor.PARTY
            )
        else if (parent.deleted)
            InformationRequestPolicyDecision.Deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        else if (parent.status !in setOf(ExchangeStatus.INITIATED, ExchangeStatus.ACCEPTED_STARTED) &&
            !(isOwner && request.action in retainedRecordActions)
        )
            InformationRequestPolicyDecision.Deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        else InformationRequestPolicyDecision.Allow()
        return when (decision)
        {
            is InformationRequestPolicyDecision.Allow -> ResourcePolicyOutcome.Permit()
            is InformationRequestPolicyDecision.Deny -> ResourcePolicyOutcome.Deny(
                decision.reasonCode,
                "Parent Exchange does not permit this Information Request action"
            )
        }
    }
}
