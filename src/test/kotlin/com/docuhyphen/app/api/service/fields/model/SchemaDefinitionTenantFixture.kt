package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService

internal data class SchemaDefinitionTenantFixture(
    val service: SchemaDefinitionService,
    val schemaRepository: SchemaDefinitionRepository,
    val versionRepository: SchemaVersionRepository,
    val bindingRepository: SchemaFieldBindingRepository,
    val contractRepository: FieldContractRepository,
    val fieldRepository: FieldDefinitionRepository,
)
