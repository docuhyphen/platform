package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import java.util.*

interface WorkflowRequirementOperandSource
{
    fun frozenValue(
        requestId: UUID,
        packageId: UUID,
        templateRequirementId: UUID,
        occurrencePath: String
    ): WorkflowRequirementOperand
}
