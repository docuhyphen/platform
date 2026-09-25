package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import java.util.UUID

data class InformationRequestAttestationPolicy(
    val bindingId: UUID,
    val requiredRoles: List<InformationRequestContributorRole>,
    val ordering: InformationRequestAttestationOrdering,
    val minimumAssentCount: Int,
    val minimumAuthenticationStrength: InformationRequestAuthenticationStrength,
    val validityHours: Int?,
    val externalSignatureReference: InformationRequestExternalSignatureReferencePolicy,
    val policyHash: String,
)

enum class InformationRequestAttestationState
{
    SATISFIED,
    PENDING,
    REFUSED,
}

data class InformationRequestAttestingParty(
    val partyId: UUID,
    val role: InformationRequestContributorRole,
)

data class InformationRequestAttestationEvaluation(
    val state: InformationRequestAttestationState,
    val counted: List<InformationRequestSubmissionAttestation>,
    val refusals: List<InformationRequestSubmissionAttestation>,
    val missingRoles: List<InformationRequestContributorRole>,
    val assentCount: Int,
    val requiredAssentCount: Int,
    val superseded: Int,
)

data class InformationRequestActingParty(
    val party: InformationRequestParty,
    val role: InformationRequestContributorRole,
    val delegatedAuthorityId: UUID?,
)

data class InformationRequestAttestationRequirementEvaluation(
    val requirementId: UUID,
    val bindingId: UUID,
    val stageKey: String?,
    val contentHash: String,
    val policy: InformationRequestAttestationPolicy,
    val evaluation: InformationRequestAttestationEvaluation,
)
