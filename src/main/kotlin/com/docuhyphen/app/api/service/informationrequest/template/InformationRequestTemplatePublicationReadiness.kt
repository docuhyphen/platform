package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition

internal object InformationRequestTemplatePublicationReadiness
{
    fun requireReady(draft: InformationRequestTemplateVersionDto?)
    {
        val version = draft ?: return
        version.sections.forEach { section ->
            section.requirements.forEach { requirement -> requireReady(version, section, requirement) }
        }
    }

    private fun requireReady(
        version: InformationRequestTemplateVersionDto,
        section: InformationRequestTemplateSectionDto,
        requirement: InformationRequestTemplateRequirementDto,
    )
    {
        val key = requirement.requirementKey
        if (requirement.requirementType == InformationRequestRequirementType.FIELD && version.schemaVersionId == null)
        {
            refuse(
                "Requirement $key asks for typed data, so this version names the Schema Version it resolves against",
                section,
                requirement,
            )
        }
        if (requirement.requirementType == InformationRequestRequirementType.DOCUMENT && requirement.evidencePolicy == null)
        {
            refuse(
                "Requirement $key asks for a document but states no evidence policy to judge its files by",
                section,
                requirement,
            )
        }
        val waiver = requirement.evidencePolicy?.waiverPolicy ?: return
        val permitsWaiver = InformationRequestResponseDisposition.WAIVED in requirement.permittedDispositions
        if (waiver != InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && !permitsWaiver)
        {
            refuse("Requirement $key states a waiver rule, so it permits a waived answer", section, requirement)
        }
        if (waiver == InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && permitsWaiver)
        {
            refuse(
                "Requirement $key permits a waived answer, so its evidence policy states the waiver rule that reaches it",
                section,
                requirement,
            )
        }
    }

    private fun refuse(
        message: String,
        section: InformationRequestTemplateSectionDto,
        requirement: InformationRequestTemplateRequirementDto,
    ): Nothing = throw InformationRequestTemplateValidationException(
        message,
        sectionKey = section.sectionKey,
        requirementKey = requirement.requirementKey,
    )
}
