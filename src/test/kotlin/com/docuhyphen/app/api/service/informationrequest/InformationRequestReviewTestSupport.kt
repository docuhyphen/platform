package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestFindingSeverity
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.AssignInformationRequestReviewerCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewDraftPatch
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestReviewDecisionsCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.SaveInformationRequestReviewDraftCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import io.quarkus.narayana.jta.QuarkusTransaction
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal data class ReviewFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val reviewerPartyId: UUID,
    val reviewerUserId: UUID,
)
{
    val requestId: UUID get() = runtime.requestId
    val documentRequirementId: UUID get() = runtime.documentRequirementId
    val attestationRequirementId: UUID get() = runtime.attestationRequirementId
    val optionalRequirementId: UUID get() = runtime.optionalRequirementId
    val artifactId: UUID get() = runtime.artifactId
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

    fun roleDenials(fixture: ReviewFixture): (PrincipalRef, Action) -> Boolean = { principal, action ->
        when (principal.id)
        {
            fixture.runtime.template.userId -> action in setOf(
                Action.INFORMATION_REQUEST_REVIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
                Action.INFORMATION_REQUEST_APPEAL_REVIEW,
            )
            fixture.reviewerUserId -> action in setOf(
                Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
                Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
                Action.INFORMATION_REQUEST_APPEAL_REVIEW,
            )
            else -> action in setOf(
                Action.INFORMATION_REQUEST_REVIEW,
                Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
            )
        }
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
        QuarkusTransaction.requiringNew().call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))) }

    fun reviewETag(reviewId: UUID): String =
        QuarkusTransaction.requiringNew().call { runtime.reviewLoader.reviewETag(requireNotNull(reviewRepository.findById(reviewId))) }

    fun patchNarrative(services: InformationRequestRuntimeServices, fixture: ReviewFixture, requirementId: UUID, narrative: String) =
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
                        InformationRequestResponsePatch(requirementId = requirementId, narrative = ResponseNarrativePatch.Set(narrative)),
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
        services.reviewAssignments.assign(assignment(fixture, review, partyId, stageKey, "assign-${UUID.randomUUID()}", dueAt))
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
    ) = QuarkusTransaction.requiringNew().call {
        services.reviewFindings.record(
            RecordInformationRequestReviewFindingCommand(
                requestId = fixture.requestId,
                reviewId = review.id,
                submissionItemId = itemId,
                evidenceVersionId = evidenceVersionId,
                reasonCode = "record.incomplete",
                narrative = "The note does not say what was asked",
                severity = InformationRequestFindingSeverity.MAJOR,
                visibility = visibility,
                correctionScope = scope,
                access = reviewerAccess,
                idempotencyKey = "finding-${UUID.randomUUID()}",
            ),
        )
    }.finding!!

    fun owner(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    fun contributor(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    fun attestor(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.attestorUserId), AuthorizationContext(sessionRef = "attestor-session"))

    fun reviewer(fixture: ReviewFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.reviewerUserId), AuthorizationContext(sessionRef = "reviewer-session"))
}
