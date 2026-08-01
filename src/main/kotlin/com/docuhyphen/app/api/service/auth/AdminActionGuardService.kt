package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.StepUpRequiredException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Correlation data for an admin action. The only thing the API layer needs to pass
 * through is an optional request id used to correlate audit events with the original
 * HTTP request. Step-up state is derived from the verified server-side session and
 * MUST NOT be supplied by callers.
 */
data class AdminApprovalContext(
    val requestId: String? = null,
)

@RequestScoped
class AdminActionGuardService @Inject constructor(
    private val authAuditService: AuthAuditService,
    private val stepUpAuthService: StepUpAuthService,
)
{
    /**
     * Enforce step-up authentication for a sensitive admin action.
     *
     * Step-up freshness is derived from the current session's [StepUpAuthService.isFresh]
     * check (which reads the verified `auth_time` claim). There is no client-controlled
     * input that can satisfy this guard.
     *
     * If [requireStepUp] is `false`, the guard only emits a success audit event.
     */
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
