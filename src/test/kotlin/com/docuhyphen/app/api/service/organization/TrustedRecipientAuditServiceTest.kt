package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.transaction.Transactional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class TrustedRecipientAuditServiceTest
{
    private val auditRecorder = mock<AuditRecorder>()
    private val service = TrustedRecipientAuditService(auditRecorder)

    @Test
    fun `records allowed and denied trusted recipient validation without sensitive evidence`()
    {
        whenever(auditRecorder.record(any())).thenReturn(captured())
        val actorId = UUID.randomUUID()
        val callerOrganizationId = UUID.randomUUID()
        val targetOrganizationId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()

        service.recordValidationAllowed(
            actorId,
            callerOrganizationId,
            targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            subjectId,
        )
        service.recordValidationDenied(
            actorId,
            callerOrganizationId,
            targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            subjectId,
        )

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, times(2)).record(drafts.capture())
        assertEquals(
            AuditEventType.ORG_TRUST_RECIPIENT_VALIDATION_ALLOWED.key,
            drafts.firstValue.eventTypeKey,
        )
        assertEquals(AuditOutcome.SUCCESS, drafts.firstValue.outcome)
        assertEquals(
            AuditEventType.ORG_TRUST_RECIPIENT_VALIDATION_DENIED.key,
            drafts.secondValue.eventTypeKey,
        )
        assertEquals(AuditOutcome.DENIED, drafts.secondValue.outcome)
        assertEquals(
            AuditOwnerScope.Organization(callerOrganizationId),
            drafts.secondValue.owner,
        )
        assertFalse(drafts.allValues.any { draft ->
            draft.payload.keys.any { key -> key.contains("email", ignoreCase = true) } ||
                draft.payload.keys.any { key -> key.contains("resolution", ignoreCase = true) }
        })
    }

    @Test
    fun `records allowed and denied trusted acceptance against the recipient`()
    {
        whenever(auditRecorder.record(any())).thenReturn(captured())
        val actorId = UUID.randomUUID()
        val ownerOrganizationId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val targetOrganizationId = UUID.randomUUID()
        val recipient = ExchangeRecipient().apply {
            this.exchangeId = exchangeId
            directShareId = UUID.randomUUID()
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
            this.targetOrganizationId = targetOrganizationId
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }

        service.recordAcceptanceAllowed(actorId, ownerOrganizationId, exchangeId, recipient)
        service.recordAcceptanceDenied(actorId, ownerOrganizationId, exchangeId, recipient)

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, times(4)).record(drafts.capture())
        assertEquals(
            AuditEventType.ORG_TRUST_ACCEPTANCE_ALLOWED.key,
            drafts.firstValue.eventTypeKey,
        )
        assertEquals(AuditOutcome.SUCCESS, drafts.firstValue.outcome)
        assertEquals(recipient.id.toString(), drafts.firstValue.targetId)
        assertEquals(
            setOf(
                AuditOwnerScope.Organization(ownerOrganizationId),
                AuditOwnerScope.Organization(targetOrganizationId),
            ),
            drafts.allValues.take(2).map { it.owner }.toSet(),
        )
        assertEquals(
            1,
            drafts.allValues.take(2).map { it.businessTransactionId }.distinct().size,
        )
        assertEquals(
            AuditEventType.ORG_TRUST_ACCEPTANCE_DENIED.key,
            drafts.allValues[2].eventTypeKey,
        )
        assertEquals(AuditOutcome.DENIED, drafts.allValues[2].outcome)
        assertEquals(
            1,
            drafts.allValues.drop(2).map { it.businessTransactionId }.distinct().size,
        )
    }

    @Test
    fun `denied audit methods use isolated transactions`()
    {
        val validation = TrustedRecipientAuditService::class.java.getMethod(
            "recordValidationDenied",
            UUID::class.java,
            UUID::class.java,
            UUID::class.java,
            ExchangeRecipientSelectionType::class.java,
            UUID::class.java,
        ).getAnnotation(Transactional::class.java)
        val acceptance = TrustedRecipientAuditService::class.java.getMethod(
            "recordAcceptanceDenied",
            UUID::class.java,
            UUID::class.java,
            UUID::class.java,
            ExchangeRecipient::class.java,
        ).getAnnotation(Transactional::class.java)

        assertEquals(Transactional.TxType.REQUIRES_NEW, validation.value)
        assertEquals(Transactional.TxType.REQUIRES_NEW, acceptance.value)
    }

    private fun captured(): AuditCaptureResult =
        AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID())
}
