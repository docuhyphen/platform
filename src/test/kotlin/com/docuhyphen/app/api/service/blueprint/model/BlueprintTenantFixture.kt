package com.docuhyphen.app.api.service.blueprint.model

import com.docuhyphen.app.api.repository.blueprint.BlueprintDefinitionRepository
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService

internal data class BlueprintTenantFixture(
    val service: BlueprintDefinitionService,
    val repository: BlueprintDefinitionRepository,
)
