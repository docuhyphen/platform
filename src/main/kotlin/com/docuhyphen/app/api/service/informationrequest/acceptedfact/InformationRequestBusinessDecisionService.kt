package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecision
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestBusinessDecisionResult
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestBusinessDecisionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestBusinessDecisionService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val queryService: InformationRequestQueryService,
    private val decisionRepository: InformationRequestBusinessDecisionRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    fun decisions(requestId: UUID, access: RequestAccessContext): List<InformationRequestBusinessDecision>
    {
        queryService.findById(requestId, access)
        return decisionRepository.findForRequest(requestId)
    }

    @Transactional
    fun record(command: RecordInformationRequestBusinessDecisionCommand): InformationRequestBusinessDecisionResult
    {
        val locked = gate.lock(command.requestId)
        val process = command.owningProcessKey.trim().lowercase()
        val outcome = command.outcomeCode.trim().lowercase()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    OPERATION,
                    process,
                    outcome,
                    command.kind,
                    command.priorDecisionId ?: "",
                    command.reasonReference?.trim().orEmpty(),
                    command.externalReference?.trim().orEmpty(),
                    command.decidedAt.toEpochMilli(),
                ).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val recorded = recordDecision(locked, command, process, outcome)
                CommandMutationResult(
                    recorded,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_BUSINESS_DECISION, recorded.id, recorded.decisionRevision.toLong(), null),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> result(locked, decision.response)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_VIEW), command.requestId)
                val recorded = decisionRepository.findById(decision.result.resourceId)
                    ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Business decision not found")
                result(locked, recorded)
            }
        }
    }

    private fun recordDecision(
        locked: LockedInformationRequest,
        command: RecordInformationRequestBusinessDecisionCommand,
        process: String,
        outcome: String,
    ): InformationRequestBusinessDecision
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.RECORD_BUSINESS_DECISION)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_RECORD_DECISION), request.id)
        if (!KEY.matches(process) || !KEY.matches(outcome))
        {
            throw InformationRequestCommandRequestException("A business decision names its process and outcome by lowercase machine keys")
        }
        if (command.decidedAt.isAfter(clock.instant()))
        {
            throw InformationRequestCommandRequestException("A business decision records when it was made, which cannot be in the future")
        }
        val chain = decisionRepository.findForRequest(request.id).filter { it.owningProcessKey == process }
        val latest = chain.maxByOrNull { it.decisionRevision }
        when (command.kind)
        {
            InformationRequestBusinessDecisionKind.ORIGINAL -> if (latest != null)
            {
                refuse("This process already has a decision; a later one reconsiders or appeals it")
            }
            InformationRequestBusinessDecisionKind.RECONSIDERATION,
            InformationRequestBusinessDecisionKind.APPEAL,
            -> if (latest == null || command.priorDecisionId != latest.id)
            {
                refuse("A reconsideration or appeal names the latest decision of its own process")
            }
        }
        val now = Timestamp.from(clock.instant())
        val recorded = decisionRepository.save(
            InformationRequestBusinessDecision().apply {
                informationRequestId = request.id
                owningProcessKey = process
                outcomeCode = outcome
                reasonReference = command.reasonReference?.trim()?.ifBlank { null }
                externalReference = command.externalReference?.trim()?.ifBlank { null }
                kind = command.kind
                priorDecisionId = command.priorDecisionId.takeIf { command.kind != InformationRequestBusinessDecisionKind.ORIGINAL }
                decisionRevision = (latest?.decisionRevision ?: 0) + 1
                decidedAt = Timestamp.from(command.decidedAt)
                recordedByPrincipalKind = command.access.principal.kind
                recordedByPrincipalId = command.access.principal.id
                recordedAt = now
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.RECORD_BUSINESS_DECISION,
                actor = command.access.principal,
                idempotencyKey = "information_request.business_decision|${recorded.id}|${command.idempotencyKey}",
                details = mapOf(
                    "businessDecisionId" to recorded.id.toString(),
                    "owningProcessKey" to process,
                    "decisionKind" to recorded.kind.name,
                    "decisionRevision" to recorded.decisionRevision.toString(),
                ) + (recorded.priorDecisionId?.let { mapOf("priorDecisionId" to it.toString()) } ?: emptyMap()),
            ),
        )
        return recorded
    }

    private fun result(locked: LockedInformationRequest, decision: InformationRequestBusinessDecision) =
        InformationRequestBusinessDecisionResult(decision, InformationRequestETag.aggregateOf(locked.request))

    private fun refuse(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.BUSINESS_DECISION_PRIOR_INVALID, message)

    private companion object
    {
        const val OPERATION = "record-information-request-business-decision"
        val KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    }
}
