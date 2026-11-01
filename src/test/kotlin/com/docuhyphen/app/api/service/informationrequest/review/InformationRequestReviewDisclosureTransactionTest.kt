package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.InformationRequestReviewDtoMapper
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewWorksheetDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.resource.informationrequest.review.InformationRequestReviewResource
import com.docuhyphen.app.api.resource.model.InformationRequestReviewWorksheetEntryRequest
import com.docuhyphen.app.api.resource.model.SaveInformationRequestReviewWorksheetRequest
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewDisclosureTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var appUsers: com.docuhyphen.app.api.service.user.AppUserService

    @Test
    fun `a reviewer command and its HTTP read disclose the same permitted opening reason`() {
        val scenario = reviewedScenario()
        val reconsideration =
            reopen(scenario, InformationRequestReviewKind.RECONSIDERATION, scenario.owner(verified = true))
        val reopened =
            QuarkusTransaction.requiringNew().call { requireNotNull(reviewRepository.findById(reconsideration)) }
        support.assign(scenario.services, scenario.fixture, reopened)
        val caller = com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
            .signIn(appUsers, scenario.fixture.reviewerUserId)
        val path = "/information-requests/${scenario.requestId}/reviews/$reconsideration"
        val detail = caller.authorized().get(path)
        assertEquals(200, detail.statusCode, detail.asString())
        assertEquals(RECONSIDERATION_REASON, detail.jsonPath().getString("review.openingReason"))

        val command = caller.command("comment-${UUID.randomUUID()}")
            .body("""{"submissionItemId":"${scenario.openItem}","visibility":"REVIEWERS_ONLY","body":"Reviewing the reopened item"}""")
            .post("$path/comments")

        assertEquals(201, command.statusCode, command.asString())
        assertEquals(RECONSIDERATION_REASON, command.jsonPath().getString("review.openingReason"))
    }

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a reviewer without recent verification reads standing and open items but no protected item text or returned file`() {
        val scenario = reviewedScenario()
        val unverified = scenario.reviewer(verified = false)
        assertTrue(
            decision(
                unverified,
                Action.INFORMATION_REQUEST_REVIEW,
                ResourceRef.informationRequest(scenario.requestId)
            ) is Decision.Allow
        )
        assertEquals(
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
            (decision(
                unverified,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                scenario.protectedRequirement
            ) as? Decision.Deny)?.reasonCode,
        )
        assertTrue(
            decision(
                unverified,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                scenario.openRequirement
            ) is Decision.Allow
        )

        val detail = detail(scenario, unverified)
        val listed = listed(scenario, unverified)

        listOf(scenario.worksheetBeforeDecisions, serialized(detail), serialized(listed)).forEach { json ->
            PROTECTED_TEXT.forEach { assertFalse(json.contains(it), "$it in $json") }
        }
        listOf(serialized(detail), serialized(listed)).forEach { json ->
            OPEN_TEXT.forEach { assertTrue(json.contains(it), "$it missing from $json") }
        }
        assertTrue(scenario.worksheetBeforeDecisions.contains(OPEN_ASSESSMENT), scenario.worksheetBeforeDecisions)
        assertStandingRetained(detail)
        assertEquals(
            setOf(scenario.openItem),
            detail.items.filter { it.contentVisible }.map { it.submissionItemId }.toSet()
        )
        assertEquals(1, detail.correction?.undisclosedItemCount)
        assertTrue(detail.correction?.evidence.orEmpty().isEmpty())
    }

    @Test
    fun `a recently verified reviewer reads every finding, comment, assessment, and returned file`() {
        val scenario = reviewedScenario()
        val verified = scenario.reviewer(verified = true)

        val detail = detail(scenario, verified)
        val listed = listed(scenario, verified)

        listOf(serialized(detail), serialized(listed)).forEach { json ->
            (PROTECTED_TEXT + OPEN_TEXT).forEach { assertTrue(json.contains(it), "$it missing from $json") }
        }
        assertEquals(0, detail.correction?.undisclosedItemCount)
        assertEquals(listOf(RETURNED_FILE), detail.correction?.evidence?.map { it.declaredFileName })
        assertEquals(
            PROTECTED_ASSESSMENT,
            detail.decisions.single { it.submissionItemId == scenario.protectedItem }.narrative
        )
    }

    @Test
    fun `an administrator who manages reviews reads their standing without any item text`() {
        val scenario = reviewedScenario()
        val administrator = scenario.owner(verified = true)
        assertTrue(
            decision(
                administrator,
                Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
                ResourceRef.informationRequest(scenario.requestId)
            ) is Decision.Allow
        )
        assertTrue(
            decision(
                administrator,
                Action.INFORMATION_REQUEST_REVIEW,
                ResourceRef.informationRequest(scenario.requestId)
            ) is Decision.Deny
        )

        val detail = detail(scenario, administrator)
        val listed = listed(scenario, administrator)

        listOf(serialized(detail), serialized(listed)).forEach { json ->
            (PROTECTED_TEXT + OPEN_TEXT).forEach { assertFalse(json.contains(it), "$it in $json") }
        }
        assertTrue(detail.canManage)
        assertStandingRetained(detail)
        assertEquals(1, detail.correction?.undisclosedItemCount)
    }

    @Test
    fun `withdrawing an administrator's reviewer Share keeps the standing it manages but withholds the item text it could read`() {
        val scenario = reviewedScenario()
        val administrator = scenario.owner(verified = true)
        val shareId = dataSource.connection.use { connection ->
            grantShare(
                connection,
                scenario.requestId,
                scenario.ownerUserId,
                "REVIEWER"
            )
        }
        val whileReviewer = serialized(detail(scenario, administrator))

        dataSource.connection.use { connection ->
            execute(connection, "UPDATE share SET status = 'REVOKED', revoked_at = now() WHERE id = ?", shareId)
        }
        val detail = detail(scenario, administrator)

        PROTECTED_TEXT.forEach { assertTrue(whileReviewer.contains(it), "$it missing from $whileReviewer") }
        val json = serialized(detail)
        (PROTECTED_TEXT + OPEN_TEXT).forEach { assertFalse(json.contains(it), "$it in $json") }
        assertTrue(detail.canManage)
        assertStandingRetained(detail)
    }

    @Test
    fun `saving a worksheet without recent verification answers only the entries for items the reviewer may still review`() {
        val scenario = reviewedScenario(recordDecisions = false)
        val unverified = scenario.reviewer(verified = false)

        val response = QuarkusTransaction.requiringNew().call {
            resource(scenario.services, unverified).saveWorksheet(
                scenario.requestId.toString(),
                scenario.review.id.toString(),
                scenario.assignmentId.toString(),
                SaveInformationRequestReviewWorksheetRequest(
                    listOf(
                        InformationRequestReviewWorksheetEntryRequest(
                            scenario.openItem,
                            InformationRequestReviewOutcome.SATISFIED,
                            REASSESSED
                        )
                    ),
                ),
                scenario.draftETag,
            )
        }

        assertEquals(200, response.status, "${response.entity}")
        val worksheet = response.entity as InformationRequestReviewWorksheetDto
        val json = Json.encodeToString(InformationRequestReviewWorksheetDto.serializer(), worksheet)
        assertFalse(json.contains(PROTECTED_ASSESSMENT), json)
        assertEquals(
            listOf(scenario.openItem to REASSESSED),
            worksheet.entries.map { it.submissionItemId to it.narrative })
        val verifiedView = detail(scenario, scenario.reviewer(verified = true))
        assertEquals(
            mapOf(scenario.protectedItem to PROTECTED_ASSESSMENT, scenario.openItem to REASSESSED),
            verifiedView.worksheets.single().entries.associate { it.submissionItemId to it.narrative },
        )
    }

    @Test
    fun `a reconsideration reason reaches its author and reviewers who may read every reopened item, never respondents`() {
        val scenario = reviewedScenario()
        val reconsideration =
            reopen(scenario, InformationRequestReviewKind.RECONSIDERATION, scenario.owner(verified = true))

        assertEquals(RECONSIDERATION_REASON, openingReason(scenario, reconsideration, scenario.owner(verified = true)))
        assertEquals(
            RECONSIDERATION_REASON,
            openingReason(scenario, reconsideration, scenario.reviewer(verified = true))
        )
        assertNull(openingReason(scenario, reconsideration, scenario.reviewer(verified = false)))
        assertNull(resultReason(scenario, reconsideration, scenario.contributor()))
        assertNull(resultReason(scenario, reconsideration, scenario.attestor()))
    }

    @Test
    fun `an appeal reason reaches its author, reviewers who may read every item, and respondents who may view every item`() {
        val scenario = reviewedScenario()
        val appeal = reopen(scenario, InformationRequestReviewKind.APPEAL, scenario.contributor())

        assertEquals(APPEAL_REASON, resultReason(scenario, appeal, scenario.contributor()))
        assertNull(resultReason(scenario, appeal, scenario.attestor()))
        assertEquals(APPEAL_REASON, openingReason(scenario, appeal, scenario.reviewer(verified = true)))
        assertNull(openingReason(scenario, appeal, scenario.reviewer(verified = false)))
        assertNull(openingReason(scenario, appeal, scenario.owner(verified = true)))
    }

    @Test
    fun `a reviewer without recent verification is told how many reviewed items wait for it`() {
        val scenario = reviewedScenario()

        assertEquals(1, detail(scenario, scenario.reviewer(verified = false)).itemsAwaitingStepUp)
        assertEquals(0, detail(scenario, scenario.reviewer(verified = true)).itemsAwaitingStepUp)
        assertEquals(0, detail(scenario, scenario.owner(verified = false)).itemsAwaitingStepUp)
    }

    @Test
    fun `saving or recording a protected item without recent verification asks the reviewer to verify again`() {
        val scenario = reviewedScenario(recordDecisions = false)
        val unverified = scenario.reviewer(verified = false)

        assertThrows(StepUpRequiredException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.reviewDecisions.saveDraft(
                    SaveInformationRequestReviewDraftCommand(
                        requestId = scenario.requestId,
                        reviewId = scenario.review.id,
                        assignmentId = scenario.assignmentId,
                        patches = listOf(
                            InformationRequestReviewDraftPatch(
                                scenario.protectedItem,
                                InformationRequestReviewOutcome.CHANGES_REQUIRED,
                                REASSESSED,
                            ),
                        ),
                        access = unverified,
                        precondition = CommandPrecondition.ExpectedRevision(scenario.draftETag),
                    ),
                )
            }
        }
        assertThrows(StepUpRequiredException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.reviewDecisions.recordDecisions(
                    RecordInformationRequestReviewDecisionsCommand(
                        requestId = scenario.requestId,
                        reviewId = scenario.review.id,
                        assignmentId = scenario.assignmentId,
                        access = unverified,
                        precondition = CommandPrecondition.ExpectedRevision(scenario.draftETag),
                        idempotencyKey = "record-unverified",
                    ),
                )
            }
        }
    }

    private fun reopen(
        scenario: ReviewedScenario,
        kind: InformationRequestReviewKind,
        access: RequestAccessContext
    ): UUID =
        QuarkusTransaction.requiringNew().call {
            scenario.services.reviewCycles.reopen(
                ReopenInformationRequestReviewCommand(
                    requestId = scenario.requestId,
                    reviewId = scenario.review.id,
                    kind = kind,
                    reason = if (kind == InformationRequestReviewKind.APPEAL) APPEAL_REASON else RECONSIDERATION_REASON,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(scenario.review.id)),
                    idempotencyKey = "reopen-${UUID.randomUUID()}",
                ),
            )
        }.review.id

    private fun openingReason(scenario: ReviewedScenario, reviewId: UUID, access: RequestAccessContext): String? =
        detailWith(scenario.services, scenario.requestId, reviewId, access).review.openingReason

    private fun resultReason(scenario: ReviewedScenario, reviewId: UUID, access: RequestAccessContext): String? =
        QuarkusTransaction.requiringNew().call {
            scenario.services.reviewQueries.results(scenario.requestId, access)
                .map { InformationRequestReviewDtoMapper.toDto(it, access.principal) }
                .single { it.review.id == reviewId }
                .review
                .openingReason
        }

    private fun reviewedScenario(recordDecisions: Boolean = true): ReviewedScenario {
        val fixture = support.fixture(beforePublish = { connection, configured ->
            reviewAttestation(connection, configured)
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = ? WHERE id = ?",
                COMPARTMENT_KEY,
                configured.documentBindingId,
            )
        })
        dataSource.connection.use { connection -> grantShares(connection, fixture) }
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        val reviewer = access(fixture.reviewerUserId, verified = true)
        val packageId = submit(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val protectedItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val openItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        QuarkusTransaction.requiringNew().run {
            services.reviewFindings.record(
                support.findingCommand(
                    fixture,
                    review,
                    protectedItem,
                    InformationRequestFindingCorrectionScope.EVIDENCE_VERSION,
                    evidenceVersionId = fixture.runtime.evidenceVersionId,
                    reviewerAccess = reviewer,
                ).copy(narrative = PROTECTED_FINDING),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.reviewFindings.record(
                support.findingCommand(
                    fixture,
                    review,
                    openItem,
                    InformationRequestFindingCorrectionScope.NONE,
                    reviewerAccess = reviewer
                )
                    .copy(narrative = OPEN_FINDING),
            )
        }
        comment(services, fixture, review, protectedItem, PROTECTED_COMMENT, reviewer)
        comment(services, fixture, review, openItem, OPEN_COMMENT, reviewer)
        val draftETag = QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    assignmentId = assigned.assignment!!.id,
                    patches = listOf(
                        InformationRequestReviewDraftPatch(
                            protectedItem,
                            InformationRequestReviewOutcome.CHANGES_REQUIRED,
                            PROTECTED_ASSESSMENT
                        ),
                        InformationRequestReviewDraftPatch(
                            openItem,
                            InformationRequestReviewOutcome.SATISFIED,
                            OPEN_ASSESSMENT
                        ),
                    ),
                    access = reviewer,
                    precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                ),
            )
        }.draftETag
        val worksheetBeforeDecisions = serialized(
            detailWith(
                services,
                fixture.requestId,
                review.id,
                access(fixture.reviewerUserId, verified = false)
            )
        )
        if (recordDecisions) {
            support.record(
                services,
                fixture,
                assigned,
                draftETag,
                "record-${UUID.randomUUID()}",
                reviewerAccess = reviewer
            )
        }
        return ReviewedScenario(
            fixture,
            services,
            review,
            assigned.assignment!!.id,
            draftETag,
            protectedItem,
            openItem,
            worksheetBeforeDecisions
        )
    }

    private fun submit(services: InformationRequestRuntimeServices, fixture: ReviewFixture): UUID {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(fixture.runtime.attestorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(support.submissionETag(fixture)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(fixture.runtime.contributorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    @Suppress("LongParameterList")
    private fun comment(
        services: InformationRequestRuntimeServices,
        fixture: ReviewFixture,
        review: InformationRequestReview,
        itemId: UUID,
        body: String,
        reviewer: RequestAccessContext,
    ) {
        QuarkusTransaction.requiringNew().run {
            services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    submissionItemId = itemId,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    body = body,
                    access = reviewer,
                    idempotencyKey = "comment-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun grantShares(connection: Connection, fixture: ReviewFixture) {
        val runtimeFixture = fixture.runtime
        val ownerPartyId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'DECISION_MAKER', 'USER', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            ownerPartyId,
            fixture.requestId,
            runtimeFixture.template.userId,
        )
        listOf(
            Triple(runtimeFixture.contributorPartyId, runtimeFixture.contributorUserId, "CONTRIBUTOR"),
            Triple(runtimeFixture.attestorPartyId, runtimeFixture.attestorUserId, "ATTESTOR"),
            Triple(fixture.reviewerPartyId, fixture.reviewerUserId, "REVIEWER"),
            Triple(ownerPartyId, runtimeFixture.template.userId, "DECISION_MAKER"),
        ).forEach { (partyId, userId, role) ->
            val shareId = grantShare(connection, fixture.requestId, userId, role)
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtimeFixture.exchangeId,
            runtimeFixture.template.userId,
        )
    }

    private fun grantShare(connection: Connection, requestId: UUID, userId: UUID, role: String): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            userId,
            role,
        )
        return shareId
    }

    private fun detail(scenario: ReviewedScenario, access: RequestAccessContext): InformationRequestReviewDto =
        detailWith(scenario.services, scenario.requestId, scenario.review.id, access)

    private fun detailWith(
        services: InformationRequestRuntimeServices,
        requestId: UUID,
        reviewId: UUID,
        access: RequestAccessContext
    ): InformationRequestReviewDto {
        val response = QuarkusTransaction.requiringNew()
            .call { resource(services, access).detail(requestId.toString(), reviewId.toString()) }
        assertEquals(200, response.status, "${response.entity}")
        return response.entity as InformationRequestReviewDto
    }

    private fun listed(scenario: ReviewedScenario, access: RequestAccessContext): List<InformationRequestReviewDto> {
        val response = QuarkusTransaction.requiringNew()
            .call { resource(scenario.services, access).list(scenario.requestId.toString()) }
        assertEquals(200, response.status, "${response.entity}")
        @Suppress("UNCHECKED_CAST")
        return (response.entity as Array<InformationRequestReviewDto>).toList()
    }

    private fun resource(services: InformationRequestRuntimeServices, access: RequestAccessContext) =
        InformationRequestReviewResource(
            services.reviewAssignments,
            services.reviewDecisions,
            services.reviewFindings,
            services.reviewComments,
            services.reviewCycles,
            services.reviewQueries,
            mock<InformationRequestAccessContextFactory> { on { currentAuthenticated() } doReturn access },
        )

    private fun decision(access: RequestAccessContext, action: Action, resource: ResourceRef): Decision =
        QuarkusTransaction.requiringNew()
            .call { authorizationService.authorize(access.principal, action, resource, access.authorization) }

    private fun decision(access: RequestAccessContext, action: Action, requirementId: UUID): Decision =
        decision(access, action, ResourceRef.informationRequestRequirement(requirementId))

    private fun assertStandingRetained(detail: InformationRequestReviewDto) {
        assertEquals(2, detail.stages.single().items.size)
        assertEquals(2, detail.items.size)
        assertEquals(2, detail.decisions.size)
        assertEquals(1, detail.assignments.size)
        assertTrue(detail.correction != null)
    }

    private fun serialized(detail: InformationRequestReviewDto): String =
        Json.encodeToString(InformationRequestReviewDto.serializer(), detail)

    private fun serialized(reviews: List<InformationRequestReviewDto>): String =
        reviews.joinToString("\n") { serialized(it) }

    private fun access(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "review-session", mfaSatisfied = verified)
        )

    @Suppress("LongParameterList")
    private inner class ReviewedScenario(
        val fixture: ReviewFixture,
        val services: InformationRequestRuntimeServices,
        val review: InformationRequestReview,
        val assignmentId: UUID,
        val draftETag: String,
        val protectedItem: UUID,
        val openItem: UUID,
        val worksheetBeforeDecisions: String,
    ) {
        val requestId: UUID get() = fixture.requestId
        val ownerUserId: UUID get() = fixture.runtime.template.userId
        val protectedRequirement: UUID get() = fixture.documentRequirementId
        val openRequirement: UUID get() = fixture.attestationRequirementId

        fun reviewer(verified: Boolean) = access(fixture.reviewerUserId, verified)

        fun owner(verified: Boolean) = access(ownerUserId, verified)

        fun contributor() = access(fixture.runtime.contributorUserId, verified = true)

        fun attestor() = access(fixture.runtime.attestorUserId, verified = true)
    }

    private companion object {
        const val COMPARTMENT_KEY = "restricted-record"
        const val PROTECTED_FINDING = "The restricted record omits the requested period"
        const val PROTECTED_COMMENT = "Restricted record note for reviewers"
        const val PROTECTED_ASSESSMENT = "Restricted record assessment"
        const val RETURNED_FILE = "process-record.pdf"
        const val OPEN_FINDING = "The open assertion wording needs a date"
        const val OPEN_COMMENT = "Open assertion note for reviewers"
        const val OPEN_ASSESSMENT = "Open assertion assessment"
        const val REASSESSED = "Open assertion reassessed"
        const val RECONSIDERATION_REASON = "The requesting side asks for the restricted record to be read again"
        const val APPEAL_REASON = "The restricted record already covers the requested period"
        val PROTECTED_TEXT = listOf(PROTECTED_FINDING, PROTECTED_COMMENT, PROTECTED_ASSESSMENT, RETURNED_FILE)
        val OPEN_TEXT = listOf(OPEN_FINDING, OPEN_COMMENT, OPEN_ASSESSMENT)
    }
}
