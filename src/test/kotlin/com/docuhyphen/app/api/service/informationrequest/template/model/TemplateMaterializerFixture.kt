package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer

internal data class TemplateMaterializerFixture(
    val service: InformationRequestTemplateMaterializer,
    val schemaAssignmentService: SchemaAssignmentService,
    val requirementRepository: InformationRequestRequirementRepository,
    val savedRequirements: MutableList<InformationRequestRequirement>,
    val savedRevisions: MutableList<InformationRequestRequirementRevision>,
    val savedCurrents: MutableList<InformationRequestRequirementCurrent>,
    val savedGroupOccurrences: MutableList<InformationRequestGroupOccurrence>,
    val supportingEvidenceLinkService: InformationRequestSupportingEvidenceLinkService,
)
