package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateSchemaCompatibility
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionFreezer

internal data class TemplateVersionFreezerFixture(
    val freezer: InformationRequestTemplateVersionFreezer,
    val definitionRepository: InformationRequestTemplateDefinitionRepository,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    val definition: InformationRequestTemplateDefinition,
    val version: InformationRequestTemplateVersion,
    val derived: List<InformationRequestCapability>,
)
