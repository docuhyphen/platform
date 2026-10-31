package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.service.fields.FieldDefinitionService

internal data class FieldDefinitionTenantFixture(
    val service: FieldDefinitionService,
    val definitionRepository: FieldDefinitionRepository,
    val contractRepository: FieldContractRepository,
)
