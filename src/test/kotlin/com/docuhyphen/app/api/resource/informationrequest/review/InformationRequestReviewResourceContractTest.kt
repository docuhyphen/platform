package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestReviewCommandResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewWorksheetDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.resource.informationrequest.review.operations.*
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.review.*
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestReviewResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val reviewId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))

    private val assignments: InformationRequestReviewAssignmentService = mock()
    private val decisions: InformationRequestReviewDecisionService = mock()
    private val findings: InformationRequestReviewFindingService = mock()
    private val comments: InformationRequestReviewCommentService = mock()
    private val cycles: InformationRequestReviewCycleService = mock()
    private val queries: InformationRequestReviewQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService =
        InformationRequestNoAuthAccessDoubles.current()

    private val request = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        state = InformationRequestState.IN_PROGRESS
    }
    private val review = InformationRequestReview().apply {
        id = reviewId
        informationRequestId = requestId
        packageId = UUID.randomUUID()
        templateVersionId = request.templateVersionId
        state = InformationRequestReviewState.IN_REVIEW
        reviewRevision = 3
        openedByPrincipalKind = PrincipalKind.USER
        openedByPrincipalId = principal.id
    }
    private val assignment = InformationRequestReviewAssignment().apply {
        id = assignmentId
        reviewId = this@InformationRequestReviewResourceContractTest.reviewId
        informationRequestId = requestId
        templateReviewStageId = UUID.randomUUID()
        stageKey = "review"
        reviewerPartyId = UUID.randomUUID()
        reviewerPrincipalKind = PrincipalKind.USER
        reviewerPrincipalId = UUID.randomUUID()
        assignedByPrincipalKind = PrincipalKind.USER
        assignedByPrincipalId = principal.id
    }
    private val result = InformationRequestReviewCommandResult(
        request = request,
        review = review,
        reviewETag = "\"review:3\"",
        responseETag = "\"responses:2\"",
        assignment = assignment,
        draftETag = "\"draft:1\"",
    )

    private val resource = InformationRequestReviewResource(
        assignments,
        decisions,
        findings,
        comments,
        cycles,
        queries,
        accessContextFactory
    )
    private val resultResource = InformationRequestReviewResultResource(
        assignments,
        decisions,
        findings,
        comments,
        cycles,
        queries,
        accessContextFactory
    )
    private val queueResource = InformationRequestReviewQueueResource(
        assignments,
        decisions,
        findings,
        comments,
        cycles,
        queries,
        accessContextFactory
    )
    private val noAuthResource = InformationRequestNoAuthReviewResource(
        assignments,
        decisions,
        findings,
        comments,
        cycles,
        queries,
        readAccessService
    )
    private val noAuthResultResource = InformationRequestNoAuthReviewResultResource(
        assignments,
        decisions,
        findings,
        comments,
        cycles,
        queries,
        readAccessService
    )

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queries.packagePosition(any())).thenReturn(1 to null)
        whenever(readAccessService.resolve(eq(requestId), eq("link-token"), anyOrNull())).thenReturn(noAuthAccess)
    }

    @Test
    fun `review resources are subordinate to the request on both surfaces and the queue belongs to the caller`()
    {
        assertEquals(
            "/information-requests/{id}/reviews",
            pathOf(InformationRequestReviewResourceOperations::class.java)
        )
        assertEquals(
            "/information-requests/{id}/review-results",
            pathOf(InformationRequestReviewResultResourceOperations::class.java)
        )
        assertEquals(
            "/information-request-reviews",
            pathOf(InformationRequestReviewQueueResourceOperations::class.java)
        )
        assertEquals(
            "no-auth/information-requests/{id}/reviews",
            pathOf(InformationRequestNoAuthReviewResourceOperations::class.java)
        )
        assertEquals(
            "no-auth/information-requests/{id}/review-results",
            pathOf(InformationRequestNoAuthReviewResultResourceOperations::class.java)
        )
    }

    @Test
    fun `an assignment delegates its stage, reviewer, due instant, precondition, and key and answers the review`()
    {
        whenever(assignments.assign(any())).thenReturn(result)
        val due = Timestamp.from(Instant.now().plusSeconds(3600))
        val reviewerPartyId = UUID.randomUUID()

        val response = resource.assign(
            requestId.toString(),
            reviewId.toString(),
            AssignInformationRequestReviewerRequest("review", reviewerPartyId, due),
            "\"review:3\"",
            " assign-key ",
        )

        assertEquals(201, response.status)
        assertEquals(result.reviewETag, response.getHeaderString("ETag"))
        assertEquals(assignmentId, (response.entity as InformationRequestReviewCommandResultDto).assignmentId)
        val captured = argumentCaptor<AssignInformationRequestReviewerCommand>()
        verify(assignments).assign(captured.capture())
        assertEquals(reviewerPartyId, captured.firstValue.reviewerPartyId)
        assertEquals(due.toInstant(), captured.firstValue.dueAt)
        assertEquals(CommandPrecondition.ExpectedRevision("\"review:3\""), captured.firstValue.precondition)
        assertEquals("assign-key", captured.firstValue.idempotencyKey)
        assertEquals(access, captured.firstValue.access)
    }

    @Test
    fun `a worksheet change needs a precondition and answers the new worksheet revision`()
    {
        assignment.draftRevision = 2
        whenever(decisions.saveDraft(any()))
            .thenThrow(CommandPreconditionException.required("\"draft:1\""))
            .thenReturn(InformationRequestReviewDraftResult(assignment, emptyList(), "\"draft:2\""))
        val missing = resource.saveWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            SaveInformationRequestReviewWorksheetRequest(
                listOf(
                    InformationRequestReviewWorksheetEntryRequest(
                        UUID.randomUUID(),
                        InformationRequestReviewOutcome.SATISFIED
                    )
                )
            ),
            null,
        )
        assertEquals(428, missing.status)

        val saved = resource.saveWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            SaveInformationRequestReviewWorksheetRequest(
                listOf(
                    InformationRequestReviewWorksheetEntryRequest(
                        UUID.randomUUID(),
                        clear = true
                    )
                )
            ),
            "\"draft:1\"",
        )
        assertEquals(200, saved.status)
        assertEquals("\"draft:2\"", saved.getHeaderString("ETag"))
        assertEquals("\"draft:2\"", (saved.entity as InformationRequestReviewWorksheetDto).draftETag)
        assertEquals(2L, (saved.entity as InformationRequestReviewWorksheetDto).draftRevision)
        val captured = argumentCaptor<SaveInformationRequestReviewDraftCommand>()
        verify(decisions, org.mockito.kotlin.times(2)).saveDraft(captured.capture())
        assertEquals(CommandPrecondition.Absent, captured.firstValue.precondition)
        assertTrue(captured.secondValue.patches.single().clear)
    }

    @Test
    fun `a missing idempotency key and a settled review are refused with stable answers`()
    {
        val keyless = resource.recordWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            "\"draft:1\"",
            null
        )
        assertEquals(400, keyless.status)

        whenever(decisions.recordDecisions(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_SETTLED,
                "This review has settled"
            ),
        )
        val settled = resource.recordWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            "\"draft:1\"",
            "record-key"
        )
        assertEquals(409, settled.status)
        assertEquals(InformationRequestErrorCatalog.REVIEW_SETTLED, (settled.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a wildcard If-Match never stands in for the review or worksheet ETag`() {
        whenever(assignments.assign(any())).thenReturn(result)
        whenever(assignments.change(any())).thenReturn(result)
        whenever(decisions.saveDraft(any())).thenReturn(
            InformationRequestReviewDraftResult(
                assignment,
                emptyList(),
                "\"draft:2\""
            )
        )
        whenever(decisions.recordDecisions(any())).thenReturn(result)
        whenever(cycles.reopen(any())).thenReturn(result)

        resource.assign(
            requestId.toString(),
            reviewId.toString(),
            AssignInformationRequestReviewerRequest("review", UUID.randomUUID(), null),
            "*",
            "assign-any"
        )
        resource.recuse(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(
                reasonCode = "conflict.declared",
                narrative = "A prior working relationship"
            ),
            "*",
            "recuse-any",
        )
        resource.saveWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            SaveInformationRequestReviewWorksheetRequest(
                listOf(
                    InformationRequestReviewWorksheetEntryRequest(
                        UUID.randomUUID(),
                        clear = true
                    )
                )
            ),
            "*",
        )
        resource.recordWorksheet(requestId.toString(), reviewId.toString(), assignmentId.toString(), "*", "record-any")
        resource.reconsider(
            requestId.toString(),
            reviewId.toString(),
            ReopenInformationRequestReviewRequest("The file was complete"),
            "*",
            "reconsider-any"
        )

        val assignCommand = argumentCaptor<AssignInformationRequestReviewerCommand>()
        verify(assignments).assign(assignCommand.capture())
        val changeCommand = argumentCaptor<ChangeInformationRequestReviewAssignmentCommand>()
        verify(assignments).change(changeCommand.capture())
        val draftCommand = argumentCaptor<SaveInformationRequestReviewDraftCommand>()
        verify(decisions).saveDraft(draftCommand.capture())
        val recordCommand = argumentCaptor<RecordInformationRequestReviewDecisionsCommand>()
        verify(decisions).recordDecisions(recordCommand.capture())
        val reopenCommand = argumentCaptor<ReopenInformationRequestReviewCommand>()
        verify(cycles).reopen(reopenCommand.capture())
        assertEquals(
            List(5) { CommandPrecondition.Absent },
            listOf(
                assignCommand.firstValue.precondition,
                changeCommand.firstValue.precondition,
                draftCommand.firstValue.precondition,
                recordCommand.firstValue.precondition,
                reopenCommand.firstValue.precondition,
            ),
        )
    }

    @Test
    fun `a reconsideration is an administrator reopening and an appeal reaches the same service from either surface`()
    {
        whenever(cycles.reopen(any())).thenReturn(result)

        val reconsidered = resource.reconsider(
            requestId.toString(),
            reviewId.toString(),
            ReopenInformationRequestReviewRequest("The file was complete"),
            "\"review:3\"",
            "reconsider-key",
        )
        val appealed = noAuthResource.appeal(
            requestId.toString(),
            reviewId.toString(),
            ReopenInformationRequestReviewRequest("The file was complete"),
            "link-token",
            "session-token",
            "\"review:3\"",
            "appeal-key",
        )

        assertEquals(201, reconsidered.status)
        assertEquals(201, appealed.status)
        val captured = argumentCaptor<ReopenInformationRequestReviewCommand>()
        verify(cycles, org.mockito.kotlin.times(2)).reopen(captured.capture())
        assertEquals(InformationRequestReviewKind.RECONSIDERATION, captured.firstValue.kind)
        assertEquals(access, captured.firstValue.access)
        assertEquals(InformationRequestReviewKind.APPEAL, captured.secondValue.kind)
        assertEquals(noAuthAccess, captured.secondValue.access)
    }

    @Test
    fun `a finding names the assignment it is recorded through and answers the recorded finding`() {
        val finding = InformationRequestReviewFinding().apply {
            reviewId = this@InformationRequestReviewResourceContractTest.reviewId
            informationRequestId = requestId
            packageId = review.packageId
            submissionItemId = UUID.randomUUID()
            requirementId = UUID.randomUUID()
            assignmentId = this@InformationRequestReviewResourceContractTest.assignmentId
            reasonCode = "record.incomplete"
            narrative = "The note does not say what was asked"
            severity = InformationRequestFindingSeverity.MAJOR
            visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE
            correctionScope = InformationRequestFindingCorrectionScope.RESPONSE
            recordedByPrincipalKind = principal.kind
            recordedByPrincipalId = principal.id
        }
        whenever(findings.record(any())).thenReturn(result.copy(finding = finding))
        val itemId = UUID.randomUUID()

        val response = resource.finding(
            requestId.toString(),
            reviewId.toString(),
            RecordInformationRequestReviewFindingRequest(
                submissionItemId = itemId,
                assignmentId = assignmentId,
                reasonCode = "record.incomplete",
                narrative = "The note does not say what was asked",
                severity = InformationRequestFindingSeverity.MAJOR,
                visibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                correctionScope = InformationRequestFindingCorrectionScope.RESPONSE,
            ),
            " finding-key ",
        )

        assertEquals(201, response.status)
        val body = response.entity as InformationRequestReviewCommandResultDto
        assertEquals(finding.id, body.findingId)
        assertEquals(assignmentId, body.assignmentId)
        val captured = argumentCaptor<RecordInformationRequestReviewFindingCommand>()
        verify(findings).record(captured.capture())
        assertEquals(assignmentId, captured.firstValue.assignmentId)
        assertEquals(itemId, captured.firstValue.submissionItemId)
        assertEquals("finding-key", captured.firstValue.idempotencyKey)
        assertEquals(access, captured.firstValue.access)
    }

    @Test
    fun `a recusal delegates its reason, narrative, precondition, and key to the assignment service`() {
        whenever(assignments.change(any())).thenReturn(result)

        val response = resource.recuse(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(
                reasonCode = "conflict.declared",
                narrative = "A prior working relationship"
            ),
            "\"review:3\"",
            "recuse-key",
        )

        assertEquals(200, response.status)
        assertEquals(result.reviewETag, response.getHeaderString("ETag"))
        val command = capturedChange()
        assertEquals(InformationRequestReviewAssignmentChange.RECUSAL, command.change)
        assertEquals(assignmentId, command.assignmentId)
        assertEquals("conflict.declared", command.reasonCode)
        assertEquals("A prior working relationship", command.narrative)
        assertEquals(CommandPrecondition.ExpectedRevision("\"review:3\""), command.precondition)
        assertEquals("recuse-key", command.idempotencyKey)
        assertEquals(access, command.access)
    }

    @Test
    fun `a delegation names the delegate party and a missing precondition is refused as required`() {
        val delegatePartyId = UUID.randomUUID()
        whenever(assignments.change(any()))
            .thenThrow(CommandPreconditionException.required("\"review:3\""))
            .thenReturn(result)

        val missing = resource.delegate(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(
                reasonCode = "reviewer.unavailable",
                delegatePartyId = delegatePartyId
            ),
            null,
            "delegate-key",
        )
        assertEquals(428, missing.status)

        val delegated = resource.delegate(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(
                reasonCode = "reviewer.unavailable",
                delegatePartyId = delegatePartyId
            ),
            "\"review:3\"",
            "delegate-key",
        )
        assertEquals(200, delegated.status)
        assertEquals(assignmentId, (delegated.entity as InformationRequestReviewCommandResultDto).assignmentId)
        val captured = argumentCaptor<ChangeInformationRequestReviewAssignmentCommand>()
        verify(assignments, times(2)).change(captured.capture())
        assertEquals(CommandPrecondition.Absent, captured.firstValue.precondition)
        assertEquals(InformationRequestReviewAssignmentChange.DELEGATION, captured.secondValue.change)
        assertEquals(delegatePartyId, captured.secondValue.delegatePartyId)
        assertEquals("reviewer.unavailable", captured.secondValue.reasonCode)
    }

    @Test
    fun `a revocation reaches the assignment service and its stable refusals keep their reason codes`() {
        whenever(assignments.change(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE,
                "This review assignment is no longer active"
            ),
        )

        val refused = resource.revoke(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(reasonCode = "assignment.withdrawn"),
            "\"review:3\"",
            "revoke-key",
        )

        assertEquals(409, refused.status)
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE,
            (refused.entity as ResponseError).reasonCode
        )
        val command = capturedChange()
        assertEquals(InformationRequestReviewAssignmentChange.REVOCATION, command.change)
        assertEquals("assignment.withdrawn", command.reasonCode)
        assertEquals("revoke-key", command.idempotencyKey)

        val keyless = resource.revoke(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            ChangeInformationRequestReviewAssignmentRequest(reasonCode = "assignment.withdrawn"),
            "\"review:3\"",
            null,
        )
        assertEquals(400, keyless.status)
    }

    @Test
    fun `an override delegates its stage, item, outcome, reason, precondition, and key and answers the review`() {
        whenever(decisions.override(any())).thenReturn(result.copy(assignment = null, draftETag = null))
        val itemId = UUID.randomUUID()

        val response = resource.override(
            requestId.toString(),
            reviewId.toString(),
            OverrideInformationRequestReviewItemRequest(
                "review",
                itemId,
                InformationRequestReviewOutcome.SATISFIED,
                "Accepted by the administrator"
            ),
            "\"review:3\"",
            "override-key",
        )

        assertEquals(201, response.status)
        assertEquals(result.reviewETag, response.getHeaderString("ETag"))
        assertEquals(null, (response.entity as InformationRequestReviewCommandResultDto).assignmentId)
        val captured = argumentCaptor<OverrideInformationRequestReviewItemCommand>()
        verify(decisions).override(captured.capture())
        assertEquals("review", captured.firstValue.stageKey)
        assertEquals(itemId, captured.firstValue.submissionItemId)
        assertEquals(InformationRequestReviewOutcome.SATISFIED, captured.firstValue.outcome)
        assertEquals("Accepted by the administrator", captured.firstValue.narrative)
        assertEquals(CommandPrecondition.ExpectedRevision("\"review:3\""), captured.firstValue.precondition)
        assertEquals("override-key", captured.firstValue.idempotencyKey)
        assertEquals(access, captured.firstValue.access)
    }

    private fun capturedChange(): ChangeInformationRequestReviewAssignmentCommand {
        val captured = argumentCaptor<ChangeInformationRequestReviewAssignmentCommand>()
        verify(assignments).change(captured.capture())
        return captured.firstValue
    }

    @Test
    fun `respondent results and the reviewer queue are read through the query service`()
    {
        whenever(queries.results(requestId, noAuthAccess)).thenReturn(emptyList())
        whenever(queries.results(requestId, access)).thenReturn(emptyList())
        whenever(queries.queue(access)).thenReturn(emptyList())

        assertEquals(200, noAuthResultResource.results(requestId.toString(), "link-token", "session-token").status)
        assertEquals(200, queueResource.queue().status)
        verify(queries).results(requestId, noAuthAccess)
        verify(queries).queue(access)
        assertEquals(200, resultResource.results(requestId.toString()).status)
    }

    private fun pathOf(resourceClass: Class<*>): String = resourceClass.getAnnotation(Path::class.java).value
}
