package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestDelegatedAuthorityRecorder @Inject constructor(
    private val transitionHistory: InformationRequestTransitionHistoryService,
) {
    fun record(
        request: InformationRequest,
        authority: InformationRequestDelegatedAuthority,
        mutation: InformationRequestMutation,
        access: RequestAccessContext,
        idempotencyKey: String,
    ) {
        val details = detailsOf(authority)
        val commandKey = CommandRequestFingerprint.sha256Hex(idempotencyKey)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = InformationRequestAuditActor.of(access),
                partyId = authority.assignedPartyId,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${authority.id}|$commandKey",
                details = details,
                requireAuditCapture = true,
            ),
        )
    }

    fun recordCutoff(
        request: InformationRequest,
        authority: InformationRequestDelegatedAuthority,
        cutoff: InformationRequestCredentialCutoff,
    ) {
        val mutation = InformationRequestMutation.REVOKE_DELEGATED_AUTHORITY
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = cutoff.actor,
                reasonCode = cutoff.reasonCode,
                partyId = authority.assignedPartyId,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${authority.id}|${cutoff.reasonCode.lowercase()}",
                details = detailsOf(authority),
                requireAuditCapture = true,
            ),
        )
    }

    private fun detailsOf(authority: InformationRequestDelegatedAuthority): Map<String, String> {
        val details = linkedMapOf("authorityId" to authority.id.toString())
        authority.requirementId?.let { details["requirementId"] = it.toString() }
        return details
    }
}
