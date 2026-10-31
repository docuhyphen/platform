package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType

internal object InformationRequestTemplateRoleValidator {
    fun validate(
        sectionKey: String,
        requirementKey: String,
        requirementType: InformationRequestRequirementType,
        contributorRole: InformationRequestContributorRole,
        confirmationRoles: List<InformationRequestContributorRole>,
    ) {
        val collectsAnswers = requirementType != InformationRequestRequirementType.RESPONSE_ATTESTATION
        if (contributorRole == InformationRequestContributorRole.SUBJECT) {
            throw InformationRequestTemplateValidationException(
                "Requirement $requirementKey needs an acting contributor role. SUBJECT identifies who or what " +
                        "the request is about; choose ${if (collectsAnswers) "CONTRIBUTOR or PREPARER" else "CONTRIBUTOR, PREPARER, or ATTESTOR"}",
                sectionKey = sectionKey,
                requirementKey = requirementKey,
            )
        }
        if (collectsAnswers && contributorRole == InformationRequestContributorRole.ATTESTOR) {
            throw InformationRequestTemplateValidationException(
                "Requirement $requirementKey asks for typed data or files, which ATTESTOR can confirm but never " +
                        "provide; choose CONTRIBUTOR or PREPARER",
                sectionKey = sectionKey,
                requirementKey = requirementKey,
            )
        }
        if (InformationRequestContributorRole.SUBJECT in confirmationRoles) {
            throw InformationRequestTemplateValidationException(
                "Requirement $requirementKey needs acting confirmation roles. SUBJECT cannot confirm " +
                        "information; choose CONTRIBUTOR, PREPARER, or ATTESTOR",
                sectionKey = sectionKey,
                requirementKey = requirementKey,
            )
        }
    }
}
