package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestationSubjectType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class TrustedGroupAccessReconciliationService @Inject constructor(
    private val attestationService: ExchangeRecipientAttestationService,
    private val shareService: ShareService,
)
{
    fun reconcileRelationship(relationshipId: UUID)
    {
        attestationService.findForRelationship(relationshipId)
            .filter { it.subjectType == ExchangeRecipientAttestationSubjectType.GROUP }
            .mapNotNull(attestationService::directShareId)
            .forEach(shareService::reconcileGroupShare)
    }
}
