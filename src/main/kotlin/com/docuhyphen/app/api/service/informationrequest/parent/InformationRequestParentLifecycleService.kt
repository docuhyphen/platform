package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.noauth.RequestAccessSessionBinding
import com.docuhyphen.app.api.model.informationrequest.workflow.InformationRequestWorkflowTriggerCatalog
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockIssueRebaser
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockStopper
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestParentLifecycleService @Inject constructor(
    private val requests: InformationRequestRepository,
    private val sessions: RequestAccessSessionService,
    private val history: InformationRequestTransitionHistoryService,
    private val shares: com.docuhyphen.app.api.service.exchange.ShareService,
    private val bootstrapLinks: InformationRequestBootstrapShareLinkService,
    private val clockStopper: InformationRequestClockStopper,
    private val clockRebaser: InformationRequestClockIssueRebaser,
    private val workflows: WorkflowEngineService,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun apply(
        exchangeId: UUID,
        status: ExchangeStatus,
        deleted: Boolean,
        actor: InformationRequestAuditActor,
        previousStatus: ExchangeStatus? = null,
    )
    {
        if (!deleted && previousStatus == ExchangeStatus.INITIATED && status == ExchangeStatus.ACCEPTED_STARTED) {
            rebaseClocksOnAcceptance(exchangeId, actor)
            return
        }
        val effects =
            InformationRequestTransitionMatrix.parentEffects(InformationRequestParentSnapshot(status, deleted, true))
        if (effects.nonTerminalRequestEffect == InformationRequestNonTerminalEffect.NONE) return
        val cutoffReason = if (deleted) InformationRequestCredentialCutoff.PARENT_DELETED else "PARENT_${status.name}"
        requests.flushPendingChanges()
        requests.findForExchange(exchangeId).sortedBy { it.id }.forEach { candidate ->
            val request = requests.findRequestByIdForUpdate(candidate.id) ?: return@forEach
            shares.retainInformationRequestOwnerReads(exchangeId, request.id)
            if (effects.externalSessionEffect in setOf(
                    InformationRequestExternalSessionEffect.REVOKE_ALL,
                    InformationRequestExternalSessionEffect.REVOKE_NON_OWNER
                )
            ) {
                sessions.revokeLiveForRequest(request.id).forEach { revoked ->
                    recordSessionCutoff(request, revoked, actor, cutoffReason)
                }
                bootstrapLinks.revokeLinksKeepingSessions(
                    request,
                    InformationRequestCredentialCutoff(actor, cutoffReason)
                )
            }
            if (effects.nonTerminalRequestEffect == InformationRequestNonTerminalEffect.CANCEL && !request.state.isTerminal)
            {
                val fromState = request.state
                val now = Timestamp.from(Instant.now())
                request.state = InformationRequestState.CANCELLED
                request.cancelledAt = now
                request.updatedAt = now
                request.aggregateRevision += 1
                requests.update(request)
                history.record(
                    InformationRequestTransitionHistoryCommand(
                        request, fromState, request.state,
                        InformationRequestMutation.CANCEL, actor, reasonCode = cutoffReason,
                        requireAuditCapture = true,
                    )
                )
                clockStopper.stopForFinishedRequest(
                    request,
                    actor.principal,
                    now.toInstant(),
                    InformationRequestClockStopper.PARENT_FINISHED,
                )
            }
            if (effects.nonTerminalRequestEffect == InformationRequestNonTerminalEffect.CANCEL) {
                workflows.cancelActiveForSubject(
                    InformationRequestWorkflowTriggerCatalog.SUBJECT_RESOURCE_TYPE,
                    request.id,
                    cutoffReason
                )
            }
        }
    }

    private fun rebaseClocksOnAcceptance(exchangeId: UUID, actor: InformationRequestAuditActor) {
        val acceptedAt = Instant.now()
        requests.flushPendingChanges()
        requests.findForExchange(exchangeId).sortedBy { it.id }.forEach { candidate ->
            val request = requests.findRequestByIdForUpdate(candidate.id) ?: return@forEach
            if (request.state != InformationRequestState.DRAFT && !request.state.isTerminal) {
                clockRebaser.rebaseOnAcceptance(request, acceptedAt, actor.principal)
            }
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun enforceRecipientSignIn(exchangeId: UUID, actor: InformationRequestAuditActor) {
        val cutoff = InformationRequestCredentialCutoff(
            actor = actor,
            reasonCode = InformationRequestCredentialCutoff.RECIPIENT_SIGN_IN_REQUIRED,
        )
        requests.flushPendingChanges()
        requests.findForExchange(exchangeId).sortedBy { it.id }.forEach { candidate ->
            val request = requests.findRequestByIdForUpdate(candidate.id) ?: return@forEach
            bootstrapLinks.revokeAllForRequest(request, cutoff)
        }
    }

    private fun recordSessionCutoff(
        request: InformationRequest,
        revoked: RequestAccessSessionBinding,
        actor: InformationRequestAuditActor,
        reasonCode: String,
    ) {
        val mutation = InformationRequestMutation.REVOKE_ACCESS_SESSION
        history.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                reasonCode = reasonCode,
                partyId = revoked.partyId,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${revoked.session.id}",
                details = linkedMapOf(
                    "accessSessionId" to revoked.session.id.toString(),
                    "shareLinkId" to revoked.session.shareLinkId.toString(),
                ),
                requireAuditCapture = true,
            ),
        )
    }
}
