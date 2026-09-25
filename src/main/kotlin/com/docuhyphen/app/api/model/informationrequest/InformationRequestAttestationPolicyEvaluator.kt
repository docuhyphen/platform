package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import java.time.Instant

object InformationRequestAttestationPolicyEvaluator
{
    fun evaluate(
        policy: InformationRequestAttestationPolicy,
        contentHash: String,
        attestations: List<InformationRequestSubmissionAttestation>,
        parties: List<InformationRequestAttestingParty>,
        asOf: Instant,
    ): InformationRequestAttestationEvaluation
    {
        val roleByParty = parties
            .filter { it.role in policy.requiredRoles }
            .associate { it.partyId to it.role }
        val eligible = attestations.filter { roleByParty[it.partyId] == it.partyRole }
        val current = eligible.filter { it.attestedContentHashSha256 == contentHash && isUnexpired(it, asOf) }
        val governing = current
            .groupBy { it.partyId }
            .map { (_, decisions) -> decisions.maxBy { it.sequenceNumber } }
        val refusals = governing.filter { it.decision == InformationRequestAttestationDecision.REFUSED }
        val assents = governing
            .filter { it.decision == InformationRequestAttestationDecision.ASSENTED }
            .filter { meetsStrength(policy, it) && meetsReferenceRule(policy, it) }
        val counted = inRoleOrder(policy, assents)
        val missingRoles = policy.requiredRoles.filter { role -> counted.none { it.partyRole == role } }

        val state = when
        {
            refusals.isNotEmpty() -> InformationRequestAttestationState.REFUSED
            missingRoles.isEmpty() && counted.size >= policy.minimumAssentCount -> InformationRequestAttestationState.SATISFIED
            else -> InformationRequestAttestationState.PENDING
        }
        return InformationRequestAttestationEvaluation(
            state = state,
            counted = counted.sortedBy { it.sequenceNumber },
            refusals = refusals,
            missingRoles = missingRoles,
            assentCount = counted.size,
            requiredAssentCount = policy.minimumAssentCount,
            superseded = eligible.count { it !in current },
        )
    }

    private fun inRoleOrder(
        policy: InformationRequestAttestationPolicy,
        assents: List<InformationRequestSubmissionAttestation>,
    ): List<InformationRequestSubmissionAttestation>
    {
        if (policy.ordering != InformationRequestAttestationOrdering.ROLE_SEQUENCE) return assents
        val accepted = mutableListOf<InformationRequestSubmissionAttestation>()
        var previousFirst = Int.MIN_VALUE
        for (role in policy.requiredRoles)
        {
            val ofRole = assents.filter { it.partyRole == role }
            val first = ofRole.minOfOrNull { it.sequenceNumber } ?: break
            if (first < previousFirst) break
            accepted += ofRole
            previousFirst = first
        }
        return accepted
    }

    private fun isUnexpired(attestation: InformationRequestSubmissionAttestation, asOf: Instant): Boolean =
        attestation.expiresAt?.toInstant()?.isAfter(asOf) ?: true

    private fun meetsStrength(
        policy: InformationRequestAttestationPolicy,
        attestation: InformationRequestSubmissionAttestation,
    ): Boolean = attestation.authenticationStrength.rank >= policy.minimumAuthenticationStrength.rank

    private fun meetsReferenceRule(
        policy: InformationRequestAttestationPolicy,
        attestation: InformationRequestSubmissionAttestation,
    ): Boolean =
        policy.externalSignatureReference != InformationRequestExternalSignatureReferencePolicy.REQUIRED ||
            !attestation.externalSignatureReference.isNullOrBlank()

    fun rolesBefore(
        policy: InformationRequestAttestationPolicy,
        role: InformationRequestContributorRole,
    ): List<InformationRequestContributorRole> =
        if (policy.ordering == InformationRequestAttestationOrdering.ROLE_SEQUENCE)
            policy.requiredRoles.takeWhile { it != role }
        else
            emptyList()
}
