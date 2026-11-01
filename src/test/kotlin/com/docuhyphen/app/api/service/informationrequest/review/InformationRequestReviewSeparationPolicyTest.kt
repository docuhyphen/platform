package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewPlan
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestReviewSeparationPolicyTest {
    private val requestId = UUID.randomUUID()
    private val contributor = PrincipalRef.user(UUID.randomUUID())
    private val unrelated = PrincipalRef.user(UUID.randomUUID())
    private val partyQueries: InformationRequestPartyQueryService = mock()
    private val decisionRepository: InformationRequestReviewDecisionRepository = mock()
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider = mock()
    private val evidenceQueries: InformationRequestEvidenceQueryService = mock()
    private val policy =
        InformationRequestReviewSeparationPolicy(partyQueries, decisionRepository, requirementContext, evidenceQueries)
    private val equivalents = mutableMapOf<UUID, Set<PrincipalRef>>()
    private val submissionPackage = InformationRequestSubmissionPackage().apply {
        informationRequestId = requestId
        templateVersionId = UUID.randomUUID()
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = contributor.kind
        submittedByPrincipalId = contributor.id
    }
    private val item = InformationRequestSubmissionItem().apply {
        packageId = submissionPackage.id
        informationRequestId = requestId
        informationRequestRequirementId = UUID.randomUUID()
        requirementRevisionId = UUID.randomUUID()
        templateBindingId = UUID.randomUUID()
        requirementKey = "recorded-item"
        requirementType = InformationRequestRequirementType.DOCUMENT
        occurrencePath = "root"
        completenessState = InformationRequestCompletenessItemState.COMPLETE
        disposition = InformationRequestResponseDisposition.PROVIDED
        respondedByPrincipalKind = contributor.kind
        respondedByPrincipalId = contributor.id
        itemHashSha256 = "c".repeat(64)
    }
    private val stage = InformationRequestReviewStagePlan(
        id = UUID.randomUUID(),
        stageKey = "independent-check",
        position = 1,
        title = "Independent check",
        aggregation = InformationRequestReviewAggregation.ANY,
        quorumCount = null,
        minimumReviewerCount = 1,
        tieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted = false,
        excludesResponseParties = true,
        excludesPriorReviewers = false,
        coveredSectionIds = emptySet(),
    )

    init {
        whenever(evidenceQueries.versionRecords(any())).thenReturn(emptyMap())
        whenever(requirementContext.linkedAccountPrincipalsOf(any())).thenReturn(emptySet())
        whenever(requirementContext.principalsActingFor(any())).thenAnswer { invocation ->
            val party = invocation.getArgument<InformationRequestParty>(0)
            val own = party.principalKind?.let { kind -> PrincipalRef(kind, requireNotNull(party.principalId)) }
            listOfNotNull(own).toSet() + equivalents[party.id].orEmpty()
        }
        parties()
    }

    @Test
    fun `a subject who can act on the request is a response party`() {
        val subject = PrincipalRef.user(UUID.randomUUID())
        parties(party(InformationRequestShareRoleKey.SUBJECT, subject))

        assertRefused(snapshot(), subject)
        assertDoesNotThrow { policy.requireMayReview(snapshot(), stage, setOf(unrelated)) }
    }

    @Test
    fun `whoever supplied a frozen evidence version is a response party`() {
        val uploader = PrincipalRef.user(UUID.randomUUID())
        val evidence = InformationRequestSubmissionEvidence().apply {
            packageId = submissionPackage.id
            itemId = item.id
            informationRequestId = requestId
            evidenceArtifactId = UUID.randomUUID()
            evidenceVersionId = UUID.randomUUID()
            conformance = "CONFORMING"
        }
        whenever(evidenceQueries.versionRecords(any())).thenReturn(
            mapOf(
                evidence.evidenceVersionId to InformationRequestEvidenceVersionRecord(
                    evidenceVersionId = evidence.evidenceVersionId,
                    evidenceArtifactId = evidence.evidenceArtifactId,
                    versionNumber = 1,
                    declaredFileName = "supplied-record.pdf",
                    createdBy = uploader,
                ),
            ),
        )

        assertRefused(snapshot(listOf(evidence)), uploader)
        assertDoesNotThrow { policy.requireMayReview(snapshot(listOf(evidence)), stage, setOf(unrelated)) }
    }

    @Test
    fun `a responding party that answered stays a response party after it is revoked, through every principal acting for it`() {
        val answered = party(InformationRequestShareRoleKey.CONTRIBUTOR, contributor, active = false)
        val linkedAccount = PrincipalRef.user(UUID.randomUUID())
        equivalents[answered.id] = setOf(linkedAccount)
        val silentPrincipal = PrincipalRef(PrincipalKind.PARTICIPANT, UUID.randomUUID())
        val silent = party(InformationRequestShareRoleKey.PREPARER, silentPrincipal, active = false)
        parties(answered, silent)

        assertRefused(snapshot(), linkedAccount)
        assertDoesNotThrow { policy.requireMayReview(snapshot(), stage, setOf(silentPrincipal)) }
    }

    @Test
    fun `an active responding party is a response party whether or not it answered`() {
        val preparer = PrincipalRef.user(UUID.randomUUID())
        parties(
            party(InformationRequestShareRoleKey.PREPARER, preparer),
            party(InformationRequestShareRoleKey.REVIEWER, unrelated)
        )

        assertRefused(snapshot(), preparer)
        assertRefused(snapshot(), contributor)
        assertDoesNotThrow { policy.requireMayReview(snapshot(), stage, setOf(unrelated)) }
    }

    @Test
    fun `an account linked to a recorded respondent stays a response party after the respondent's party is reassigned`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val linkedAccount = PrincipalRef.user(UUID.randomUUID())
        submissionPackage.submittedByPrincipalKind = participant.kind
        submissionPackage.submittedByPrincipalId = participant.id
        item.respondedByPrincipalKind = participant.kind
        item.respondedByPrincipalId = participant.id
        whenever(requirementContext.linkedAccountPrincipalsOf(participant)).thenReturn(setOf(linkedAccount))
        val reassigned = party(InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.user(UUID.randomUUID()))
        parties(reassigned, party(InformationRequestShareRoleKey.REVIEWER, linkedAccount))

        assertRefused(snapshot(), linkedAccount)
        assertDoesNotThrow { policy.requireMayReview(snapshot(), stage, setOf(unrelated)) }
    }

    @Test
    fun `a stage that excludes prior reviewers refuses whoever decided the reopened review, on a reconsideration or an appeal only`() {
        val priorReviewId = UUID.randomUUID()
        val priorReviewer = PrincipalRef.user(UUID.randomUUID())
        val carriedOnly = PrincipalRef.user(UUID.randomUUID())
        whenever(decisionRepository.findForReview(priorReviewId)).thenReturn(
            listOf(
                InformationRequestReviewDecision().apply {
                    kind = InformationRequestReviewDecisionKind.REVIEWER
                    decidedByPrincipalKind = priorReviewer.kind
                    decidedByPrincipalId = priorReviewer.id
                },
                InformationRequestReviewDecision().apply {
                    kind = InformationRequestReviewDecisionKind.CARRIED
                    decidedByPrincipalKind = carriedOnly.kind
                    decidedByPrincipalId = carriedOnly.id
                },
            ),
        )
        val excluding = stage.copy(excludesResponseParties = false, excludesPriorReviewers = true)

        listOf(InformationRequestReviewKind.RECONSIDERATION, InformationRequestReviewKind.APPEAL).forEach { kind ->
            val reopened = snapshot(reviewKind = kind, priorReviewId = priorReviewId)
            assertFalse(policy.permitsReview(reopened, excluding, setOf(priorReviewer)), kind.name)
            assertTrue(policy.permitsReview(reopened, excluding, setOf(carriedOnly)), kind.name)
            assertTrue(policy.permitsReview(reopened, excluding, setOf(unrelated)), kind.name)
        }
        val resubmission =
            snapshot(reviewKind = InformationRequestReviewKind.RESUBMISSION, priorReviewId = priorReviewId)
        assertTrue(policy.permitsReview(resubmission, excluding, setOf(priorReviewer)))
    }

    private fun assertRefused(snapshot: InformationRequestReviewSnapshot, principal: PrincipalRef) {
        val refusal = assertThrows<InformationRequestLifecycleException> {
            policy.requireMayReview(
                snapshot,
                stage,
                setOf(principal)
            )
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
    }

    private fun parties(vararg parties: InformationRequestParty) {
        whenever(partyQueries.partiesOf(requestId)).thenReturn(parties.toList())
    }

    private fun party(role: InformationRequestShareRoleKey, principal: PrincipalRef, active: Boolean = true) =
        InformationRequestParty().apply {
            informationRequestId = requestId
            roleKey = role
            principalKind = principal.kind
            principalId = principal.id
            this.active = active
            if (role == InformationRequestShareRoleKey.SUBJECT) subjectIdentityRefId = UUID.randomUUID()
        }

    private fun snapshot(
        evidence: List<InformationRequestSubmissionEvidence> = emptyList(),
        reviewKind: InformationRequestReviewKind = InformationRequestReviewKind.INITIAL,
        priorReviewId: UUID? = null,
    ) =
        InformationRequestReviewSnapshot(
            review = InformationRequestReview().apply {
                kind = reviewKind
                this.priorReviewId = priorReviewId
                informationRequestId = requestId
                packageId = submissionPackage.id
                templateVersionId = submissionPackage.templateVersionId
                openedByPrincipalKind = contributor.kind
                openedByPrincipalId = contributor.id
            },
            submission = InformationRequestSubmissionPackageView(
                submissionPackage,
                listOf(item),
                evidence,
                emptyList(),
                emptyList(),
                null
            ),
            plan = InformationRequestReviewPlan(
                submissionPackage.templateVersionId,
                InformationRequestReviewStageOrdering.SEQUENTIAL,
                listOf(stage)
            ),
            reviewedItems = listOf(item),
            coverage = mapOf(stage.stageKey to listOf(item.id)),
            assignments = emptyList(),
            decisions = emptyList(),
            findings = emptyList(),
            standing = InformationRequestReviewResult(emptyList(), null, emptyMap()),
        )
}
