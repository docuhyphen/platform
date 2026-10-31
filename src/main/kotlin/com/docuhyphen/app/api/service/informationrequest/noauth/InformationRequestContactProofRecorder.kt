package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp

@ApplicationScoped
class InformationRequestContactProofRecorder @Inject constructor(
    private val transitionHistory: InformationRequestTransitionHistoryService,
) {
    fun recordChallenge(request: InformationRequest, party: InformationRequestParty, link: ShareLink) {
        val mutation = InformationRequestMutation.ISSUE_CONTACT_CHALLENGE
        record(
            request,
            party,
            mutation,
            idempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${link.id}|${link.rotationCount}|" +
                    "${link.contactOtpChallengeCount}|${link.contactOtpExpiresAt?.toInstant()}",
            details = linkedMapOf(
                "shareLinkId" to link.id.toString(),
                "challengeCount" to link.contactOtpChallengeCount.toString(),
            ),
        )
    }

    fun recordRefusal(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        failedAttempt: Int
    ) {
        val details = linkedMapOf(
            "shareLinkId" to link.id.toString(),
            "failedAttempt" to failedAttempt.toString(),
        )
        link.contactOtpLockedUntil?.let { details["lockedUntil"] = it.toInstant().toString() }
        record(
            request,
            party,
            InformationRequestMutation.REFUSE_CONTACT_CHALLENGE,
            reasonCode = CONTACT_PROOF_INVALID,
            details = details,
        )
    }

    fun recordSessionIssued(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        session: RequestAccessSession,
    ) {
        val mutation = InformationRequestMutation.ISSUE_ACCESS_SESSION
        val details = linkedMapOf(
            "accessSessionId" to session.id.toString(),
            "shareLinkId" to link.id.toString(),
            "usedCount" to link.usedCount.toString(),
            "verificationStrength" to session.verificationStrength.name,
        )
        details["expiresAt"] = session.expiresAt.toInstant().toString()
        record(
            request,
            party,
            mutation,
            idempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${session.id}",
            details = details,
            session = session,
        )
    }

    fun recordStepUpChallenge(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        session: RequestAccessSession,
    ) {
        val mutation = InformationRequestMutation.ISSUE_CONTACT_CHALLENGE
        record(
            request,
            party,
            mutation,
            reasonCode = ACCESS_SESSION_STEP_UP,
            idempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${session.id}|${session.stepUpCodeExpiresAt?.toInstant()}",
            details = linkedMapOf(
                "accessSessionId" to session.id.toString(),
                "shareLinkId" to link.id.toString(),
                "challengeCount" to session.stepUpChallengeCount.toString(),
            ),
            session = session,
        )
    }

    fun recordStepUpRefusal(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        session: RequestAccessSession,
        failedAttempt: Int,
    ) {
        val details = linkedMapOf(
            "accessSessionId" to session.id.toString(),
            "shareLinkId" to link.id.toString(),
            "failedAttempt" to failedAttempt.toString(),
        )
        session.stepUpLockedUntil?.let { details["lockedUntil"] = it.toInstant().toString() }
        record(
            request,
            party,
            InformationRequestMutation.REFUSE_CONTACT_CHALLENGE,
            reasonCode = CONTACT_PROOF_INVALID,
            details = details,
            session = session,
        )
    }

    fun recordStepUp(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ShareLink,
        session: RequestAccessSession,
        verifiedUntil: Timestamp,
    ) {
        val mutation = InformationRequestMutation.STEP_UP_ACCESS_SESSION
        record(
            request,
            party,
            mutation,
            idempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${session.id}|${session.steppedUpAt?.toInstant()}",
            details = linkedMapOf(
                "accessSessionId" to session.id.toString(),
                "shareLinkId" to link.id.toString(),
                "verifiedUntil" to verifiedUntil.toInstant().toString(),
            ),
            session = session,
        )
    }

    private fun record(
        request: InformationRequest,
        party: InformationRequestParty,
        mutation: InformationRequestMutation,
        reasonCode: String? = null,
        idempotencyKey: String? = null,
        details: Map<String, String>,
        session: RequestAccessSession? = null,
    ) {
        val participant = PrincipalRef(
            kind = requireNotNull(party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(party.principalId) { "Information Request party has no principal" },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = session?.let { InformationRequestAuditActor.of(participant, it) }
                    ?: InformationRequestAuditActor.beforeSession(participant),
                reasonCode = reasonCode,
                partyId = party.id,
                auditIdempotencyKey = idempotencyKey,
                details = details,
                requireAuditCapture = true,
            ),
        )
    }

    private companion object {
        const val CONTACT_PROOF_INVALID = "CONTACT_PROOF_INVALID"
        const val ACCESS_SESSION_STEP_UP = "ACCESS_SESSION_STEP_UP"
    }
}
