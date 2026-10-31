package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestationSubjectType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class TrustedGroupAccessReconciliationServiceTest {
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val shareService = mock<ShareService>()
    private val service = TrustedGroupAccessReconciliationService(attestationService, shareService)

    @Test
    fun `resuming a relationship re-expands the Exchange and request-scoped Shares bound to its group attestations`() {
        val relationshipId = UUID.randomUUID()
        val group = ExchangeRecipientAttestation().apply {
            exchangeRecipientId = UUID.randomUUID()
            subjectType = ExchangeRecipientAttestationSubjectType.GROUP
        }
        val person = ExchangeRecipientAttestation().apply {
            exchangeRecipientId = UUID.randomUUID()
            subjectType = ExchangeRecipientAttestationSubjectType.PERSON
        }
        val exchangeShareId = UUID.randomUUID()
        val requestShareId = UUID.randomUUID()
        whenever(attestationService.findForRelationship(relationshipId)).thenReturn(listOf(group, person))
        whenever(attestationService.directShareId(group)).thenReturn(exchangeShareId)
        whenever(attestationService.scopedGroupShareIdsFor(group.exchangeRecipientId)).thenReturn(listOf(requestShareId))

        service.reconcileRelationship(relationshipId)

        verify(shareService).reconcileGroupShare(exchangeShareId)
        verify(shareService).reconcileGroupShare(requestShareId)
        verify(attestationService, never()).scopedGroupShareIdsFor(person.exchangeRecipientId)
    }
}
