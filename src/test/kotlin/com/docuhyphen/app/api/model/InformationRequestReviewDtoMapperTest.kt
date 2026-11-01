package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestCorrectionEvidenceDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.RevisionETag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestReviewDtoMapperTest {
    private val requestId = UUID.randomUUID()
    private val caller = PrincipalRef.user(UUID.randomUUID())
    private val submissionPackage = InformationRequestSubmissionPackage().apply {
        informationRequestId = requestId
        packageNumber = 2
        templateVersionId = UUID.randomUUID()
        contentHashSha256 = "a".repeat(64)
        manifestHashSha256 = "b".repeat(64)
        submittedByPrincipalKind = caller.kind
        submittedByPrincipalId = UUID.randomUUID()
    }
    private val shownItem = item("shown_record")
    private val withheldItem = item("withheld_record")
    private val file = InformationRequestSubmissionEvidence().apply {
        packageId = submissionPackage.id
        itemId = shownItem.id
        informationRequestId = requestId
        evidenceArtifactId = UUID.randomUUID()
        evidenceVersionId = UUID.randomUUID()
        evidenceVersionNumber = 3
        conformance = "CONFORMING"
    }
    private val review = InformationRequestReview().apply {
        informationRequestId = requestId
        packageId = submissionPackage.id
        reviewNumber = 2
        kind = InformationRequestReviewKind.RESUBMISSION
        priorReviewId = UUID.randomUUID()
        templateVersionId = submissionPackage.templateVersionId
        state = InformationRequestReviewState.CHANGES_REQUESTED
        reviewRevision = 4
        openedByPrincipalKind = caller.kind
        openedByPrincipalId = caller.id
    }
    private val assignmentId = UUID.randomUUID()
    private val finding = finding(review.id, shownItem, assignmentId)
    private val earlierFinding = finding(requireNotNull(review.priorReviewId), shownItem, UUID.randomUUID())
    private val earlierRemediation = InformationRequestReviewRemediation().apply {
        informationRequestId = requestId
        correctionId = UUID.randomUUID()
        findingId = earlierFinding.id
        remediatedByPackageId = submissionPackage.id
        remediatedByItemId = shownItem.id
    }
    private val correction = InformationRequestCorrection().apply {
        informationRequestId = requestId
        reviewId = review.id
        packageId = submissionPackage.id
        state = InformationRequestCorrectionState.OPEN
    }
    private val shownCorrectionItem = correctionItem(shownItem)
    private val withheldCorrectionItem = correctionItem(withheldItem)
    private val withheldFileVersion = UUID.randomUUID()
    private val correctionView = InformationRequestReviewCorrectionView(
        correction = correction,
        items = listOf(shownCorrectionItem, withheldCorrectionItem),
        evidence = listOf(
            InformationRequestReturnedEvidence(
                shownCorrectionItem.id,
                file.evidenceArtifactId,
                file.evidenceVersionId,
                3,
                "returned-record.pdf"
            ),
            InformationRequestReturnedEvidence(
                withheldCorrectionItem.id,
                UUID.randomUUID(),
                withheldFileVersion,
                1,
                "withheld-record.pdf"
            ),
        ),
    )
    private val shownEntry = InformationRequestCorrectionEvidenceDto(
        file.evidenceArtifactId,
        file.evidenceVersionId,
        3,
        "returned-record.pdf"
    )

    @Test
    fun `the reviewer view names returned files only for items it may review, binds findings to their review and assignment, and carries earlier findings and reconsideration`() {
        val view = InformationRequestSubmissionPackageView(
            submissionPackage,
            listOf(shownItem, withheldItem),
            listOf(file),
            emptyList(),
            emptyList(),
            null
        )
        val readable = InformationRequestReadableReview(
            snapshot = InformationRequestReviewSnapshot(
                review = review,
                submission = view,
                plan = InformationRequestReviewPlan(
                    submissionPackage.templateVersionId,
                    InformationRequestReviewStageOrdering.SEQUENTIAL,
                    emptyList()
                ),
                reviewedItems = emptyList(),
                coverage = emptyMap(),
                assignments = emptyList(),
                decisions = emptyList(),
                findings = listOf(finding),
                standing = InformationRequestReviewResult(emptyList(), null, emptyMap()),
            ),
            contentVisibleItemIds = setOf(shownItem.id),
            fieldValues = emptyMap(),
            evidenceVersions = mapOf(
                file.evidenceVersionId to InformationRequestEvidenceVersionRecord(
                    file.evidenceVersionId,
                    file.evidenceArtifactId,
                    3,
                    "returned-record.pdf",
                    caller
                ),
            ),
            findings = listOf(finding),
            comments = emptyList(),
            correction = correctionView,
            remediations = emptyList(),
            earlierFindings = listOf(earlierFinding),
            earlierRemediations = listOf(earlierRemediation),
            callerAssignmentIds = emptySet(),
            drafts = emptyMap(),
            canManage = true,
            canReconsider = true,
        )

        val dto = InformationRequestReviewDtoMapper.toDto(readable, caller)

        assertEquals(review.id, dto.findings.single().reviewId)
        assertEquals(assignmentId, dto.findings.single().assignmentId)
        assertEquals(listOf(earlierFinding.id), dto.earlierFindings.map { it.id })
        assertEquals(review.priorReviewId, dto.earlierFindings.single().reviewId)
        assertEquals(listOf(earlierFinding.id), dto.earlierRemediations.map { it.findingId })
        assertTrue(dto.canReconsider)
        assertEquals(
            listOf("returned-record.pdf"),
            dto.items.single { it.submissionItemId == shownItem.id }.evidence.map { it.declaredFileName },
        )
        assertEquals(listOf(shownEntry), dto.correction!!.evidence)
        assertEquals(listOf(shownItem.informationRequestRequirementId), dto.correction.requirementIds)
        assertEquals(1, dto.correction.undisclosedItemCount)
    }

    @Test
    fun `the reviewer view states each worksheet's draft revision beside its ETag`() {
        val assignment = InformationRequestReviewAssignment().apply {
            reviewId = review.id
            informationRequestId = requestId
            templateReviewStageId = UUID.randomUUID()
            stageKey = "review"
            reviewerPartyId = UUID.randomUUID()
            reviewerPrincipalKind = caller.kind
            reviewerPrincipalId = caller.id
            assignedByPrincipalKind = caller.kind
            assignedByPrincipalId = caller.id
            draftRevision = 5
        }
        val entry = InformationRequestReviewDraftItem().apply {
            assignmentId = assignment.id
            reviewId = review.id
            submissionItemId = shownItem.id
            outcome = InformationRequestReviewOutcome.SATISFIED
            narrative = "Checked against the register"
        }
        val view = InformationRequestSubmissionPackageView(
            submissionPackage,
            listOf(shownItem),
            emptyList(),
            emptyList(),
            emptyList(),
            null
        )
        val readable = InformationRequestReadableReview(
            snapshot = InformationRequestReviewSnapshot(
                review = review,
                submission = view,
                plan = InformationRequestReviewPlan(
                    submissionPackage.templateVersionId,
                    InformationRequestReviewStageOrdering.SEQUENTIAL,
                    emptyList()
                ),
                reviewedItems = emptyList(),
                coverage = emptyMap(),
                assignments = listOf(assignment),
                decisions = emptyList(),
                findings = emptyList(),
                standing = InformationRequestReviewResult(emptyList(), null, emptyMap()),
            ),
            contentVisibleItemIds = setOf(shownItem.id),
            fieldValues = emptyMap(),
            evidenceVersions = emptyMap(),
            findings = emptyList(),
            comments = emptyList(),
            correction = null,
            remediations = emptyList(),
            earlierFindings = emptyList(),
            earlierRemediations = emptyList(),
            callerAssignmentIds = setOf(assignment.id),
            drafts = mapOf(assignment.id to listOf(entry)),
            canManage = false,
            canReconsider = false,
        )

        val worksheet = InformationRequestReviewDtoMapper.toDto(readable, caller).worksheets.single()

        assertEquals(assignment.id, worksheet.assignmentId)
        assertEquals(RevisionETag.of(assignment.id, 5), worksheet.draftETag)
        assertEquals(5L, worksheet.draftRevision)
        assertEquals(listOf("Checked against the register"), worksheet.entries.map { it.narrative })
    }

    @Test
    fun `respondents see named returned files only for requirements they may view and never the assignment behind a finding`() {
        val result = InformationRequestRespondentReviewResult(
            review = review,
            packageNumber = submissionPackage.packageNumber,
            stageKey = null,
            visibleFindings = listOf(finding),
            visibleComments = emptyList(),
            correction = correctionView,
            visibleRequirementIds = setOf(shownItem.informationRequestRequirementId),
            remediations = emptyList(),
            canAppeal = true,
            canComment = true,
        )

        val dto = InformationRequestReviewDtoMapper.toDto(result, caller)

        assertNull(dto.findings.single().assignmentId)
        assertEquals(review.id, dto.findings.single().reviewId)
        assertEquals(listOf(shownEntry), dto.correction!!.evidence)
        assertEquals(1, dto.correction.undisclosedItemCount)
    }

    private fun item(key: String) = InformationRequestSubmissionItem().apply {
        packageId = submissionPackage.id
        informationRequestId = requestId
        informationRequestRequirementId = UUID.randomUUID()
        requirementRevisionId = UUID.randomUUID()
        templateBindingId = UUID.randomUUID()
        requirementKey = key
        requirementType = InformationRequestRequirementType.DOCUMENT
        occurrencePath = "root"
        completenessState = InformationRequestCompletenessItemState.COMPLETE
        disposition = InformationRequestResponseDisposition.PROVIDED
        itemHashSha256 = "c".repeat(64)
    }

    private fun finding(reviewId: UUID, item: InformationRequestSubmissionItem, assignment: UUID) =
        InformationRequestReviewFinding().apply {
            this.reviewId = reviewId
            informationRequestId = requestId
            packageId = submissionPackage.id
            submissionItemId = item.id
            requirementId = item.informationRequestRequirementId
            evidenceVersionId = file.evidenceVersionId
            assignmentId = assignment
            reasonCode = "record.unreadable"
            narrative = "The file cannot be read"
            severity = InformationRequestFindingSeverity.MAJOR
            visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE
            correctionScope = InformationRequestFindingCorrectionScope.EVIDENCE_VERSION
            recordedByPrincipalKind = caller.kind
            recordedByPrincipalId = caller.id
        }

    private fun correctionItem(item: InformationRequestSubmissionItem) = InformationRequestCorrectionItem().apply {
        correctionId = correction.id
        informationRequestId = requestId
        submissionItemId = item.id
        requirementId = item.informationRequestRequirementId
    }
}
