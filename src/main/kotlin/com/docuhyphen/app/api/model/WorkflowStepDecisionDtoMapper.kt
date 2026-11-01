package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.WorkflowDecisionResponse
import com.docuhyphen.app.api.model.workflow.DecisionResult

object WorkflowStepDecisionDtoMapper
{
    fun toDto(result: DecisionResult): WorkflowDecisionResponse = WorkflowDecisionResponse(
        instanceId = result.instanceId.toString(),
        stepInstanceId = result.stepInstanceId.toString(),
        stepStatus = result.stepStatus.name,
        instanceStatus = result.instanceStatus.name,
    )
}
