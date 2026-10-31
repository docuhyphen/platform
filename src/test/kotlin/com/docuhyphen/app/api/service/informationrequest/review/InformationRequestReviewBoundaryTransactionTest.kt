package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.model.informationrequest.review.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import com.docuhyphen.app.api.service.informationrequest.execution.typedAnswerGrantFeatures
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.informationrequest.response.grantPartyShare
import com.docuhyphen.app.api.service.informationrequest.response.grantRespondentShares
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewBoundaryTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var reviews: InformationRequestReviewRepository

    @Inject
    lateinit var authorization: DefaultAuthorizationService

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    @Inject
    lateinit var revocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var appUsers: AppUserService
    private val support by lazy { InformationRequestReviewTestSupport(dataSource, runtime, requests, reviews) }

    @Test
    fun `a reviewer queue omits a waiting sequential stage`() {
        val fixture = fixture { connection, configured ->
            orderReviewStages(connection, configured.template.versionId, "SEQUENTIAL")
            insertReviewStage(connection, configured.template.versionId, "first-check", 1)
            insertReviewStage(connection, configured.template.versionId, "second-check", 2)
        }
        val services = services(fixture)
        support.submitWhole(services, fixture)
        support.assign(services, fixture, support.reviewsOf(fixture).single(), stageKey = "second-check")
        val queue = QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(support.reviewer(fixture)) }
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `a declared recusal prevents assigning the same reviewer to the stage again`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        QuarkusTransaction.requiringNew().run {
            services.reviewAssignments.change(
                support.changeCommand(
                    fixture,
                    assigned.assignment!!,
                    InformationRequestReviewAssignmentChange.RECUSAL,
                    support.reviewer(fixture),
                    reasonCode = "conflict"
                )
            )
        }
        val refusal =
            assertThrows(InformationRequestLifecycleException::class.java) { support.assign(services, fixture, review) }
        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
    }

    @Test
    fun `revoking an undecided reviewer party settles an otherwise complete all-reviewers stage`() {
        val otherParty = UUID.randomUUID()
        val otherUser = UUID.randomUUID()
        val fixture = fixture(prepare = {
            it.insertActingParty(
                otherParty,
                "REVIEWER",
                otherUser
            )
        }) { connection, configured ->
            insertReviewStage(
                connection,
                configured.template.versionId,
                "all-check",
                1,
                aggregation = "ALL"
            )
        }
        dataSource.connection.use { connection ->
            grantPartyShare(connection, fixture.requestId, otherParty, otherUser, "REVIEWER")
        }
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val first = support.assign(services, fixture, review, stageKey = "all-check")
        support.assign(services, fixture, review, partyId = otherParty, stageKey = "all-check")
        support.record(
            services,
            fixture,
            first,
            support.draft(services, fixture, first, InformationRequestReviewOutcome.SATISFIED),
            "first-done"
        )
        QuarkusTransaction.requiringNew().run {
            revocations.revoke(
                RevokeInformationRequestPartyCommand(
                    fixture.requestId,
                    otherParty,
                    support.owner(fixture),
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.partiesOf(
                            requireNotNull(
                                requests.findById(fixture.requestId)
                            )
                        )
                    ),
                    "revoke-reviewer"
                )
            )
        }
        assertEquals(InformationRequestReviewState.SATISFIED, support.reviewsOf(fixture).single().state)
    }

    @Test
    fun `a cancelled request projects its review as read only`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)
        QuarkusTransaction.requiringNew().run {
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    fixture.requestId,
                    "no-longer-needed",
                    support.owner(fixture),
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(
                            requireNotNull(
                                requests.findById(fixture.requestId)
                            )
                        )
                    ),
                    "cancel-review"
                )
            )
        }
        val readable = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.owner(fixture)) }
        assertFalse(readable.canManage)
        assertTrue(readable.delegationCandidates.isEmpty())
        val reviewer = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.reviewer(fixture)) }
        assertTrue(reviewer.callerAssignmentIds.isEmpty())
    }

    @Test
    fun `an administrator sees their own finding without gaining the submitted content`() {
        val fixture = fixture()
        val services = services(fixture)
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val finding = support.finding(
            services,
            fixture,
            review,
            support.item(fixture, packageId, fixture.documentRequirementId),
            InformationRequestFindingCorrectionScope.NONE,
            reviewerAccess = support.owner(fixture)
        )
        val readable = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.owner(fixture)) }
        assertTrue(readable.contentVisibleItemIds.isEmpty())
        assertEquals(listOf(finding.id), readable.findings.map { it.id })
    }

    @Test
    fun `duplicate worksheet entries are malformed over HTTP`() {
        val fixture = fixture()
        val services = services(fixture)
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val item = support.item(fixture, packageId, fixture.documentRequirementId)
        val entry = """{"submissionItemId":"$item","outcome":"SATISFIED"}"""
        val response =
            InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("duplicate-worksheet")
                .header("If-Match", assigned.draftETag)
                .body("""{"entries":[$entry,$entry]}""")
                .patch("/information-requests/${fixture.requestId}/reviews/${review.id}/assignments/${assigned.assignment!!.id}/worksheet")
        assertEquals(400, response.statusCode, response.asString())
        assertEquals("INFORMATION_REQUEST_REVIEW_INPUT_INVALID", response.jsonPath().getString("reasonCode"))
    }

    @Test
    fun `an overlong assignment reason is malformed over HTTP`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val response = InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("long-recusal")
            .header("If-Match", support.reviewETag(review.id))
            .body("""{"reasonCode":"${"r".repeat(129)}"}""")
            .post("/information-requests/${fixture.requestId}/reviews/${review.id}/assignments/${assigned.assignment!!.id}/recusal")
        assertEquals(400, response.statusCode, response.asString())
        assertEquals("INFORMATION_REQUEST_REVIEW_INPUT_INVALID", response.jsonPath().getString("reasonCode"))
    }

    @Test
    fun `an overlong finding narrative is malformed over HTTP`() {
        val fixture = fixture()
        val services = services(fixture)
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)
        val item = support.item(fixture, packageId, fixture.documentRequirementId)
        val response = InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("long-finding")
            .body("""{"submissionItemId":"$item","reasonCode":"record.incomplete","narrative":"${"x".repeat(4001)}","severity":"MAJOR","visibility":"REVIEWERS_ONLY","correctionScope":"NONE"}""")
            .post("/information-requests/${fixture.requestId}/reviews/${review.id}/findings")
        assertEquals(400, response.statusCode, response.asString())
        assertEquals("INFORMATION_REQUEST_REVIEW_INPUT_INVALID", response.jsonPath().getString("reasonCode"))
    }

    @Test
    fun `a pending correction is not superseded after its answers were edited`() {
        val fixture = fixture()
        val services = services(fixture)
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        support.finding(
            services,
            fixture,
            review,
            support.item(fixture, packageId, fixture.documentRequirementId),
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-answer"
        )
        support.patchNarrative(services, fixture, fixture.documentRequirementId, "A correction waiting to be submitted")
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                services.reviewCycles.reopen(
                    ReopenInformationRequestReviewCommand(
                        fixture.requestId,
                        review.id,
                        InformationRequestReviewKind.RECONSIDERATION,
                        "Check the outcome",
                        support.owner(fixture),
                        CommandPrecondition.ExpectedRevision(support.reviewETag(review.id)),
                        "reopen-pending-answer"
                    )
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_NOT_REOPENABLE, refusal.reasonCode)
    }

    @Test
    fun `delegation audits identify both assignments and the delegate party`() {
        val otherParty = UUID.randomUUID()
        val otherUser = UUID.randomUUID()
        val fixture = fixture(prepare = { it.insertActingParty(otherParty, "REVIEWER", otherUser) })
        dataSource.connection.use { grantPartyShare(it, fixture.requestId, otherParty, otherUser, "REVIEWER") }
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val delegated = QuarkusTransaction.requiringNew().call {
            services.reviewAssignments.change(
                support.changeCommand(
                    fixture,
                    assigned.assignment!!,
                    InformationRequestReviewAssignmentChange.DELEGATION,
                    support.owner(fixture),
                    delegatePartyId = otherParty
                )
            )
        }
        val payload = requireNotNull(dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT payload_json FROM audit_outbox WHERE target_id = ? AND payload_json::jsonb ->> 'assignmentChange' = 'DELEGATION'",
                fixture.requestId.toString()
            )
        })
        assertTrue(payload.contains("\"delegateAssignmentId\":\"${delegated.assignment!!.id}\""), payload)
        assertTrue(payload.contains("\"delegatePartyId\":\"$otherParty\""), payload)
        assertTrue(payload.contains(assigned.assignment!!.id.toString()), payload)
    }

    @Test
    fun `review HTTP reads project comment and item waiver permissions`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)
        val response =
            InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("review-standing-read")
                .get("/information-requests/${fixture.requestId}/reviews/${review.id}")
        assertEquals(200, response.statusCode, response.asString())
        assertAll(
            { assertEquals("true", response.jsonPath().getString("canComment")) },
            { assertEquals("false", response.jsonPath().getString("items[0].waiverPermitted")) },
        )
    }

    @Test
    fun `an unsupported waiver names its requirement over HTTP`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val draft = support.draft(services, fixture, assigned, InformationRequestReviewOutcome.WAIVED)
        val response =
            InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("unsupported-waiver")
                .header("If-Match", draft)
                .post("/information-requests/${fixture.requestId}/reviews/${review.id}/assignments/${assigned.assignment!!.id}/decisions")
        assertEquals(409, response.statusCode, response.asString())
        assertEquals(
            InformationRequestErrorCatalog.REVIEW_OUTCOME_NOT_PERMITTED,
            response.jsonPath().getString("reasonCode")
        )
        assertEquals(
            fixture.documentRequirementId.toString(),
            response.jsonPath().getString("problems[0].requirementId")
        )
    }

    @Test
    fun `review queue HTTP entries name their request Exchange and stage`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)
        val response = InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("queue-labels")
            .get("/information-request-reviews")
        assertEquals(200, response.statusCode, response.asString())
        val entry = response.jsonPath().getList<Map<String, Any>>("$")
            .single { it["informationRequestId"] == fixture.requestId.toString() }
        val title = dataSource.connection.use {
            queryString(
                it,
                "SELECT display_name FROM information_request_template_definition WHERE id = ?",
                fixture.runtime.template.definitionId
            )
        }
        val exchange = dataSource.connection.use {
            queryString(
                it,
                "SELECT name FROM exchange WHERE id = ?",
                fixture.runtime.exchangeId
            )
        }
        assertAll(
            { assertEquals(title, entry["requestTitle"]) },
            { assertEquals(exchange, entry["exchangeName"]) },
            { assertTrue((entry["reviewStageTitle"] as? String)?.isNotBlank() == true) },
        )
    }

    @Test
    fun `withdrawable standing follows the caller recovery permission over HTTP`() {
        val otherUser = UUID.randomUUID()
        val otherParty = UUID.randomUUID()
        val fixture = fixture(prepare = { it.insertActingParty(otherParty, "CONTRIBUTOR", otherUser) })
        dataSource.connection.use { grantPartyShare(it, fixture.requestId, otherParty, otherUser, "CONTRIBUTOR") }
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val reader =
            InformationRequestSignedInCaller.signIn(appUsers, fixture.reviewerUserId).command("read-withdrawal")
                .get("/information-requests/${fixture.requestId}/submissions")
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, otherUser).command("recover-withdrawal")
            .get("/information-requests/${fixture.requestId}/submissions")
        assertAll(
            { assertEquals(200, reader.statusCode, reader.asString()) },
            { assertEquals(200, contributor.statusCode, contributor.asString()) },
            { assertEquals("false", reader.jsonPath().getString("[0].withdrawable")) },
            { assertEquals("true", contributor.jsonPath().getString("[0].withdrawable")) },
            { assertEquals("false", contributor.jsonPath().getString("[0].submittedByCaller")) },
        )
    }

    @Test
    fun `submission preview HTTP freezes the visible answers covered by its ETag`() {
        val readerUser = UUID.randomUUID()
        val readerParty = UUID.randomUUID()
        val support = com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport(
            dataSource,
            runtime,
            requests
        )
        val prepared = support.fieldRequest(prepare = { connection, configured ->
            configured.runtime.insertActingParty(readerParty, "REVIEWER", readerUser)
            grantPartyShare(connection, configured.requestId, readerParty, readerUser, "REVIEWER")
        })
        val services = runtime.build(prepared.requestId, centralAuthorization = authorization, executionGrants = grants)
        support.answerField(services, prepared, "Earlier recorded value", "preview-earlier")
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.runtime.contributorUserId)
        val first =
            caller.command("read-earlier-preview").get("/information-requests/${prepared.requestId}/submission-preview")
        val context = io.quarkus.arc.Arc.container().requestContext()
        context.activate()
        try {
            support.answerField(services, prepared, "Updated recorded value", "preview-updated")
        } finally {
            context.terminate()
        }
        val latest =
            caller.command("read-latest-preview").get("/information-requests/${prepared.requestId}/submission-preview")
        val reader = InformationRequestSignedInCaller.signIn(appUsers, readerUser).command("read-withheld-preview")
            .get("/information-requests/${prepared.requestId}/submission-preview")
        assertEquals(200, first.statusCode, first.asString())
        assertEquals(200, latest.statusCode, latest.asString())
        assertEquals(200, reader.statusCode, reader.asString())
        val firstItems = first.jsonPath().getList<Map<String, Any>>("scopeItems")
        val latestItems = latest.jsonPath().getList<Map<String, Any>>("scopeItems")
        val hiddenItems = reader.jsonPath().getList<Map<String, Any>>("scopeItems")
        assertAll(
            {
                assertNotEquals(
                    first.jsonPath().getString("submissionETag"),
                    latest.jsonPath().getString("submissionETag")
                )
            },
            {
                assertEquals(
                    "Earlier recorded value",
                    firstItems?.find { it["requirementId"] == prepared.answers.requirementId.toString() }
                        ?.get("fieldValue")
                )
            },
            {
                assertEquals(
                    "Updated recorded value",
                    latestItems?.find { it["requirementId"] == prepared.answers.requirementId.toString() }
                        ?.get("fieldValue")
                )
            },
            { assertEquals(emptyList<Map<String, Any>>(), hiddenItems) },
        )
    }

    @Test
    fun `assignment change narrative is readable over HTTP`() {
        val fixture = fixture()
        val services = services(fixture)
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        QuarkusTransaction.requiringNew().run {
            services.reviewAssignments.change(
                support.changeCommand(
                    fixture, assigned.assignment!!,
                    InformationRequestReviewAssignmentChange.RECUSAL, support.reviewer(fixture), reasonCode = "conflict"
                )
                    .copy(narrative = "The reviewer prepared the recorded detail")
            )
        }
        val response = InformationRequestSignedInCaller.signIn(appUsers, fixture.runtime.template.userId)
            .command("read-assignment-history").get("/information-requests/${fixture.requestId}/reviews/${review.id}")
        assertEquals(200, response.statusCode, response.asString())
        assertEquals(
            "The reviewer prepared the recorded detail",
            response.jsonPath().getString("assignments[0].changeNarrative")
        )
    }

    private fun fixture(
        prepare: (SubmissionRuntimeSqlFixture) -> Unit = {},
        beforePublish: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> }
    ): ReviewFixture =
        support.fixture(beforePublish = beforePublish, prepare = prepare).also { fixture ->
            dataSource.connection.use { connection ->
                grantRespondentShares(connection, fixture.runtime)
                grantPartyShare(
                    connection,
                    fixture.requestId,
                    fixture.reviewerPartyId,
                    fixture.reviewerUserId,
                    "REVIEWER"
                )
                execute(
                    connection,
                    "INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at) VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())",
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.runtime.template.userId
                )
                execute(
                    connection,
                    """INSERT INTO request_execution_grant (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode, acting_party_cap, granted_features, continuation_actions, issued_at, created_at)
                    VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, ?, ?, now(), now())""",
                    UUID.randomUUID(),
                    fixture.requestId,
                    fixture.runtime.template.organizationId,
                    connection.createArrayOf("varchar", typedAnswerGrantFeatures.toTypedArray()),
                    connection.createArrayOf("varchar", everyContinuationAction.toTypedArray())
                )
            }
        }

    private fun services(fixture: ReviewFixture) =
        runtime.build(fixture.requestId, centralAuthorization = authorization, executionGrants = grants)
}