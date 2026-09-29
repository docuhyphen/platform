package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCallerStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNextAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSummaryPermissions
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestCallerStandingService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val gate: InformationRequestMutationGate,
    private val lockService: InformationRequestSubmissionLockService,
    private val stages: InformationRequestSubmissionStages,
)
{
    fun standingOf(
        request: InformationRequest,
        access: RequestAccessContext,
        reviewAwaitedRequestIds: Set<UUID>,
    ): InformationRequestCallerStanding
    {
        val roles = partyRepository.findActiveForRequest(request.id)
            .filter { access.principal in requirementContext.principalsActingFor(it) }
            .map { it.roleKey }
            .distinct()
            .sorted()
        val permissions = InformationRequestSummaryPermissions(
            canManage = gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_PARTIES, request.id),
            canRespond = gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUBMIT, request.id) ||
                roles.any { it in ATTESTING_ROLES },
            canReview = gate.permitsRequest(access, Action.INFORMATION_REQUEST_REVIEW, request.id),
        )
        return InformationRequestCallerStanding(
            roles = roles,
            permissions = permissions,
            nextAction = nextActionOf(request, permissions, request.id in reviewAwaitedRequestIds),
        )
    }

    private fun nextActionOf(
        request: InformationRequest,
        permissions: InformationRequestSummaryPermissions,
        reviewAwaited: Boolean,
    ): InformationRequestNextAction =
        when
        {
            request.state.isTerminal -> InformationRequestNextAction.VIEW
            request.state == InformationRequestState.DRAFT ->
                if (permissions.canManage) InformationRequestNextAction.COMPLETE_SETUP else InformationRequestNextAction.VIEW
            permissions.canReview && reviewAwaited -> InformationRequestNextAction.REVIEW
            permissions.canRespond && awaitsResponse(request) -> InformationRequestNextAction.RESPOND
            permissions.canManage -> InformationRequestNextAction.MANAGE
            else -> InformationRequestNextAction.VIEW
        }

    private fun awaitsResponse(request: InformationRequest): Boolean
    {
        if (lockService.openCorrections(request.id).isNotEmpty()) return true
        val submitted = lockService.submittedStages(request.id)
        val stageOrder = stages.stageOrder(request)
        return if (stageOrder.isEmpty()) null !in submitted else !submitted.containsAll(stageOrder)
    }

    private companion object
    {
        val ATTESTING_ROLES = setOf(InformationRequestShareRoleKey.SUBJECT, InformationRequestShareRoleKey.ATTESTOR)
    }
}
