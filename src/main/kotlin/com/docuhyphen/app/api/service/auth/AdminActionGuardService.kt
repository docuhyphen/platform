package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

@RequestScoped
class AdminActionGuardService @Inject constructor(
    private val authAuditService: AuthAuditService,
    private val stepUpAuthService: StepUpAuthService,
)
{
    fun enforce(
        action: String,
        actorId: UUID?,
        context: AdminApprovalContext,
        requireStepUp: Boolean = true,
    )
    {
        if (requireStepUp && !stepUpAuthService.isFresh())
        {
            authAuditService.emit(
                action = action,
                outcome = "DENY",
                reasonCode = RevocationReasonCode.STEP_UP_REQUIRED,
                actorId = actorId,
                requestId = context.requestId,
                reason = "Session lacks fresh authentication for sensitive action",
            )
            throw StepUpRequiredException(action = StepUpActionLabelFormatter.labelFor(action) ?: action)
        }

        authAuditService.emit(
            action = action,
            outcome = "SUCCESS",
            actorId = actorId,
            requestId = context.requestId,
        )
    }
}
