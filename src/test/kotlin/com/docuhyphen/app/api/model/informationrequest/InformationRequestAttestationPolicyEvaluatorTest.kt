package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.PrincipalKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InformationRequestAttestationPolicyEvaluatorTest
{
    private val now: Instant = Instant.parse("2026-09-25T12:00:00Z")
    private val content = "a".repeat(64)
    private val preparer = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.PREPARER)
    private val attestor = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.ATTESTOR)
    private val secondAttestor = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.ATTESTOR)
    private var sequence = 0

    @Test
    fun `one assent from each required role satisfies a policy for the content it was given against`()
    {
        val evaluation = evaluate(
            policy(),
            listOf(assent(preparer), assent(attestor)),
        )
        assertEquals(InformationRequestAttestationState.SATISFIED, evaluation.state)
        assertEquals(2, evaluation.assentCount)
        assertEquals(2, evaluation.counted.size)

        val otherContent = evaluate(policy(), listOf(assent(preparer), assent(attestor, content = "b".repeat(64))))
        assertEquals(InformationRequestAttestationState.PENDING, otherContent.state)
        assertEquals(listOf(InformationRequestContributorRole.ATTESTOR), otherContent.missingRoles)
        assertEquals(1, otherContent.superseded)
    }

    @Test
    fun `a quorum needs more assents than one per role`()
    {
        val quorum = policy(minimumAssents = 3)
        assertEquals(
            InformationRequestAttestationState.PENDING,
            evaluate(quorum, listOf(assent(preparer), assent(attestor))).state,
        )
        assertEquals(
            InformationRequestAttestationState.SATISFIED,
            evaluate(quorum, listOf(assent(preparer), assent(attestor), assent(secondAttestor))).state,
        )
    }

    @Test
    fun `a party's latest decision governs and a refusal blocks until it assents again`()
    {
        val refused = evaluate(policy(), listOf(assent(preparer), assent(attestor), refusal(attestor)))
        assertEquals(InformationRequestAttestationState.REFUSED, refused.state)
        assertEquals(1, refused.refusals.size)

        val reconsidered = evaluate(
            policy(),
            listOf(assent(preparer), refusal(attestor), assent(attestor)),
        )
        assertEquals(InformationRequestAttestationState.SATISFIED, reconsidered.state)
        assertEquals(2, reconsidered.counted.size)
    }

    @Test
    fun `an expired assent, an ineligible party, and a weak or unreferenced assent do not count`()
    {
        val expiring = policy(validityHours = 1)
        assertEquals(
            InformationRequestAttestationState.PENDING,
            evaluate(expiring, listOf(assent(preparer), assent(attestor, expiresAt = now.minusSeconds(60)))).state,
        )

        val outsider = InformationRequestAttestingParty(UUID.randomUUID(), InformationRequestContributorRole.ATTESTOR)
        assertEquals(
            InformationRequestAttestationState.PENDING,
            InformationRequestAttestationPolicyEvaluator.evaluate(
                policy(),
                content,
                listOf(assent(preparer), assent(outsider)),
                listOf(preparer, attestor),
                now,
            ).state,
        )

        val strong = policy(strength = InformationRequestAuthenticationStrength.MULTI_FACTOR)
        assertEquals(
            InformationRequestAttestationState.PENDING,
            evaluate(
                strong,
                listOf(
                    assent(preparer, strength = InformationRequestAuthenticationStrength.MULTI_FACTOR),
                    assent(attestor, strength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN),
                ),
            ).state,
        )

        val referenced = policy(signature = InformationRequestExternalSignatureReferencePolicy.REQUIRED)
        assertEquals(
            InformationRequestAttestationState.PENDING,
            evaluate(referenced, listOf(assent(preparer, reference = "external-signing-1"), assent(attestor))).state,
        )
        assertEquals(
            InformationRequestAttestationState.SATISFIED,
            evaluate(
                referenced,
                listOf(assent(preparer, reference = "external-signing-1"), assent(attestor, reference = "external-signing-2")),
            ).state,
        )
    }

    @Test
    fun `a role sequence counts a role only after every earlier role has assented`()
    {
        val sequenced = policy(ordering = InformationRequestAttestationOrdering.ROLE_SEQUENCE)
        val outOfOrder = evaluate(sequenced, listOf(assent(attestor), assent(preparer)))
        assertEquals(InformationRequestAttestationState.PENDING, outOfOrder.state)
        assertEquals(listOf(InformationRequestContributorRole.ATTESTOR), outOfOrder.missingRoles)

        assertEquals(
            InformationRequestAttestationState.SATISFIED,
            evaluate(sequenced, listOf(assent(preparer), assent(attestor))).state,
        )
    }

    private fun evaluate(
        policy: InformationRequestAttestationPolicy,
        attestations: List<InformationRequestSubmissionAttestation>,
    ) = InformationRequestAttestationPolicyEvaluator.evaluate(
        policy,
        content,
        attestations,
        listOf(preparer, attestor, secondAttestor),
        now,
    )

    private fun policy(
        minimumAssents: Int = 2,
        ordering: InformationRequestAttestationOrdering = InformationRequestAttestationOrdering.ANY_ORDER,
        strength: InformationRequestAuthenticationStrength = InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
        validityHours: Int? = null,
        signature: InformationRequestExternalSignatureReferencePolicy = InformationRequestExternalSignatureReferencePolicy.OPTIONAL,
    ) = InformationRequestAttestationPolicy(
        bindingId = UUID.randomUUID(),
        requiredRoles = listOf(InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR),
        ordering = ordering,
        minimumAssentCount = minimumAssents,
        minimumAuthenticationStrength = strength,
        validityHours = validityHours,
        externalSignatureReference = signature,
        policyHash = "f".repeat(64),
    )

    private fun assent(
        party: InformationRequestAttestingParty,
        content: String = this.content,
        expiresAt: Instant? = null,
        strength: InformationRequestAuthenticationStrength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
        reference: String? = null,
    ) = attestation(party, InformationRequestAttestationDecision.ASSENTED, content, expiresAt, strength, reference)

    private fun refusal(party: InformationRequestAttestingParty) =
        attestation(
            party,
            InformationRequestAttestationDecision.REFUSED,
            content,
            null,
            InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
            null,
        ).apply { refusalReason = "The recorded items are incomplete" }

    @Suppress("LongParameterList")
    private fun attestation(
        party: InformationRequestAttestingParty,
        decision: InformationRequestAttestationDecision,
        content: String,
        expiresAt: Instant?,
        strength: InformationRequestAuthenticationStrength,
        reference: String?,
    ) = InformationRequestSubmissionAttestation().apply {
        sequence += 1
        partyId = party.partyId
        partyRole = party.role
        principalKind = PrincipalKind.USER
        principalId = UUID.randomUUID()
        this.decision = decision
        authenticationStrength = strength
        externalSignatureReference = reference
        attestedContentHashSha256 = content
        attestedAt = Timestamp.from(now.minus(10L - sequence, ChronoUnit.MINUTES))
        this.expiresAt = expiresAt?.let(Timestamp::from)
        sequenceNumber = sequence
    }
}
