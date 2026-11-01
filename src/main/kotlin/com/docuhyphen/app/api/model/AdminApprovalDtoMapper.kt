package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.resource.model.AdminApprovalApproveResponse
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateResponse

object AdminApprovalDtoMapper
{
    fun toInitiateResponse(approval: WorkflowInstance): AdminApprovalInitiateResponse = AdminApprovalInitiateResponse(
        approvalId = approval.id.toString(),
        status = approval.status.name,
    )

    fun toApproveResponse(approval: WorkflowInstance): AdminApprovalApproveResponse = AdminApprovalApproveResponse(
        approvalId = approval.id.toString(),
        status = approval.status.name,
    )
}
