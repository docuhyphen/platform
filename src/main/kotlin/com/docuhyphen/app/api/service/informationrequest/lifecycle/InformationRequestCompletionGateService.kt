package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
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
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestCompletionGateService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun change(command: ChangeInformationRequestCompletionGateCommand): InformationRequestLifecycleResult
    {
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(OPERATION, command.requestId, command.gatesExchangeClosure).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                gate.requireMutation(locked, InformationRequestMutation.CHANGE_COMPLETION_GATE)
                command.precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(locked.request))
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_CONFIGURE_COMPLETION), command.requestId)
                val request = locked.request
                if (request.gatesExchangeClosure != command.gatesExchangeClosure)
                {
                    request.gatesExchangeClosure = command.gatesExchangeClosure
                    request.aggregateRevision += 1
                    request.updatedAt = Timestamp.from(clock.instant())
                    requestRepository.update(request)
                    transitionHistory.record(
                        InformationRequestTransitionHistoryCommand(
                            request = request,
                            fromState = request.state,
                            toState = request.state,
                            mutation = InformationRequestMutation.CHANGE_COMPLETION_GATE,
                            actor = command.access.principal,
                            idempotencyKey = "information_request.completion_gate|${request.id}|${command.idempotencyKey}",
                            details = mapOf("gatesExchangeClosure" to command.gatesExchangeClosure.toString()),
                        ),
                    )
                }
                val etag = InformationRequestETag.aggregateOf(request)
                CommandMutationResult(
                    InformationRequestLifecycleResult(request, etag),
                    CommandResultReference(ResourceType.INFORMATION_REQUEST, request.id, request.aggregateRevision, etag),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_CONFIGURE_COMPLETION), command.requestId)
                InformationRequestLifecycleResult(
                    locked.request,
                    requireNotNull(decision.result.etag) { "A completion gate receipt records the request ETag" },
                )
            }
        }
    }

    private companion object
    {
        const val OPERATION = "change-information-request-completion-gate"
    }
}
