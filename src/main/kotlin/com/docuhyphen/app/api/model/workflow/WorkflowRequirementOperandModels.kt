package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.service.fields.CanonicalFieldValue

sealed interface WorkflowRequirementOperand
{
    data class Value(val value: CanonicalFieldValue) : WorkflowRequirementOperand

    data object Empty : WorkflowRequirementOperand

    data class Unavailable(val reason: String) : WorkflowRequirementOperand
}

data class WorkflowTriggerSubject(
    val subjectResourceType: String,
    val subjectFieldNames: Set<String>,
)
