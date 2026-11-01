package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.workflow.Decision
import java.time.Instant

data class WorkflowDecisionRecord(
    val principal: PrincipalRef,
    val decision: Decision,
    val decidedAt: Instant,
)
