package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeKind
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReminderResult
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.MAXIMUM_REMINDER_REQUESTS
import com.docuhyphen.app.api.model.informationrequest.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class InformationRequestReminderService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val requestRepository: InformationRequestRepository,
    private val gate: InformationRequestMutationGate,
    private val partyRepository: InformationRequestPartyRepository,
    private val intentRepository: InformationRequestNoticeIntentRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun send(command: SendInformationRequestRemindersCommand): List<InformationRequestReminderResult>
    {
        val owner = ownerAccess.currentOwner()
        val actor = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)
        val requestIds = command.requestIds.distinct()
        if (requestIds.isEmpty()) throw InformationRequestCommandRequestException("Name at least one Information Request")
        if (requestIds.size > MAXIMUM_REMINDER_REQUESTS)
        {
            throw InformationRequestCommandRequestException("At most $MAXIMUM_REMINDER_REQUESTS Information Requests are reminded at once")
        }
        requestIds.forEach { requireOwned(it, owner) }
        return requestIds.sorted()
            .map { gate.lock(it) }
            .map { remind(it, actor, command.idempotencyKey) }
    }

    private fun requireOwned(requestId: UUID, owner: InformationRequestOwnerRef)
    {
        val request = requestRepository.findById(requestId)
        val ownerId = request?.ownerOrganizationId ?: request?.ownerUserId
        if (request == null || request.ownerType != owner.ownerType || ownerId != owner.ownerId)
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found")
        }
    }

    private fun remind(locked: LockedInformationRequest, actor: PrincipalRef, idempotencyKey: String): InformationRequestReminderResult
    {
        val request = locked.request
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(request.id),
            operation = SEND_OPERATION,
            actor = CommandActorRef.principal(actor),
            idempotencyKey = idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex("$SEND_OPERATION|${request.id}"),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                gate.requireMutation(locked, InformationRequestMutation.SEND_REMINDER)
                val transition = transitionHistory.record(
                    InformationRequestTransitionHistoryCommand(
                        request = request,
                        fromState = request.state,
                        toState = request.state,
                        mutation = InformationRequestMutation.SEND_REMINDER,
                        actor = actor,
                        idempotencyKey = "information_request.remind|${request.id}|$idempotencyKey",
                    ),
                )
                val parties = partyRepository.findActiveForRequest(request.id)
                    .filter { it.roleKey in RESPONDING_ROLES && it.principalKind != null && it.principalId != null }
                parties.forEach { party ->
                    intentRepository.save(
                        InformationRequestNoticeIntent().apply {
                            informationRequestId = request.id
                            transitionId = transition.id
                            partyId = party.id
                            noticeKind = InformationRequestNoticeKind.RESPONSE_REMINDER
                        },
                    )
                }
                CommandMutationResult(
                    InformationRequestReminderResult(request.id, parties.size),
                    CommandResultReference(ResourceType.INFORMATION_REQUEST, request.id, revision = parties.size.toLong()),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
                InformationRequestReminderResult(request.id, requireNotNull(decision.result.revision).toInt())
        }
    }

    private companion object
    {
        const val SEND_OPERATION = "send-information-request-reminder"
        val RESPONDING_ROLES = setOf(
            InformationRequestShareRoleKey.SUBJECT,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
    }
}
