package com.docuhyphen.app.api.service.blueprint.model

import com.docuhyphen.app.api.repository.blueprint.BlueprintDefinitionRepository
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService

internal data class BlueprintTemplateReferenceFixture(
    val service: BlueprintDefinitionService,
    val repository: BlueprintDefinitionRepository,
    val templateReferenceService: InformationRequestTemplateReferenceService,
)
