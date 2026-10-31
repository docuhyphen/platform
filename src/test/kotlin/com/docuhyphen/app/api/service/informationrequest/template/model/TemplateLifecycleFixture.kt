package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateLifecycleService

internal data class TemplateLifecycleFixture(
    val service: InformationRequestTemplateLifecycleService,
    val authoringService: InformationRequestTemplateAuthoringService,
    val definitionRepository: InformationRequestTemplateDefinitionRepository,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val configurationWriter: InformationRequestTemplateConfigurationWriter,
    val definition: InformationRequestTemplateDefinition,
    val source: InformationRequestTemplateVersion,
    val projected: InformationRequestTemplateDto,
)
