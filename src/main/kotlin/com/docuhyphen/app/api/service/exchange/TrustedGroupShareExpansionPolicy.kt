package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.service.auth.authz.GroupShareExpansionPolicy
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class TrustedGroupShareExpansionPolicy @Inject constructor(
    private val attestationService: ExchangeRecipientAttestationService,
    private val trustedRecipientValidationService: TrustedRecipientValidationService,
) : GroupShareExpansionPolicy {
    override fun expandsToNewMembers(groupShare: Share): Boolean {
        val attestation = attestationService.findForDirectShare(groupShare.id)
            ?: attestationService.findForScopedGroupShare(groupShare)
            ?: return true
        return trustedRecipientValidationService.isGroupAttestationCurrentlyEligible(attestation)
    }
}
