package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeStateReader
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponsePolicyEnforcer
import com.docuhyphen.app.api.service.informationrequest.review.insertReviewStage
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendmentTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var currentRepository: InformationRequestRequirementCurrentRepository
    @Inject lateinit var responseRepository: InformationRequestResponseRepository
    @Inject lateinit var noticeRepository: InformationRequestNoticeIntentRepository
    @Inject lateinit var noticeStates: InformationRequestNoticeStateReader
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Inject
    lateinit var hiddenResponses: InformationRequestHiddenResponsePolicyEnforcer

    @Test
    fun `a reworded Version re-pins the request, advances each Requirement, keeps its answers, and owes each party a notice`()
    {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val services = runtime.build(fixture.requestId)
        val etag = aggregateETag(fixture)

        val result = QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword")) }

        assertEquals(next.versionId, result.request.templateVersionId)
        assertNotEquals(etag, result.requestETag)
        assertEquals(
            listOf("supporting-record" to InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED),
            result.amendment.changes.map { it.requirementKey to it.changeKind },
        )
        assertEquals(
            setOf(fixture.contributorPartyId, fixture.attestorPartyId),
            result.amendment.notices.map { it.partyId }.toSet(),
        )
        QuarkusTransaction.requiringNew().run {
            val active = requirementRepository.findForRequest(fixture.requestId)
            assertEquals(
                setOf(next.documentBindingId, next.attestationBindingId),
                active.map { it.sourceTemplateBindingId }.toSet(),
            )
            assertEquals(setOf(2), active.map { currentRepository.findForRequirement(it.id)?.currentRevisionNumber }.toSet())
            val response = requireNotNull(responseRepository.findAllForRequest(fixture.requestId).single())
            assertEquals(fixture.documentRevisionId, response.requirementRevisionId)
            assertNull(response.reconfirmationRequiredByAmendmentId)
            assertEquals(
                setOf(InformationRequestNoticeDeliveryState.PENDING),
                noticeRepository.findForRequest(fixture.requestId).map { noticeStates.states(fixture.requestId).getValue(it.id) }.toSet(),
            )
            assertTrue(InformationRequestMutation.AMEND in transitionRepository.findForRequest(fixture.requestId).map { it.mutation })
        }

        val replayed = QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword")) }
        assertEquals(result.amendment.amendment.id, replayed.amendment.amendment.id)
        val visible = QuarkusTransaction.requiringNew().call { services.amendmentQueries.amendments(fixture.requestId, owner(fixture)) }
        assertEquals(1, visible.single().view.amendment.amendmentNumber)
    }

    @Test
    fun `a draft is moved to the later Version without telling its parties that what was asked of them changed`() {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                version.documentBindingId,
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'DRAFT', issued_at = NULL WHERE id = ?",
                fixture.requestId
            )
        }
        val services = runtime.build(fixture.requestId)

        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "draft"))
        }

        assertEquals(next.versionId, result.request.templateVersionId)
        assertTrue(result.amendment.notices.isEmpty())
        QuarkusTransaction.requiringNew()
            .run { assertTrue(noticeRepository.findForRequest(fixture.requestId).isEmpty()) }
    }

    @Test
    fun `a retried amendment answers the request with the ETag it has after a later change`() {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val services = runtime.build(fixture.requestId)
        val etag = aggregateETag(fixture)
        val result = QuarkusTransaction.requiringNew()
            .call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword-then-change")) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET aggregate_revision = aggregate_revision + 1 WHERE id = ?",
                fixture.requestId
            )
        }

        val replayed = QuarkusTransaction.requiringNew()
            .call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword-then-change")) }

        assertEquals(result.amendment.amendment.id, replayed.amendment.amendment.id)
        assertNotEquals(result.requestETag, replayed.requestETag)
        assertEquals(aggregateETag(fixture), replayed.requestETag)
    }

    @Test
    fun `a change of meaning makes the existing answer need reconfirming until it is saved again`()
    {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET requiredness = 'OPTIONAL' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val services = runtime.build(fixture.requestId)

        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "tighten"))
        }

        assertEquals(
            listOf(Triple("supporting-record", InformationRequestAmendmentChangeKind.MEANING_CHANGED, true)),
            result.amendment.changes.map { Triple(it.requirementKey, it.changeKind, it.reconfirmationRequired) },
        )
        assertEquals(
            InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED,
            problems(services, fixture)[fixture.documentRequirementId],
        )

        QuarkusTransaction.requiringNew().run {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(fixture)),
                    idempotencyKey = "reconfirm-${UUID.randomUUID()}",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.documentRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }

        assertEquals(null, problems(services, fixture)[fixture.documentRequirementId])
        QuarkusTransaction.requiringNew().run {
            assertNull(responseRepository.findAllForRequest(fixture.requestId).single().reconfirmationRequiredByAmendmentId)
        }
    }

    @Test
    fun `a Version that stops asking for a Requirement retires it and one that newly asks materializes it`()
    {
        val addedRequirement = UUID.randomUUID()
        val (fixture, next) = fixture(keepAttestation = false) { _, fixture, version ->
            val binding = UUID.randomUUID()
            fixture.template.insertRequirement(addedRequirement, "additional-record", "DOCUMENT")
            fixture.template.insertBinding(binding, version.versionId, addedRequirement, version.sectionId, 3)
            fixture.template.insertEvidencePolicy(UUID.randomUUID(), binding, version.versionId)
            fixture.template.insertDisposition(binding, version.versionId, "PROVIDED")
        }
        val services = runtime.build(fixture.requestId)

        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "restructure"))
        }

        assertEquals(
            listOf(
                "additional-record" to InformationRequestAmendmentChangeKind.ADDED,
                "recorded-assertion" to InformationRequestAmendmentChangeKind.REMOVED,
            ),
            result.amendment.changes.map { it.requirementKey to it.changeKind },
        )
        QuarkusTransaction.requiringNew().run {
            val active = requirementRepository.findForRequest(fixture.requestId)
            assertEquals(
                setOf(fixture.documentTemplateRequirementId, addedRequirement),
                active.map { it.sourceTemplateRequirementId }.toSet(),
            )
            assertEquals(3, requirementRepository.findAllForRequest(fixture.requestId).size)
        }
    }

    @Test
    fun `an earlier Version, a stale request, and a change reaching a submitted stage are refused whole`()
    {
        val (fixture, next) = fixture(staged = true)
        val services = runtime.build(fixture.requestId)

        val earlier = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.amendments.amend(amend(fixture, fixture.template.versionId, aggregateETag(fixture), "same-version"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.AMENDMENT_TARGET_INVALID, earlier.reasonCode)

        val stale = assertThrows(CommandPreconditionException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, "\"stale\"", "stale")) }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)

        val stageETag = QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf("record-stage", runtime.contentCollector.collect(request, "record-stage").contentHash)
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    stageKey = "record-stage",
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(stageETag),
                    idempotencyKey = "stage-before-amendment",
                ),
            )
        }
        val submitted = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "after-submission"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, submitted.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(fixture.template.versionId, requestRepository.findById(fixture.requestId)?.templateVersionId)
            assertTrue(noticeRepository.findForRequest(fixture.requestId).isEmpty())
        }
    }

    @Test
    fun `a Version that changes only the review plan is refused while a submission stands`() {
        val (fixture, next) = reviewPlanFixture()
        val services = runtime.build(fixture.requestId)
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = RequestAccessContext(
                        PrincipalRef.user(fixture.attestorUserId),
                        AuthorizationContext(sessionRef = "attestor-session")
                    ),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val request = requireNotNull(requestRepository.findById(fixture.requestId))
                            InformationRequestETag.submissionOf(
                                null,
                                runtime.contentCollector.collect(request, null).contentHash
                            )
                        },
                    ),
                    idempotencyKey = "assent-before-review-plan-change",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-before-review-plan-change",
                ),
            )
        }

        val refused = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.amendments.amend(
                    amend(
                        fixture,
                        next.versionId,
                        aggregateETag(fixture),
                        "review-plan-after-submission"
                    )
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, refused.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(fixture.template.versionId, requestRepository.findById(fixture.requestId)?.templateVersionId)
        }
    }

    @Test
    fun `a Version that changes only the review plan applies before any submission and records the review plan change`() {
        val (fixture, next) = reviewPlanFixture()
        val services = runtime.build(fixture.requestId)

        val amended = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(
                amend(
                    fixture,
                    next.versionId,
                    aggregateETag(fixture),
                    "review-plan-before-submission"
                )
            )
        }

        assertEquals(next.versionId, amended.request.templateVersionId)
        assertEquals(
            "true",
            dataSource.connection.use { connection ->
                queryString(
                    connection,
                    "SELECT payload_json::jsonb ->> 'reviewPlanChanged' FROM audit_outbox WHERE target_id = ? AND event_type_key = 'information_request.request.amend'",
                    fixture.requestId.toString(),
                )
            },
        )
    }

    @Test
    fun `after the owner's plan lapses an amendment that adds or changes what is asked is refused while a reworded one applies`() {
        val addedRequirement = UUID.randomUUID()
        val (expanding, expandingNext) = fixture { _, fixture, version ->
            val binding = UUID.randomUUID()
            fixture.template.insertRequirement(addedRequirement, "additional-record", "DOCUMENT")
            fixture.template.insertBinding(binding, version.versionId, addedRequirement, version.sectionId, 3)
            fixture.template.insertEvidencePolicy(UUID.randomUUID(), binding, version.versionId)
            fixture.template.insertDisposition(binding, version.versionId, "PROVIDED")
        }
        val (tightened, tightenedNext) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET requiredness = 'OPTIONAL' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val (reworded, rewordedNext) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                version.documentBindingId,
            )
        }

        listOf(expanding to expandingNext, tightened to tightenedNext).forEach { (fixture, next) ->
            val refusal = assertThrows(SubscriptionDenialException::class.java) {
                QuarkusTransaction.requiringNew().call {
                    lapsedAmendments(fixture).amend(
                        amend(
                            fixture,
                            next.versionId,
                            aggregateETag(fixture),
                            "expand-after-lapse"
                        )
                    )
                }
            }
            assertEquals(SubscriptionDenialReason.SUBSCRIPTION_CANCELED, refusal.denial.reason)
            QuarkusTransaction.requiringNew().run {
                assertEquals(
                    fixture.template.versionId,
                    requestRepository.findById(fixture.requestId)?.templateVersionId
                )
                assertTrue(
                    transitionRepository.findForRequest(fixture.requestId)
                        .none { it.mutation == InformationRequestMutation.AMEND })
            }
        }

        val result = QuarkusTransaction.requiringNew().call {
            lapsedAmendments(reworded).amend(
                amend(
                    reworded,
                    rewordedNext.versionId,
                    aggregateETag(reworded),
                    "reword-after-lapse"
                )
            )
        }
        assertEquals(rewordedNext.versionId, result.request.templateVersionId)
    }

    @Test
    fun `after the owner's plan lapses new configuration for a request is refused before any Version is published`() {
        val (fixture, _) = fixture()
        val versionsBefore = templateVersions(fixture)

        assertThrows(SubscriptionDenialException::class.java) {
            QuarkusTransaction.requiringNew().call {
                lapsedAmendments(fixture).amend(
                    AmendInformationRequestCommand(
                        requestId = fixture.requestId,
                        configuration = InformationRequestTemplateConfigurationRequest(),
                        reasonCode = "revised-collection",
                        access = owner(fixture),
                        precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture)),
                        idempotencyKey = "configure-after-lapse",
                    ),
                )
            }
        }

        assertEquals(versionsBefore, templateVersions(fixture))
    }

    @Test
    fun `a meaning change requires reconfirmation of files even without a disposition row`() {
        val (fixture, next) = fixture { connection, configured, version ->
            execute(connection, "DELETE FROM information_request_response WHERE id = ?", configured.documentResponseId)
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET requiredness = 'OPTIONAL' WHERE id = ?",
                version.documentBindingId
            )
        }
        val services = runtime.build(fixture.requestId)
        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "file-reconfirmation"))
        }
        QuarkusTransaction.requiringNew().run {
            val response = responseRepository.findAllForRequest(fixture.requestId).singleOrNull()
            assertEquals(result.amendment.amendment.id, response?.reconfirmationRequiredByAmendmentId)
            assertEquals(fixture.documentRequirementId, response?.informationRequestRequirementId)
        }
        assertEquals(
            InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED,
            problems(services, fixture)[fixture.documentRequirementId]
        )
    }

    private fun lapsedAmendments(fixture: SubmissionRuntimeSqlFixture): InformationRequestAmendmentService {
        val lapsed = mock<InformationRequestEntitlementGuard>()
        whenever(lapsed.requireRequestMutation(any())).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.SUBSCRIPTION_CANCELED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "The subscription was canceled.",
                ),
            ),
        )
        val grants = mock<InformationRequestExecutionGrantService>()
        whenever(grants.findForRequest(fixture.requestId)).thenReturn(issuedExecutionGrant().apply {
            requestId = fixture.requestId
        })
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val gate = InformationRequestMutationGate(
            requestRepository,
            runtime.exchangeLookup,
            authorization,
            lapsed,
            InformationRequestContinuationGuard(grants, lapsed),
        )
        return InformationRequestAmendmentService(
            gate,
            requestRepository,
            runtime.amendmentTargetResolver,
            runtime.projectionLoader,
            runtime.capabilityGate,
            runtime.amendmentGuard,
            runtime.materializer,
            runtime.amendmentRecorder,
            runtime.amendmentReader,
            runtime.commandReceiptService,
            runtime.transitionHistory,
            runtime.clock,
            requirementRepository,
            hiddenResponses,
            runtime.fieldAudience,
        )
    }

    private fun templateVersions(fixture: SubmissionRuntimeSqlFixture): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM information_request_template_version WHERE template_definition_id = ?",
                fixture.template.definitionId,
            )
        }

    private fun reviewPlanFixture(): Pair<SubmissionRuntimeSqlFixture, NextSubmissionVersion> =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection, reviewed = true)
            fixture to fixture.publishNextVersion(
                adjust = { version ->
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
                        version.documentBindingId,
                    )
                    insertReviewStage(connection, version.versionId, "added-check", 1)
                },
            )
        }

    private fun fixture(
        staged: Boolean = false,
        keepAttestation: Boolean = true,
        adjust: (Connection, SubmissionRuntimeSqlFixture, NextSubmissionVersion) -> Unit = { _, _, _ -> },
    ): Pair<SubmissionRuntimeSqlFixture, NextSubmissionVersion> =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection, staged = staged)
            fixture to fixture.publishNextVersion(keepAttestation = keepAttestation) { version -> adjust(connection, fixture, version) }
        }

    private fun problems(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture) =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(fixture.requestId, null, contributor(fixture)).readiness.problems
                .associate { it.requirementId to it.code }
        }

    private fun aggregateETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId)))
        }

    private fun responseETag(fixture: SubmissionRuntimeSqlFixture): String =
        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId)))

    private fun amend(fixture: SubmissionRuntimeSqlFixture, versionId: UUID, etag: String, key: String) =
        AmendInformationRequestCommand(
            requestId = fixture.requestId,
            targetTemplateVersionId = versionId,
            reasonCode = "revised-collection",
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
        )

    private fun owner(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))
}
