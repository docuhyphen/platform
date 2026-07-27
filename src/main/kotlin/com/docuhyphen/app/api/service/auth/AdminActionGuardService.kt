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
    companion object
    {
        private val ACTION_LABELS: Map<String, String> = mapOf(
            "ORG_APP_USER_ADD"                          to "add a new user",
            "ORG_APP_USER_UPDATE"                       to "update a user",
            "ORG_APP_USER_DELETE"                       to "remove a user",
            "ORG_UPDATE"                                to "update your organization",
            "ORG_SETTINGS_UPDATE"                       to "update organization settings",
            "ORG_GROUP_ADD"                             to "create a group",
            "ORG_GROUP_UPDATE"                          to "update a group",
            "ORG_GROUP_DELETE"                          to "delete a group",
            "PERSONAL_GROUP_DELETE"                     to "delete a personal group",
            "ORG_SHARE_EXTERNAL_CUSTOMER"               to "share with an external customer",
            "ORG_IDP_CONFIG_CREATE"                     to "add an identity provider",
            "ORG_IDP_CONFIG_UPDATE"                     to "update an identity provider",
            "ORG_IDP_CONFIG_DELETE"                     to "remove an identity provider",
            "ORG_AUTH_EXCHANGE_POLICY_UPDATE"           to "update the exchange authentication policy",
            "ORG_VARIABLE_CREATE"                       to "create an organization variable",
            "ORG_VARIABLE_UPDATE"                       to "update an organization variable",
            "ORG_VARIABLE_DELETE"                       to "delete an organization variable",
            "PERSONAL_VARIABLE_CREATE"                  to "create a personal variable",
            "PERSONAL_VARIABLE_UPDATE"                  to "update a personal variable",
            "PERSONAL_VARIABLE_DELETE"                  to "delete a personal variable",
            "ORG_SEQUENCE_CREATE"                       to "create a sequence",
            "ORG_SEQUENCE_UPDATE"                       to "update a sequence",
            "ORG_SEQUENCE_DELETE"                       to "delete a sequence",
            "ORG_SEQUENCE_RESET"                        to "reset a sequence counter",
            "PERSONAL_BLUEPRINT_CREATE"                 to "create a personal blueprint",
            "PERSONAL_BLUEPRINT_UPDATE"                 to "update a personal blueprint",
            "PERSONAL_BLUEPRINT_STATUS_UPDATE"          to "change a personal blueprint status",
            "PERSONAL_BLUEPRINT_DELETE"                 to "delete a personal blueprint",
            "PERSONAL_BLUEPRINT_CLONE"                  to "duplicate a blueprint into your personal collection",
            "ORG_BLUEPRINT_CREATE"                      to "create an organization blueprint",
            "ORG_BLUEPRINT_UPDATE"                      to "update an organization blueprint",
            "ORG_BLUEPRINT_STATUS_UPDATE"               to "change an organization blueprint status",
            "ORG_BLUEPRINT_PUBLISH_UPDATE"              to "change organization blueprint publishing",
            "ORG_BLUEPRINT_DELETE"                      to "delete an organization blueprint",
            "ORG_BLUEPRINT_CLONE"                       to "duplicate a blueprint into the organization collection",
            "APP_BLUEPRINT_CREATE"                      to "create a platform blueprint",
            "APP_BLUEPRINT_UPDATE"                      to "update a platform blueprint",
            "APP_BLUEPRINT_STATUS_UPDATE"               to "change a platform blueprint status",
            "APP_BLUEPRINT_PUBLISH_UPDATE"              to "change platform blueprint publishing",
            "APP_BLUEPRINT_DELETE"                      to "delete a platform blueprint",
            "ORG_IDP_SECRET_ROTATE"                     to "rotate an identity provider secret",
            "ORG_IDP_SECRET_ROLLBACK"                   to "roll back an identity provider secret",
            "ORG_IDP_SECRET_ACTIVATE"                   to "activate an identity provider secret",
            "ORG_IDP_SECRET_DISABLE"                    to "disable an identity provider secret",
            "ORG_IDP_SECRET_ENABLE"                     to "enable an identity provider secret",
            "ORG_IDP_SECRET_RETIRE"                     to "retire an identity provider secret",
            "ORG_IDP_SECRET_ROTATION_RUNBOOK"           to "run an identity provider secret rotation",
            "ORG_IDP_SECRET_ROTATION_PREVIEW"           to "preview an identity provider secret rotation",
            "PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT"  to "update an organization subscription policy",
            "PLATFORM_ORG_SUBSCRIPTION_POLICY_DELETE"  to "delete an organization subscription policy",
            "ORG_WORKFLOW_DEFINITION_DELETE"            to "delete a workflow",
        )
    }

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
            throw StepUpRequiredException(action = ACTION_LABELS[action] ?: action)
        }

        authAuditService.emit(
            action = action,
            outcome = "SUCCESS",
            actorId = actorId,
            requestId = context.requestId,
        )
    }
}
