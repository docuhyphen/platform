package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.ShareLink
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
class InformationRequestAccessLinkRecorder @Inject constructor(
    private val transitionHistory: InformationRequestTransitionHistoryService,
) {
    fun record(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        mutation: InformationRequestMutation,
        access: RequestAccessContext,
        idempotencyKey: String,
    ) {
        val commandKey = CommandRequestFingerprint.sha256Hex(idempotencyKey)
        val principal = access.principal
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = InformationRequestAuditActor.of(access),
                partyId = party.id,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${link.id}|${principal.kind}|${principal.id}|$commandKey",
                details = detailsOf(link),
                requireAuditCapture = true,
            ),
        )
    }

    fun recordCutoff(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        cutoff: InformationRequestCredentialCutoff,
    ) {
        val mutation = InformationRequestMutation.REVOKE_ACCESS_LINK
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = cutoff.actor,
                reasonCode = cutoff.reasonCode,
                partyId = party.id,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${link.id}|${cutoff.reasonCode.lowercase()}",
                details = detailsOf(link),
                requireAuditCapture = true,
            ),
        )
    }

    private fun detailsOf(link: ShareLink): Map<String, String> {
        val details = linkedMapOf(
            "shareLinkId" to link.id.toString(),
            "rotationCount" to link.rotationCount.toString(),
        )
        link.replacesShareLinkId?.let { details["replacesShareLinkId"] = it.toString() }
        link.expiresAt?.let { details["expiresAt"] = it.toInstant().toString() }
        link.maxUses?.let { details["maxUses"] = it.toString() }
        return details
    }
}
