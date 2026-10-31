package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue

sealed interface WorkflowRequirementOperand
{
    data class Value(val value: CanonicalFieldValue) : WorkflowRequirementOperand

    data class Empty(val type: FieldValueType) : WorkflowRequirementOperand

    data class Unavailable(val reason: String) : WorkflowRequirementOperand
}

data class WorkflowTriggerSubject(
    val subjectResourceType: String,
    val subjectFieldNames: Set<String>,
)
