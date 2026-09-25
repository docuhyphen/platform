package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.InformationRequestTransitionActorKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceTransition
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class InformationRequestTransitionHistoryCommand(
    val request: InformationRequest,
    val fromState: InformationRequestState?,
    val toState: InformationRequestState,
    val mutation: InformationRequestMutation,
    val actor: PrincipalRef,
    val reasonCode: String? = null,
    val partyId: UUID? = null,
    val commandReceiptId: UUID? = null,
    val idempotencyKey: String? = null,
    val evidence: InformationRequestEvidenceTransition? = null,
    val details: Map<String, String> = emptyMap(),
)

@ApplicationScoped
class InformationRequestTransitionHistoryService @Inject constructor(
    private val transitionRepository: InformationRequestTransitionRepository,
    private val auditRecorder: AuditRecorder,
    @TransactionalEventSink private val domainEventPublisher: DomainEventPublisher,
)
{
    fun record(command: InformationRequestTransitionHistoryCommand): InformationRequestTransition
    {
        val occurredAt = Timestamp.from(Instant.now())
        val transition = transitionRepository.save(
            InformationRequestTransition().apply {
                informationRequestId = command.request.id
                sequenceNumber = transitionRepository.nextSequenceNumber(command.request.id)
                fromState = command.fromState
                toState = command.toState
                mutation = command.mutation
                actorKind = transitionActorKindOf(command.actor.kind)
                actorId = command.actor.id
                reasonCode = command.reasonCode?.trim()?.ifBlank { null }
                partyId = command.partyId
                commandReceiptId = command.commandReceiptId
                this.occurredAt = occurredAt
            },
        )

        val eventType = auditEventTypeFor(command.mutation) ?: return transition
        val owner = informationRequestAuditOwner(command.request)
        val eventId = UUID.randomUUID()
        val payload = buildPayload(command, transition)
        auditRecorder.record(
            AuditEventDraft(
                owner = owner,
                eventTypeKey = eventType.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = command.actor.id,
                actorKind = AuditActorKind.forPrincipal(command.actor.kind),
                targetType = "INFORMATION_REQUEST",
                targetId = command.request.id.toString(),
                reason = command.reasonCode?.trim()?.ifBlank { null },
                payload = payload,
                eventId = eventId,
                idempotencyKey = command.idempotencyKey,
                businessTransactionId = transition.id.toString(),
            ),
        )
        domainEventPublisher.publish(
            DomainEvent(
                id = eventId.toString(),
                type = eventType.key,
                actor = DomainEvent.PrincipalRefDto.from(command.actor),
                subject = DomainEvent.SubjectRef("INFORMATION_REQUEST", command.request.id.toString()),
                organizationId = command.request.ownerOrganizationId?.toString(),
                payload = payload,
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

    private fun auditEventTypeFor(mutation: InformationRequestMutation): AuditEventType? = when (mutation)
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
        else -> null
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

}
