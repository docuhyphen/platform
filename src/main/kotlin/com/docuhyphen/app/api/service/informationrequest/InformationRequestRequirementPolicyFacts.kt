package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyFacts
import java.util.UUID

data class InformationRequestRequirementPolicyFacts(
    val requestId: UUID,
    val requirementId: UUID,
    val currentRevisionId: UUID,
    val currentRevisionNumber: Int,
    val sourceTemplateBindingId: UUID,
    val occurrencePath: String,
    val responseMode: InformationRequestResponseMode,
    val assignedRoleKey: InformationRequestShareRoleKey,
    val assignedParties: List<InformationRequestRequirementAssignedPartyFact>,
    val confidentialityCompartmentKey: String?,
    val correctionScope: InformationRequestRequirementCorrectionScope,
    val requestState: InformationRequestState,
    val parent: InformationRequestParentSnapshot,
    val delegatedAuthorityFacts: List<InformationRequestRequirementDelegatedAuthorityFact> = emptyList(),
    val occurrenceRemoved: Boolean = false,
    val attestingRoleKeys: Set<InformationRequestShareRoleKey> = emptySet(),
) : ResourcePolicyFacts

data class InformationRequestRequirementAssignedPartyFact(
    val partyId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val principal: PrincipalRef?,
    val equivalentPrincipals: Set<PrincipalRef> = emptySet(),
    val subjectIdentityRefId: UUID?,
    val exchangeRecipientId: UUID?,
    val shareId: UUID?,
)

data class InformationRequestRequirementDelegatedAuthorityFact(
    val authorityId: UUID,
    val assignedPartyId: UUID,
    val delegatePrincipal: PrincipalRef,
    val requestId: UUID,
    val requirementId: UUID?,
    val active: Boolean,
)

enum class InformationRequestRequirementCorrectionScope
{
    NORMAL_RESPONSE,
    OPEN_CORRECTION,
}
