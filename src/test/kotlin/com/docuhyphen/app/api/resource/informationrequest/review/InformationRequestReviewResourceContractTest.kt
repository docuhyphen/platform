package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.model.dto.InformationRequestReviewCommandResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewWorksheetDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.model.informationrequest.review.AssignInformationRequestReviewerCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDraftResult
import com.docuhyphen.app.api.model.informationrequest.review.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.model.informationrequest.review.SaveInformationRequestReviewDraftCommand
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResultResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewQueueResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewResultResourceOperations
import com.docuhyphen.app.api.resource.model.AssignInformationRequestReviewerRequest
import com.docuhyphen.app.api.resource.model.InformationRequestReviewWorksheetEntryRequest
import com.docuhyphen.app.api.resource.model.ReopenInformationRequestReviewRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SaveInformationRequestReviewWorksheetRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestReviewResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val reviewId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))

    private val assignments: InformationRequestReviewAssignmentService = mock()
    private val decisions: InformationRequestReviewDecisionService = mock()
    private val findings: InformationRequestReviewFindingService = mock()
    private val comments: InformationRequestReviewCommentService = mock()
    private val cycles: InformationRequestReviewCycleService = mock()
    private val queries: InformationRequestReviewQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService = mock()

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

    private val resource = InformationRequestReviewResource(assignments, decisions, findings, comments, cycles, queries, accessContextFactory)
    private val resultResource = InformationRequestReviewResultResource(assignments, decisions, findings, comments, cycles, queries, accessContextFactory)
    private val queueResource = InformationRequestReviewQueueResource(assignments, decisions, findings, comments, cycles, queries, accessContextFactory)
    private val noAuthResource = InformationRequestNoAuthReviewResource(assignments, decisions, findings, comments, cycles, queries, readAccessService)
    private val noAuthResultResource = InformationRequestNoAuthReviewResultResource(assignments, decisions, findings, comments, cycles, queries, readAccessService)

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queries.packagePosition(any())).thenReturn(1 to null)
        whenever(readAccessService.resolve(eq("link-token"), anyOrNull())).thenReturn(InformationRequestNoAuthAccess(noAuthAccess, requestId))
    }

    @Test
    fun `review resources are subordinate to the request on both surfaces and the queue belongs to the caller`()
    {
        assertEquals("/information-requests/{id}/reviews", pathOf(InformationRequestReviewResourceOperations::class.java))
        assertEquals("/information-requests/{id}/review-results", pathOf(InformationRequestReviewResultResourceOperations::class.java))
        assertEquals("/information-request-reviews", pathOf(InformationRequestReviewQueueResourceOperations::class.java))
        assertEquals("no-auth/information-requests/{id}/reviews", pathOf(InformationRequestNoAuthReviewResourceOperations::class.java))
        assertEquals("no-auth/information-requests/{id}/review-results", pathOf(InformationRequestNoAuthReviewResultResourceOperations::class.java))
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
        whenever(decisions.saveDraft(any()))
            .thenThrow(CommandPreconditionException.required("\"draft:1\""))
            .thenReturn(InformationRequestReviewDraftResult(assignment, emptyList(), "\"draft:2\""))
        val missing = resource.saveWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            SaveInformationRequestReviewWorksheetRequest(listOf(InformationRequestReviewWorksheetEntryRequest(UUID.randomUUID(), InformationRequestReviewOutcome.SATISFIED))),
            null,
        )
        assertEquals(428, missing.status)

        val saved = resource.saveWorksheet(
            requestId.toString(),
            reviewId.toString(),
            assignmentId.toString(),
            SaveInformationRequestReviewWorksheetRequest(listOf(InformationRequestReviewWorksheetEntryRequest(UUID.randomUUID(), clear = true))),
            "\"draft:1\"",
        )
        assertEquals(200, saved.status)
        assertEquals("\"draft:2\"", saved.getHeaderString("ETag"))
        assertEquals("\"draft:2\"", (saved.entity as InformationRequestReviewWorksheetDto).draftETag)
        val captured = argumentCaptor<SaveInformationRequestReviewDraftCommand>()
        verify(decisions, org.mockito.kotlin.times(2)).saveDraft(captured.capture())
        assertEquals(CommandPrecondition.Absent, captured.firstValue.precondition)
        assertTrue(captured.secondValue.patches.single().clear)
    }

    @Test
    fun `a missing idempotency key and a settled review are refused with stable answers`()
    {
        val keyless = resource.recordWorksheet(requestId.toString(), reviewId.toString(), assignmentId.toString(), "\"draft:1\"", null)
        assertEquals(400, keyless.status)

        whenever(decisions.recordDecisions(any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.REVIEW_SETTLED, "This review has settled"),
        )
        val settled = resource.recordWorksheet(requestId.toString(), reviewId.toString(), assignmentId.toString(), "\"draft:1\"", "record-key")
        assertEquals(409, settled.status)
        assertEquals(InformationRequestErrorCatalog.REVIEW_SETTLED, (settled.entity as ResponseError).reasonCode)
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
