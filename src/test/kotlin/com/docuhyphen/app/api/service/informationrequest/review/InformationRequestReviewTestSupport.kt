package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@Suppress("LongParameterList")
internal fun insertReviewStage(
    connection: Connection,
    versionId: UUID,
    stageKey: String,
    position: Int,
    aggregation: String = "ANY",
    minimumReviewers: Int = 1,
    tieResolution: String = "MOST_SEVERE_OUTCOME",
    overridePermitted: Boolean = false,
    excludesResponseParties: Boolean = false,
    excludesPriorReviewers: Boolean = false,
) {
    execute(
        connection,
        """
        INSERT INTO information_request_template_review_stage
            (id, template_version_id, stage_key, position, title, aggregation, minimum_reviewer_count,
             tie_resolution, override_permitted, excludes_response_parties, excludes_prior_reviewers)
        VALUES (gen_random_uuid(), ?, ?, ?, 'Synthetic check', ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        versionId,
        stageKey,
        position,
        aggregation,
        minimumReviewers,
        tieResolution,
        overridePermitted,
        excludesResponseParties,
        excludesPriorReviewers,
    )
}

internal fun orderReviewStages(connection: Connection, versionId: UUID, ordering: String)
{
    execute(
        connection,
        "UPDATE information_request_template_version SET review_stage_ordering = ? WHERE id = ?",
        ordering,
        versionId
    )
}

internal fun reviewAttestation(connection: Connection, runtime: SubmissionRuntimeSqlFixture) {
    execute(
        connection,
        "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
        runtime.attestationBindingId,
    )
}

internal class InformationRequestReviewTestSupport(
    private val dataSource: DataSource,
    private val runtime: InformationRequestRuntimeTestServices,
    private val requestRepository: InformationRequestRepository,
    private val reviewRepository: InformationRequestReviewRepository,
)
{
    fun fixture(
        optionalRecord: Boolean = false,
        beforePublish: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> },
        prepare: (SubmissionRuntimeSqlFixture) -> Unit = {},
    ): ReviewFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(
                connection,
                reviewed = true,
                optionalRecord = optionalRecord,
                beforePublish = { runtime -> beforePublish(connection, runtime) },
            )
            val reviewerPartyId = UUID.randomUUID()
            val reviewerUserId = UUID.randomUUID()
            runtimeFixture.insertActingParty(reviewerPartyId, "REVIEWER", reviewerUserId)
            prepare(runtimeFixture)
            ReviewFixture(runtimeFixture, reviewerPartyId, reviewerUserId)
        }

    fun roleDenials(
        fixture: ReviewFixture,
        reviewers: Set<UUID> = setOf(fixture.reviewerUserId),
    ): (PrincipalRef, Action) -> Boolean = { principal, action ->
        when (principal.id)
        {
            fixture.runtime.template.userId -> action in setOf(
                Action.INFORMATION_REQUEST_REVIEW,
                Action.INFORMATION_REQUEST_VIEW_REVIEWS,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
                Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
                Action.INFORMATION_REQUEST_APPEAL_REVIEW,
            )

            in reviewers -> action in setOf(
                Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
                Action.INFORMATION_REQUEST_APPEAL_REVIEW,
            )

            else -> action in setOf(
                Action.INFORMATION_REQUEST_REVIEW,
                Action.INFORMATION_REQUEST_VIEW_REVIEWS,
                Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
            )
        }
    }

    fun authorization(
        denies: (PrincipalRef, Action) -> Boolean,
        hidden: Set<Triple<UUID, Action, UUID>> = emptySet(),
    ): AuthorizationService {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
            val principal = invocation.getArgument<PrincipalRef>(0)
            val action = invocation.getArgument<Action>(1)
            val resource = invocation.getArgument<ResourceRef>(2)
            if (denies(principal, action) || Triple(principal.id, action, resource.id) in hidden)
                Decision.Deny("DENIED", "Denied to this caller")
            else
                Decision.Allow()
        }
        return authorization
    }

    fun reviewsOf(fixture: ReviewFixture): List<InformationRequestReview> =
        QuarkusTransaction.requiringNew().call { reviewRepository.findForRequest(fixture.requestId) }

    fun state(fixture: ReviewFixture): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)).state }

    fun item(fixture: ReviewFixture, packageId: UUID, requirementId: UUID): UUID =
        QuarkusTransaction.requiringNew().call {
            runtime.packageReader.view(fixture.requestId, packageId).items
                .single { it.informationRequestRequirementId == requirementId }.id
        }

    fun submitWhole(services: InformationRequestRuntimeServices, fixture: ReviewFixture): UUID
    {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = attestor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    fun submissionETag(fixture: ReviewFixture): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    fun responseETag(fixture: ReviewFixture): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))) }

    fun reviewETag(reviewId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { runtime.reviewLoader.reviewETag(requireNotNull(reviewRepository.findById(reviewId))) }

    fun patchNarrative(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        requirementId: UUID,
        narrative: String
    ) =
        QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))),
                    ),
                    idempotencyKey = "patch-${UUID.randomUUID()}",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            narrative = ResponseNarrativePatch.Set(narrative)
                        ),
                    ),
                ),
            )
        }

    fun assign(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        review: InformationRequestReview,
        partyId: UUID = fixture.reviewerPartyId,
        stageKey: String = "review",
        dueAt: Instant? = null,
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewAssignments.assign(
            assignment(
                fixture,
                review,
                partyId,
                stageKey,
                "assign-${UUID.randomUUID()}",
                dueAt
            )
        )
    }

    @Suppress("LongParameterList")
    fun assignment(
        fixture: ReviewFixture,
        review: InformationRequestReview,
        partyId: UUID,
        stageKey: String,
        key: String,
        dueAt: Instant? = null,
    ) = AssignInformationRequestReviewerCommand(
        requestId = fixture.requestId,
        reviewId = review.id,
        stageKey = stageKey,
        reviewerPartyId = partyId,
        dueAt = dueAt,
        access = owner(fixture),
        precondition = CommandPrecondition.ExpectedRevision(reviewETag(review.id)),
        idempotencyKey = key,
    )

    fun draft(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        assigned: InformationRequestReviewCommandResult,
        outcome: InformationRequestReviewOutcome? = null,
        outcomes: Map<UUID, InformationRequestReviewOutcome>? = null,
        reviewerAccess: RequestAccessContext = reviewer(fixture),
    ): String
    {
        val entries = outcomes ?: QuarkusTransaction.requiringNew().call {
            val snapshot = runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(assigned.review.id)))
            snapshot.coverage[assigned.assignment!!.stageKey].orEmpty()
                .filterNot { id ->
                    snapshot.decisions.any { it.submissionItemId == id && it.kind != InformationRequestReviewDecisionKind.REVIEWER }
                }
                .associateWith { requireNotNull(outcome) }
        }
        return QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = fixture.requestId,
                    reviewId = assigned.review.id,
                    assignmentId = assigned.assignment!!.id,
                    patches = entries.map { (itemId, value) ->
                        InformationRequestReviewDraftPatch(itemId, value, narrative = "Checked against the request")
                    },
                    access = reviewerAccess,
                    precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                ),
            )
        }.draftETag
    }

    fun record(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        assigned: InformationRequestReviewCommandResult,
        draftETag: String,
        key: String,
        reviewerAccess: RequestAccessContext = reviewer(fixture),
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewDecisions.recordDecisions(
            RecordInformationRequestReviewDecisionsCommand(
                requestId = fixture.requestId,
                reviewId = assigned.review.id,
                assignmentId = assigned.assignment!!.id,
                access = reviewerAccess,
                precondition = CommandPrecondition.ExpectedRevision(draftETag),
                idempotencyKey = key,
            ),
        )
    }

    @Suppress("LongParameterList")
    fun finding(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        review: InformationRequestReview,
        itemId: UUID,
        scope: InformationRequestFindingCorrectionScope,
        visibility: InformationRequestReviewVisibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        evidenceVersionId: UUID? = null,
        reviewerAccess: RequestAccessContext = reviewer(fixture),
        assignmentId: UUID? = null,
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewFindings.record(
            findingCommand(fixture, review, itemId, scope, visibility, evidenceVersionId, reviewerAccess, assignmentId),
        )
    }.finding!!

    @Suppress("LongParameterList")
    fun findingCommand(
        fixture: ReviewFixture,
        review: InformationRequestReview,
        itemId: UUID,
        scope: InformationRequestFindingCorrectionScope = InformationRequestFindingCorrectionScope.RESPONSE,
        visibility: InformationRequestReviewVisibility = InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        evidenceVersionId: UUID? = null,
        reviewerAccess: RequestAccessContext = reviewer(fixture),
        assignmentId: UUID? = null,
        key: String = "finding-${UUID.randomUUID()}",
    ) = RecordInformationRequestReviewFindingCommand(
        requestId = fixture.requestId,
        reviewId = review.id,
        submissionItemId = itemId,
        assignmentId = assignmentId,
        evidenceVersionId = evidenceVersionId,
        reasonCode = "record.incomplete",
        narrative = "The note does not say what was asked",
        severity = InformationRequestFindingSeverity.MAJOR,
        visibility = visibility,
        correctionScope = scope,
        access = reviewerAccess,
        idempotencyKey = key,
    )

    @Suppress("LongParameterList")
    fun changeCommand(
        fixture: ReviewFixture,
        assignment: InformationRequestReviewAssignment,
        change: InformationRequestReviewAssignmentChange,
        access: RequestAccessContext,
        reasonCode: String? = "reviewer.unavailable",
        delegatePartyId: UUID? = null,
        key: String = "change-${UUID.randomUUID()}",
    ) = ChangeInformationRequestReviewAssignmentCommand(
        requestId = fixture.requestId,
        reviewId = assignment.reviewId,
        assignmentId = assignment.id,
        change = change,
        reasonCode = reasonCode,
        narrative = "The assigned reviewer cannot take this item",
        delegatePartyId = delegatePartyId,
        access = access,
        precondition = CommandPrecondition.ExpectedRevision(reviewETag(assignment.reviewId)),
        idempotencyKey = key,
    )

    fun assignmentOf(assignmentId: UUID): InformationRequestReviewAssignment =
        QuarkusTransaction.requiringNew()
            .call { requireNotNull(runtime.reviewAssignmentRepository.findById(assignmentId)) }

    fun reviewOf(reviewId: UUID): InformationRequestReview =
        QuarkusTransaction.requiringNew().call { requireNotNull(reviewRepository.findById(reviewId)) }

    fun access(userId: UUID) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "session-${userId.toString().take(8)}")
        )

    fun owner(fixture: ReviewFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.runtime.template.userId),
            AuthorizationContext(sessionRef = "owner-session")
        )

    fun contributor(fixture: ReviewFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.runtime.contributorUserId),
            AuthorizationContext(sessionRef = "contributor-session")
        )

    fun attestor(fixture: ReviewFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.runtime.attestorUserId),
            AuthorizationContext(sessionRef = "attestor-session")
        )

    fun reviewer(fixture: ReviewFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.reviewerUserId),
            AuthorizationContext(sessionRef = "reviewer-session")
        )
}
