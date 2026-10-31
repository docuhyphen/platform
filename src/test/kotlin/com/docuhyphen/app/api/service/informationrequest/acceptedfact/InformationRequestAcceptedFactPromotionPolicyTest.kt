package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.evidence.InMemoryEvidenceReceiptStore
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewLoader
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionPackageReader
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

class InformationRequestAcceptedFactPromotionPolicyTest {
    private val gate = mock<InformationRequestMutationGate>()
    private val loader = mock<InformationRequestReviewLoader>()
    private val lockService = mock<InformationRequestSubmissionLockService>()
    private val packageReader = mock<InformationRequestSubmissionPackageReader>()
    private val factRepository = mock<InformationRequestAcceptedFactRepository>()
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val fieldValueRevisions = mock<FieldValueRevisionQueryService>()
    private val facts = mock<InformationRequestAcceptedFactStanding>()
    private val restrictions = mock<InformationRequestSubjectRestrictionService>()
    private val saved = mutableListOf<InformationRequestAcceptedFact>()
    private val submittedAt: Instant = Instant.parse("2026-09-01T10:00:00Z")
    private val organizationId = UUID.randomUUID()
    private val subjectId = UUID.randomUUID()
    private val fieldDefinitionId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.user(UUID.randomUUID()),
        AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = true)
    )
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
    }
    private val submission = InformationRequestSubmissionPackage().apply {
        informationRequestId = request.id
        templateVersionId = request.templateVersionId
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = PrincipalKind.USER
        submittedByPrincipalId = UUID.randomUUID()
        submittedAt = Timestamp.from(this@InformationRequestAcceptedFactPromotionPolicyTest.submittedAt)
    }
    private val item = InformationRequestSubmissionItem().apply {
        packageId = submission.id
        informationRequestId = request.id
        informationRequestRequirementId = UUID.randomUUID()
        requirementRevisionId = UUID.randomUUID()
        templateBindingId = UUID.randomUUID()
        requirementKey = "recorded-note"
        requirementType = InformationRequestRequirementType.FIELD
        occurrencePath = "root"
        completenessState = InformationRequestCompletenessItemState.COMPLETE
        disposition = InformationRequestResponseDisposition.PROVIDED
        responseId = UUID.randomUUID()
        responseRevision = 2
        fieldValueRevisionId = UUID.randomUUID()
        itemHashSha256 = "c".repeat(64)
    }
    private val service = InformationRequestAcceptedFactService(
        gate = gate,
        loader = loader,
        lockService = lockService,
        packageReader = packageReader,
        factRepository = factRepository,
        revocationRepository = mock(),
        partyRepository = partyRepository,
        bindingRepository = bindingRepository,
        fieldValueRevisions = fieldValueRevisions,
        facts = facts,
        evidenceReferences = mock(),
        restrictions = restrictions,
        commandReceiptService = CommandReceiptService(InMemoryEvidenceReceiptStore(), CommandReceiptScope()),
        transitionHistory = mock(),
        clock = Clock.systemUTC(),
        disclosure = mock(),
    )

    init {
        whenever(gate.lock(request.id)).thenReturn(LockedInformationRequest(Exchange(), request))
        whenever(lockService.activePackages(request.id)).thenReturn(listOf(submission))
        whenever(packageReader.view(request.id, submission.id))
            .thenReturn(
                InformationRequestSubmissionPackageView(
                    submission,
                    listOf(item),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    null
                )
            )
        whenever(
            partyRepository.findActiveForRequestRole(
                request.id,
                InformationRequestShareRoleKey.SUBJECT
            )
        ).thenReturn(listOf(subjectParty(subjectId)))
        whenever(partyRepository.findForRequest(request.id)).thenReturn(listOf(subjectParty(subjectId)))
        whenever(bindingRepository.findById(item.templateBindingId))
            .thenReturn(InformationRequestTemplateRequirementBinding().apply {
                collectedFieldDefinitionId = fieldDefinitionId
            })
        whenever(fieldValueRevisions.valueOf(requireNotNull(item.fieldValueRevisionId))).thenReturn(
            FieldValueRevisionValue(
                requireNotNull(item.fieldValueRevisionId),
                UUID.randomUUID(),
                FieldValueType.SHORT_TEXT,
                false,
                JsonPrimitive("Recorded answer")
            ),
        )
        whenever(facts.activeForKey(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(factRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestAcceptedFact>(0).also(saved::add)
        }
    }

    @Test
    fun `an answer its review waived is promoted as declared rather than reviewed`() {
        submission.reviewRequired = true
        val review = InformationRequestReview().apply {
            informationRequestId = request.id
            packageId = submission.id
            templateVersionId = request.templateVersionId
            state = InformationRequestReviewState.SATISFIED
            openedByPrincipalKind = PrincipalKind.USER
            openedByPrincipalId = UUID.randomUUID()
        }
        whenever(loader.latestForPackage(request.id, submission.id)).thenReturn(review)
        val snapshot = mock<InformationRequestReviewSnapshot> {
            on { standing } doReturn InformationRequestReviewResult(
                emptyList(),
                InformationRequestReviewState.SATISFIED,
                mapOf(item.id to InformationRequestReviewOutcome.WAIVED)
            )
        }
        whenever(loader.snapshot(review)).thenReturn(snapshot)

        service.promote(command("promote-waived"))

        assertEquals(InformationRequestAcceptedFactConfidence.DECLARED, saved.single().confidence)
        assertNull(saved.single().sourceReviewId)
    }

    @Test
    fun `superseding a fact from another request needs the authority that revoking it there would need`() {
        val otherRequestId = UUID.randomUUID()
        val existing = InformationRequestAcceptedFact().apply {
            sourceInformationRequestId = otherRequestId
            canonicalValue = "\"Earlier answer\""
            promotedAt = Timestamp.from(submittedAt.minus(Duration.ofDays(30)))
        }
        whenever(facts.activeForKey(any(), any(), any(), any(), any())).thenReturn(listOf(existing))
        doThrow(ForbiddenException("Access denied")).whenever(gate)
            .authorizeRequest(any(), eq(listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT)), eq(otherRequestId))

        assertThrows(ForbiddenException::class.java) {
            service.promote(command("supersede-elsewhere").copy(supersedesFactId = existing.id))
        }

        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a value is not attributed to a subject who replaced the one its package answered for`() {
        val laterSubjectId = UUID.randomUUID()
        val replaced = subjectParty(subjectId).apply {
            active = false
            revokedAt = Timestamp.from(submittedAt.plus(Duration.ofHours(1)))
        }
        val replacement =
            subjectParty(laterSubjectId).apply { assignedAt = Timestamp.from(submittedAt.plus(Duration.ofHours(1))) }
        whenever(
            partyRepository.findActiveForRequestRole(
                request.id,
                InformationRequestShareRoleKey.SUBJECT
            )
        ).thenReturn(listOf(replacement))
        whenever(partyRepository.findForRequest(request.id)).thenReturn(listOf(replaced, replacement))

        val refusal =
            assertThrows(InformationRequestLifecycleException::class.java) { service.promote(command("promote-after-subject-change")) }

        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_SUBJECT_REQUIRED, refusal.reasonCode)
        assertTrue(saved.isEmpty())
    }

    private fun subjectParty(subjectIdentityRefId: UUID) = InformationRequestParty().apply {
        informationRequestId = request.id
        roleKey = InformationRequestShareRoleKey.SUBJECT
        this.subjectIdentityRefId = subjectIdentityRefId
        assignedAt = Timestamp.from(submittedAt.minus(Duration.ofDays(1)))
    }

    private fun command(key: String) = PromoteInformationRequestAcceptedFactCommand(
        requestId = request.id,
        packageId = submission.id,
        submissionItemId = item.id,
        purposeKey = "profile.reuse",
        policyBasisKey = "policy.reuse",
        visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
        access = access,
        idempotencyKey = key,
    )
}
