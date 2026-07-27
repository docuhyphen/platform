package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class TrustedRecipientAuditService @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    fun recordValidationAllowed(
        actorId: UUID,
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        selectionType: ExchangeRecipientSelectionType,
        subjectId: UUID,
    )
    {
        recordValidation(
            actorId,
            callerOrganizationId,
            targetOrganizationId,
            selectionType,
            subjectId,
            AuditEventType.ORG_TRUST_RECIPIENT_VALIDATION_ALLOWED,
            AuditOutcome.SUCCESS,
        )
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun recordValidationDenied(
        actorId: UUID,
        callerOrganizationId: UUID?,
        targetOrganizationId: UUID?,
        selectionType: ExchangeRecipientSelectionType,
        subjectId: UUID?,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = callerOrganizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                eventTypeKey = AuditEventType.ORG_TRUST_RECIPIENT_VALIDATION_DENIED.key,
                outcome = AuditOutcome.DENIED,
                actorId = actorId,
                actorKind = AuditActorKind.HUMAN,
                targetType = "TRUSTED_RECIPIENT",
                targetId = subjectId?.toString(),
                reason = "Trusted recipient validation denied",
                payload = validationPayload(selectionType, targetOrganizationId),
            ),
        )
    }

    fun recordAcceptanceAllowed(
        actorId: UUID,
        ownerOrganizationId: UUID,
        exchangeId: UUID,
        recipient: ExchangeRecipient,
    )
    {
        recordAcceptance(
            actorId,
            ownerOrganizationId,
            exchangeId,
            recipient,
            AuditEventType.ORG_TRUST_ACCEPTANCE_ALLOWED,
            AuditOutcome.SUCCESS,
            reason = null,
        )
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun recordAcceptanceDenied(
        actorId: UUID,
        ownerOrganizationId: UUID?,
        exchangeId: UUID,
        recipient: ExchangeRecipient,
    )
    {
        recordAcceptance(
            actorId,
            ownerOrganizationId,
            exchangeId,
            recipient,
            AuditEventType.ORG_TRUST_ACCEPTANCE_DENIED,
            AuditOutcome.DENIED,
            reason = "Trusted recipient acceptance denied",
        )
    }

    private fun recordValidation(
        actorId: UUID,
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        selectionType: ExchangeRecipientSelectionType,
        subjectId: UUID,
        eventType: AuditEventType,
        outcome: AuditOutcome,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = AuditOwnerScope.Organization(callerOrganizationId),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = actorId,
                actorKind = AuditActorKind.HUMAN,
                targetType = "TRUSTED_RECIPIENT",
                targetId = subjectId.toString(),
                payload = validationPayload(selectionType, targetOrganizationId),
            ),
        )
    }

    private fun recordAcceptance(
        actorId: UUID,
        ownerOrganizationId: UUID?,
        exchangeId: UUID,
        recipient: ExchangeRecipient,
        eventType: AuditEventType,
        outcome: AuditOutcome,
        reason: String?,
    )
    {
        val businessTransactionId = UUID.randomUUID().toString()
        val organizationIds = listOfNotNull(
            ownerOrganizationId,
            recipient.targetOrganizationId,
        ).distinct()
        val owners: List<AuditOwnerScope> = organizationIds
            .map(AuditOwnerScope::Organization)
            .ifEmpty { listOf(AuditOwnerScope.Platform) }
        owners.forEach { owner ->
            auditRecorder.record(
                AuditEventDraft(
                    owner = owner,
                    eventTypeKey = eventType.key,
                    outcome = outcome,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = "EXCHANGE_RECIPIENT",
                    targetId = recipient.id.toString(),
                    reason = reason,
                    payload = acceptancePayload(exchangeId, recipient),
                    businessTransactionId = businessTransactionId,
                ),
            )
        }
    }

    private fun validationPayload(
        selectionType: ExchangeRecipientSelectionType,
        targetOrganizationId: UUID?,
    ): Map<String, String> = buildMap {
        put("selectionType", selectionType.name)
        targetOrganizationId?.let { put("targetOrganizationId", it.toString()) }
    }

    private fun acceptancePayload(
        exchangeId: UUID,
        recipient: ExchangeRecipient,
    ): Map<String, String> = buildMap {
        put("exchangeId", exchangeId.toString())
        put("purpose", recipient.purpose.name)
        put("selectionType", recipient.selectionType.name)
        recipient.targetOrganizationId?.let { put("targetOrganizationId", it.toString()) }
    }
}
