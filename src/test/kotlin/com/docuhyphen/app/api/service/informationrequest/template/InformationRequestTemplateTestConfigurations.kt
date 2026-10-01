package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.service.informationrequest.capability.WalkingSkeletonField
import java.util.UUID

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
