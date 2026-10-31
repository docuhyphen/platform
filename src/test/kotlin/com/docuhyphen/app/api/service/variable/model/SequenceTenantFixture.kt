package com.docuhyphen.app.api.service.variable.model

import com.docuhyphen.app.api.repository.variable.SequenceDefinitionRepository
import com.docuhyphen.app.api.service.variable.SequenceDefinitionService

internal data class SequenceTenantFixture(
    val service: SequenceDefinitionService,
    val repository: SequenceDefinitionRepository,
)
