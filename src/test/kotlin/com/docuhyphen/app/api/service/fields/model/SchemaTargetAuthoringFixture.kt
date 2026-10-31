package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService

internal data class SchemaTargetAuthoringFixture(
    val service: SchemaDefinitionService,
    val schemaRepository: SchemaDefinitionRepository,
)
