package com.docuhyphen.app.api.model.informationrequest.capability

enum class InformationRequestCapability(val contractVersion: Int)
{
    STRUCTURED_RESPONSE(1),

    DOCUMENT_EVIDENCE(1),

    RESPONSE_ATTESTATION(1),

    CONDITIONAL_REQUIREMENT(1),

    REPEATABLE_OCCURRENCE(1),

    RESPONSE_REVIEW(1),

    CONFIDENTIALITY_COMPARTMENT(1),

    EVIDENCE_WAIVER(1),

    SUBSTITUTE_EVIDENCE(1),

    SUPPORTING_EVIDENCE(1),

    RESPONSE_SUBMISSION(1);

    companion object
    {
        fun fromCodeOrNull(value: String?): InformationRequestCapability?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

data class InformationRequestCapabilityRequirement(
    val capability: InformationRequestCapability,
    val requiredContractVersion: Int,
)
