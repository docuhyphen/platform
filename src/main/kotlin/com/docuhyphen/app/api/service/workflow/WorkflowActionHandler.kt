package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.workflow.ActionResult

interface WorkflowActionHandler
{
    fun key(): String

    fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
}
