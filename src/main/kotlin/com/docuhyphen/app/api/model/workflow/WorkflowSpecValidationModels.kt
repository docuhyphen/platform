package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.model.entity.WorkflowScope
import java.util.UUID

data class WorkflowValidationError(
    val code: String,
    val stepIndex: Int?,
    val fieldPath: String,
    val message: String,
)

data class WorkflowDefinitionScope(
    val scope: WorkflowScope,
    val organizationId: UUID?,
    val createdByAppUserId: UUID?,
)
