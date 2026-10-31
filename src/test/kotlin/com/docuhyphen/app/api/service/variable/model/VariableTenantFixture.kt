package com.docuhyphen.app.api.service.variable.model

import com.docuhyphen.app.api.repository.variable.VariableDefinitionRepository
import com.docuhyphen.app.api.service.variable.VariableDefinitionService

internal data class VariableTenantFixture(
    val service: VariableDefinitionService,
    val repository: VariableDefinitionRepository,
)
