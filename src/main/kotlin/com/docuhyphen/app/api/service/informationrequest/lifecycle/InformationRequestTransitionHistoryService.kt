package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.CommandReceiptReference
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.InformationRequestTransitionActorKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestTransitionHistoryService @Inject constructor(
    private val transitionRepository: InformationRequestTransitionRepository,
    private val auditRecorder: AuditRecorder,
    @TransactionalEventSink private val domainEventPublisher: DomainEventPublisher,
    private val responseStart: InformationRequestResponseStart,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val receiptScope: CommandReceiptScope,
)
{
    fun latestOccurrence(requestId: UUID, mutation: InformationRequestMutation): Instant? =
        transitionRepository.latestOccurrence(requestId, mutation)?.toInstant()

    fun record(command: InformationRequestTransitionHistoryCommand): InformationRequestTransition
    {
        if (!responseStart.startsWith(command)) return recordOne(command)
        val previousState = command.request.state
        responseStart.start(command.request)
        recordOne(
            InformationRequestTransitionHistoryCommand(
                request = command.request,
                fromState = previousState,
                toState = command.request.state,
                mutation = InformationRequestMutation.START_RESPONSE,
                actor = command.actor,
                partyId = command.partyId,
                auditIdempotencyKey = "information_request.start|${command.request.id}",
            ),
        )
        val sameState = command.fromState == previousState && command.toState == previousState
        return recordOne(
            if (sameState) command.copy(
                fromState = command.request.state,
                toState = command.request.state
            )
            else command,
        )
    }

    private fun recordOne(command: InformationRequestTransitionHistoryCommand): InformationRequestTransition
    {
        val occurredAt = Timestamp.from(Instant.now())
        val receipt = receiptScope.current()
        val sequence = transitionRepository.nextSequenceNumber(command.request.id)
        val transition = transitionRepository.save(
            InformationRequestTransition().apply {
                informationRequestId = command.request.id
                sequenceNumber = sequence
                fromState = command.fromState
                toState = command.toState
                mutation = command.mutation
                actorKind = transitionActorKindOf(command.actor.principal.kind)
                actorId = command.actor.principal.id
                reasonCode = command.reasonCode?.trim()?.ifBlank { null }
                partyId = command.partyId
                commandReceiptId = receipt?.receiptId
                idempotencyKey = receipt?.idempotencyKey
                    ?: systemIdempotencyKey(command.mutation, command.request.id, sequence)
                this.occurredAt = occurredAt
            },
        )

        val eventType = auditEventTypeFor(command.mutation) ?: return transition
        val owner = informationRequestAuditOwner(command.request)
        val eventId = UUID.randomUUID()
        val payload = buildPayload(command, transition)
        val attribution = actorLabels.attribute(command.actor)
        val capture = auditRecorder.record(
            AuditEventDraft(
                owner = owner,
                eventTypeKey = eventType.key,
                outcome = command.auditOutcome,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = "INFORMATION_REQUEST",
                targetId = command.request.id.toString(),
                reason = command.reasonCode?.trim()?.ifBlank { null },
                payload = payload,
                eventId = eventId,
                idempotencyKey = auditIdempotencyKeyOf(eventType, receipt, transition.id, command.auditIdempotencyKey),
                businessTransactionId = transition.id.toString(),
            ),
        )
        if (command.requireAuditCapture && capture !is AuditCaptureResult.Captured) {
            throw AuditCaptureFailedException("Required Information Request audit capture failed", null)
        }
        domainEventPublisher.publish(
            DomainEvent(
                id = eventId.toString(),
                type = eventType.key,
                actor = DomainEvent.PrincipalRefDto.from(command.actor.principal),
                subject = DomainEvent.SubjectRef("INFORMATION_REQUEST", command.request.id.toString()),
                organizationId = command.request.ownerOrganizationId?.toString(),
                payload = payload,
                orderingKey = orderingKeyOf(command.request.id),
            ),
            owner,
        )
        return transition
    }

    private fun buildPayload(
        command: InformationRequestTransitionHistoryCommand,
        transition: InformationRequestTransition,
    ): Map<String, String>
    {
        val fields = linkedMapOf(
            "transitionId" to transition.id.toString(),
            "sequenceNumber" to transition.sequenceNumber.toString(),
            "toState" to command.toState.name,
            "mutation" to command.mutation.name,
            "exchangeId" to command.request.exchangeId.toString(),
            "templateVersionId" to command.request.templateVersionId.toString(),
        )
        command.fromState?.let { fields["fromState"] = it.name }
        command.request.supersededByRequestId?.let { fields["supersededByRequestId"] = it.toString() }
        command.reasonCode?.trim()?.ifBlank { null }?.let { fields["reasonCode"] = it }
        command.partyId?.let { fields["partyId"] = it.toString() }
        command.evidence?.let { evidence ->
            fields["requirementId"] = evidence.requirementId.toString()
            fields["evidenceArtifactId"] = evidence.artifactId.toString()
            fields["evidenceAction"] = evidence.action.name
            evidence.versionNumber?.let { fields["evidenceVersionNumber"] = it.toString() }
        }
        command.details.forEach { (key, value) -> fields.putIfAbsent(key, value) }
        return fields
    }

    private fun transitionActorKindOf(kind: PrincipalKind): InformationRequestTransitionActorKind = when (kind)
    {
        PrincipalKind.USER -> InformationRequestTransitionActorKind.USER
        PrincipalKind.PARTICIPANT -> InformationRequestTransitionActorKind.PARTICIPANT
        PrincipalKind.PRINCIPAL_GROUP -> InformationRequestTransitionActorKind.PRINCIPAL_GROUP
        PrincipalKind.ORGANIZATION -> InformationRequestTransitionActorKind.ORGANIZATION
        PrincipalKind.APPLICATION -> InformationRequestTransitionActorKind.APPLICATION
        PrincipalKind.SERVICE_ACCOUNT -> InformationRequestTransitionActorKind.SERVICE_ACCOUNT
        PrincipalKind.PUBLIC_LINK -> InformationRequestTransitionActorKind.PUBLIC_LINK
    }

    companion object
    {
        fun orderingKeyOf(requestId: UUID): String = "information_request:$requestId"

        fun systemIdempotencyKey(mutation: InformationRequestMutation, requestId: UUID, sequenceNumber: Int): String =
            "system|${mutation.name.lowercase()}|$requestId|$sequenceNumber"

        fun auditIdempotencyKeyOf(
            eventType: AuditEventType,
            receipt: CommandReceiptReference?,
            transitionId: UUID,
            callerKey: String?,
        ): String? =
            receipt?.let { "${eventType.key}|receipt:${it.receiptId}|transition:$transitionId" } ?: callerKey

        fun auditEventTypeFor(mutation: InformationRequestMutation): AuditEventType? = when (mutation)
        {
            InformationRequestMutation.CREATE_DRAFT -> AuditEventType.INFORMATION_REQUEST_CREATE
            InformationRequestMutation.ISSUE -> AuditEventType.INFORMATION_REQUEST_ISSUE
            InformationRequestMutation.SUBMIT -> AuditEventType.INFORMATION_REQUEST_SUBMIT
            InformationRequestMutation.AMEND -> AuditEventType.INFORMATION_REQUEST_AMEND
            InformationRequestMutation.CANCEL -> AuditEventType.INFORMATION_REQUEST_CANCEL
            InformationRequestMutation.SUPERSEDE -> AuditEventType.INFORMATION_REQUEST_SUPERSEDE
            InformationRequestMutation.REASSIGN -> AuditEventType.INFORMATION_REQUEST_PARTY_REASSIGN
            InformationRequestMutation.SAVE_RESPONSE -> AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND
            InformationRequestMutation.ATTEST_RESPONSE -> AuditEventType.INFORMATION_REQUEST_REQUIREMENT_ATTEST
            InformationRequestMutation.ADMINISTER_EVIDENCE -> AuditEventType.INFORMATION_REQUEST_EVIDENCE_ADMINISTER
            InformationRequestMutation.CLOSE -> AuditEventType.INFORMATION_REQUEST_CLOSE
            InformationRequestMutation.WITHDRAW_SUBMISSION -> AuditEventType.INFORMATION_REQUEST_SUBMISSION_WITHDRAW
            InformationRequestMutation.CREATE_SUCCESSOR -> AuditEventType.INFORMATION_REQUEST_SUCCESSOR_CREATE
            InformationRequestMutation.SCHEDULE_FOLLOW_UP -> AuditEventType.INFORMATION_REQUEST_FOLLOW_UP_SCHEDULE
            InformationRequestMutation.START_REVIEW -> AuditEventType.INFORMATION_REQUEST_REVIEW_START
            InformationRequestMutation.ASSIGN_REVIEWER -> AuditEventType.INFORMATION_REQUEST_REVIEW_ASSIGN
            InformationRequestMutation.SAVE_REVIEW_DRAFT -> AuditEventType.INFORMATION_REQUEST_REVIEW_DRAFT
            InformationRequestMutation.RECORD_REVIEW_DECISION -> AuditEventType.INFORMATION_REQUEST_REQUIREMENT_REVIEW
            InformationRequestMutation.RECORD_FINDING -> AuditEventType.INFORMATION_REQUEST_REVIEW_FINDING
            InformationRequestMutation.RECORD_REVIEW_COMMENT -> AuditEventType.INFORMATION_REQUEST_REVIEW_COMMENT
            InformationRequestMutation.SETTLE_REVIEW -> AuditEventType.INFORMATION_REQUEST_REVIEW_SETTLE
            InformationRequestMutation.REQUEST_CORRECTION -> AuditEventType.INFORMATION_REQUEST_CORRECTION_REQUEST
            InformationRequestMutation.PROMOTE_FACT -> AuditEventType.INFORMATION_REQUEST_FACT_PROMOTE
            InformationRequestMutation.REVOKE_FACT -> AuditEventType.INFORMATION_REQUEST_FACT_REVOKE
            InformationRequestMutation.RECERTIFY_FACT -> AuditEventType.INFORMATION_REQUEST_FACT_RECERTIFY
            InformationRequestMutation.REQUEST_EXTERNAL_SOURCE -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_REQUEST
            InformationRequestMutation.START_EXTERNAL_SOURCE_ATTEMPT -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_ATTEMPT
            InformationRequestMutation.SCHEDULE_EXTERNAL_SOURCE_RETRY -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_RETRY
            InformationRequestMutation.RECORD_EXTERNAL_SOURCE_PENDING -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_PENDING
            InformationRequestMutation.COMPLETE_EXTERNAL_SOURCE -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_COMPLETE
            InformationRequestMutation.FAIL_EXTERNAL_SOURCE -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_SOURCE_FAIL
            InformationRequestMutation.RECORD_EXTERNAL_VALUE -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_VALUE_RECORD
            InformationRequestMutation.DECIDE_EXTERNAL_VALUE -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_VALUE_DECIDE
            InformationRequestMutation.RECONCILE_IMPORTED_VALUES -> AuditEventType.INFORMATION_REQUEST_EXTERNAL_VALUES_RECONCILE
            InformationRequestMutation.RECORD_GENERATED_OUTPUT -> AuditEventType.INFORMATION_REQUEST_GENERATED_OUTPUT_RECORD
            InformationRequestMutation.RECORD_BUSINESS_DECISION -> AuditEventType.INFORMATION_REQUEST_BUSINESS_DECISION_RECORD
            InformationRequestMutation.RECORD_FIRST_VIEW -> AuditEventType.INFORMATION_REQUEST_FIRST_VIEW
            InformationRequestMutation.START_RESPONSE -> AuditEventType.INFORMATION_REQUEST_START
            InformationRequestMutation.EXPIRE -> AuditEventType.INFORMATION_REQUEST_EXPIRE
            InformationRequestMutation.RECORD_OVERDUE -> AuditEventType.INFORMATION_REQUEST_OVERDUE
            InformationRequestMutation.CHANGE_COMPLETION_GATE -> AuditEventType.INFORMATION_REQUEST_COMPLETION_GATE_CHANGE
            InformationRequestMutation.START_CLOCK -> AuditEventType.INFORMATION_REQUEST_CLOCK_START
            InformationRequestMutation.PAUSE_CLOCK -> AuditEventType.INFORMATION_REQUEST_CLOCK_PAUSE
            InformationRequestMutation.RESUME_CLOCK -> AuditEventType.INFORMATION_REQUEST_CLOCK_RESUME
            InformationRequestMutation.EXTEND_CLOCK -> AuditEventType.INFORMATION_REQUEST_CLOCK_EXTEND
            InformationRequestMutation.RECORD_REMINDER -> AuditEventType.INFORMATION_REQUEST_CLOCK_REMIND
            InformationRequestMutation.ASSIGN_PARTY -> AuditEventType.INFORMATION_REQUEST_PARTY_ASSIGN
            InformationRequestMutation.REVOKE_PARTY -> AuditEventType.INFORMATION_REQUEST_PARTY_REVOKE
            InformationRequestMutation.GRANT_DELEGATED_AUTHORITY -> AuditEventType.INFORMATION_REQUEST_DELEGATED_AUTHORITY_GRANT
            InformationRequestMutation.REVOKE_DELEGATED_AUTHORITY -> AuditEventType.INFORMATION_REQUEST_DELEGATED_AUTHORITY_REVOKE
            InformationRequestMutation.ISSUE_ACCESS_LINK -> AuditEventType.INFORMATION_REQUEST_ACCESS_LINK_ISSUE
            InformationRequestMutation.ROTATE_ACCESS_LINK -> AuditEventType.INFORMATION_REQUEST_ACCESS_LINK_ROTATE
            InformationRequestMutation.REPLACE_ACCESS_LINK -> AuditEventType.INFORMATION_REQUEST_ACCESS_LINK_REPLACE
            InformationRequestMutation.REVOKE_ACCESS_LINK -> AuditEventType.INFORMATION_REQUEST_ACCESS_LINK_REVOKE
            InformationRequestMutation.REVOKE_ACCESS_SESSION -> AuditEventType.INFORMATION_REQUEST_ACCESS_SESSION_REVOKE
            InformationRequestMutation.ISSUE_ACCESS_SESSION -> AuditEventType.INFORMATION_REQUEST_ACCESS_SESSION_ISSUE
            InformationRequestMutation.ISSUE_CONTACT_CHALLENGE -> AuditEventType.INFORMATION_REQUEST_CONTACT_CHALLENGE_ISSUE
            InformationRequestMutation.REFUSE_CONTACT_CHALLENGE -> AuditEventType.INFORMATION_REQUEST_CONTACT_CHALLENGE_REFUSE
            InformationRequestMutation.LINK_PARTICIPANT_ACCOUNT -> AuditEventType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK
            InformationRequestMutation.STEP_UP_ACCESS_SESSION -> AuditEventType.INFORMATION_REQUEST_ACCESS_SESSION_STEP_UP
            InformationRequestMutation.SEND_REMINDER -> AuditEventType.INFORMATION_REQUEST_REMIND
            InformationRequestMutation.RECORD_ESCALATION -> AuditEventType.INFORMATION_REQUEST_CLOCK_ESCALATE
        }
    }
}
