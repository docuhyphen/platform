package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import java.util.*

internal fun singleFieldConfiguration(schemaVersionId: UUID, field: WalkingSkeletonField) =
    InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "records",
                title = "Records",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = "recorded-note",
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "Record the note",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                        collectedFieldDefinitionId = field.fieldDefinitionId,
                    ),
                ),
            ),
        ),
    )
