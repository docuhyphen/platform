package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAuthorActionService @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val parentState: ExchangeLifecycleStateService,
) {
    fun actionsFor(request: InformationRequest, access: RequestAccessContext): InformationRequestAuthorActions {
        val parent = parentState.snapshot(request.exchangeId)?.copy(lockedForUpdate = true)
            ?: return InformationRequestAuthorActions()
        val decisions = mutableMapOf<Action, Boolean>()
        val permits = { action: Action ->
            decisions.getOrPut(action) {
                authorizationService.authorize(
                    access.principal,
                    action,
                    ResourceRef.informationRequest(request.id),
                    access.authorization,
                ) is Decision.Allow
            }
        }
        val allows = { action: Action, mutation: InformationRequestMutation ->
            permits(action) && transitionAllowed(parent, request, mutation)
        }
        val manageParties = allows(Action.INFORMATION_REQUEST_MANAGE_PARTIES, InformationRequestMutation.ASSIGN_PARTY)
        return InformationRequestAuthorActions(
            issue = allows(Action.INFORMATION_REQUEST_ISSUE, InformationRequestMutation.ISSUE),
            cancel = allows(Action.INFORMATION_REQUEST_CANCEL, InformationRequestMutation.CANCEL),
            supersede = allows(Action.INFORMATION_REQUEST_SUPERSEDE, InformationRequestMutation.SUPERSEDE),
            changeCompletionGate = allows(
                Action.INFORMATION_REQUEST_CONFIGURE_COMPLETION,
                InformationRequestMutation.CHANGE_COMPLETION_GATE,
            ),
            manageParties = manageParties,
            previewRecipients = permits(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
            manageClocks = allows(Action.INFORMATION_REQUEST_MANAGE_CLOCKS, InformationRequestMutation.START_CLOCK),
            scheduleFollowUps = allows(
                Action.INFORMATION_REQUEST_SUPERSEDE,
                InformationRequestMutation.SCHEDULE_FOLLOW_UP
            ),
            createFollowUps = allows(Action.INFORMATION_REQUEST_SUPERSEDE, InformationRequestMutation.CREATE_SUCCESSOR),
            promoteFacts = allows(Action.INFORMATION_REQUEST_PROMOTE_FACT, InformationRequestMutation.PROMOTE_FACT),
            revokeFacts = allows(Action.INFORMATION_REQUEST_REVOKE_FACT, InformationRequestMutation.REVOKE_FACT),
            recordDecisions = allows(
                Action.INFORMATION_REQUEST_RECORD_DECISION,
                InformationRequestMutation.RECORD_BUSINESS_DECISION,
            ),
            issueAccessLinks = manageParties && !parent.requireRecipientSignIn,
        )
    }

    fun createsFollowUp(request: InformationRequest, access: RequestAccessContext): Boolean {
        val parent = parentState.snapshot(request.exchangeId)?.copy(lockedForUpdate = true) ?: return false
        return authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_SUPERSEDE,
            ResourceRef.informationRequest(request.id),
            access.authorization,
        ) is Decision.Allow && transitionAllowed(parent, request, InformationRequestMutation.CREATE_SUCCESSOR)
    }

    private fun transitionAllowed(
        parent: InformationRequestParentSnapshot,
        request: InformationRequest,
        mutation: InformationRequestMutation,
    ): Boolean =
        InformationRequestTransitionMatrix.canMutate(
            parent,
            request.state,
            mutation
        ) is InformationRequestPolicyDecision.Allow
}
