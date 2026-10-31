package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestScheduledContinuation
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestContinuationGuard @Inject constructor(
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
) {
    fun requireContinuation(locked: LockedInformationRequest, action: InformationRequestContinuationAction) {
        val grant = issuedGrantOf(locked) ?: return
        requireNotRevoked(grant)
        requireListed(grant, action)
    }

    fun requireOwnerRecovery(locked: LockedInformationRequest, action: InformationRequestContinuationAction) {
        val grant = issuedGrantOf(locked) ?: return
        requireListed(grant, action)
    }

    fun requireOwnerRecovery(locked: LockedInformationRequest) {
        issuedGrantOf(locked)
    }

    fun requireOwnerWithdrawal(locked: LockedInformationRequest) {
        if (executionGrantService.findForRequest(locked.request.id) == null) {
            entitlementGuard.requireNotOperationallySuspended(locked.exchange)
            return
        }
        requireOwnerRecovery(locked, InformationRequestContinuationAction.CANCEL)
    }

    fun requireIssuance(locked: LockedInformationRequest) {
        val grant = issuedGrantOf(locked) ?: return
        requireNotRevoked(grant)
    }

    fun scheduledStandingOf(locked: LockedInformationRequest): InformationRequestScheduledContinuation =
        scheduledWorkOf(locked, null)

    fun scheduledContinuationOf(
        locked: LockedInformationRequest,
        action: InformationRequestContinuationAction,
    ): InformationRequestScheduledContinuation = scheduledWorkOf(locked, action)

    private fun scheduledWorkOf(
        locked: LockedInformationRequest,
        action: InformationRequestContinuationAction?,
    ): InformationRequestScheduledContinuation {
        if (entitlementGuard.isOperationallySuspended(locked.exchange)) {
            return InformationRequestScheduledContinuation.OPERATIONALLY_SUSPENDED
        }
        val grant = executionGrantService.findForRequest(locked.request.id)
            ?: return if (entitlementGuard.permitsRequestMutation(locked.exchange))
                InformationRequestScheduledContinuation.PROCEEDS
            else
                InformationRequestScheduledContinuation.OWNER_CHANGES_UNAVAILABLE
        return when {
            grant.revokedAt != null -> InformationRequestScheduledContinuation.EXECUTION_GRANT_REVOKED
            action != null && action.name !in grant.continuationActions ->
                InformationRequestScheduledContinuation.CONTINUATION_NOT_GRANTED

            else -> InformationRequestScheduledContinuation.PROCEEDS
        }
    }

    private fun issuedGrantOf(locked: LockedInformationRequest): RequestExecutionGrant? {
        val grant = executionGrantService.findForRequest(locked.request.id)
        if (grant == null) {
            entitlementGuard.requireRequestMutation(locked.exchange)
            return null
        }
        entitlementGuard.requireNotOperationallySuspended(locked.exchange)
        return grant
    }

    private fun requireNotRevoked(grant: RequestExecutionGrant) {
        if (grant.revokedAt != null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }

    private fun requireListed(grant: RequestExecutionGrant, action: InformationRequestContinuationAction) {
        if (action.name !in grant.continuationActions) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED,
                "This request's execution grant does not allow this change",
            )
        }
    }
}
