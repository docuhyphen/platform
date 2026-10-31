package com.docuhyphen.app.api.service.workflow.model

import com.docuhyphen.app.api.repository.workflow.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.service.workflow.WorkflowDefinitionService

internal data class WorkflowTenantFixture(
    val service: WorkflowDefinitionService,
    val definitionRepository: WorkflowDefinitionRepository,
    val instanceRepository: WorkflowInstanceRepository,
)
