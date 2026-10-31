package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.InformationRequestReviewDtoMapper
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewResultItem
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.model.informationrequest.review.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.model.ReturnedReviewScenario
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewQueryTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `respondents see only respondent-visible findings, and only once the review settles`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)
        val shared =
            support.finding(services, fixture, review, itemId, InformationRequestFindingCorrectionScope.RESPONSE)
        support.finding(
            services,
            fixture,
            review,
            itemId,
            InformationRequestFindingCorrectionScope.NONE,
            visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
        )

        val pending = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }
        assertTrue(pending.single().visibleFindings.isEmpty())
        val early = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewComments.record(respondentComment(fixture, review.id, itemId, shared.id)) }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, early.reasonCode)

        support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return"
        )
        QuarkusTransaction.requiringNew().run {
            services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    submissionItemId = itemId,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    body = "Kept among reviewers",
                    access = support.reviewer(fixture),
                    idempotencyKey = "reviewer-note",
                ),
            )
        }
        val answered = QuarkusTransaction.requiringNew().call {
            services.reviewComments.record(respondentComment(fixture, review.id, itemId, shared.id))
        }
        assertEquals(InformationRequestReviewVisibility.RESPONDENT_VISIBLE, answered.comment!!.visibility)

        val settled = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }.single()
        assertEquals(listOf(shared.id), settled.visibleFindings.map { it.id })
        assertEquals(listOf(answered.comment!!.id), settled.visibleComments.map { it.id })
        assertTrue(InformationRequestReviewResultItem(itemId, fixture.documentRequirementId) in settled.visibleItems)
        assertEquals(listOf(fixture.documentRequirementId), settled.correction!!.items.map { it.requirementId })
        assertTrue(settled.canAppeal)
        assertTrue(settled.canComment)
    }

    @Test
    fun `a withdrawn review that held a manager's finding shows the respondent no finding and takes no reply`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)
        val finding = support.finding(
            services,
            fixture,
            review,
            itemId,
            InformationRequestFindingCorrectionScope.RESPONSE,
            reviewerAccess = support.owner(fixture),
        )
        QuarkusTransaction.requiringNew().run {
            services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    packageId = packageId,
                    access = support.contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(support.responseETag(fixture)),
                    idempotencyKey = "withdraw-with-finding",
                ),
            )
        }

        val result = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }
            .single { it.review.id == review.id }
        val reply = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewComments.record(respondentComment(fixture, review.id, itemId, finding.id)) }
        }

        assertEquals(InformationRequestReviewState.WITHDRAWN, result.review.state)
        assertTrue(result.visibleFindings.isEmpty())
        assertFalse(result.canComment)
        assertEquals(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, reply.reasonCode)
    }

    @Test
    fun `a reviewer reads the content it may review while an administrator reads only the review's standing`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)

        val asReviewer = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.reviewer(fixture)) }
        assertTrue(asReviewer.contentVisibleItemIds.isNotEmpty())
        assertEquals(1, asReviewer.callerAssignmentIds.size)
        assertFalse(asReviewer.canManage)

        val asOwner = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.owner(fixture)) }
        assertTrue(asOwner.contentVisibleItemIds.isEmpty())
        assertTrue(asOwner.callerAssignmentIds.isEmpty())
        assertTrue(asOwner.canManage)

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewQueries.reviews(fixture.requestId, support.contributor(fixture)) }
        }
    }

    @Test
    fun `a reviewer and an administrator read every reviewed item under the prompt its Template states`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review)

        listOf(support.reviewer(fixture), support.owner(fixture)).forEach { caller ->
            val readable = QuarkusTransaction.requiringNew()
                .call { services.reviewQueries.review(fixture.requestId, review.id, caller) }
            val items = Json.encodeToJsonElement(
                InformationRequestReviewDto.serializer(),
                InformationRequestReviewDtoMapper.toDto(readable, caller.principal),
            ).jsonObject.getValue("items").jsonArray

            assertTrue(items.isNotEmpty())
            assertEquals(
                List(items.size) { "Provide the recorded item" },
                items.map { it.jsonObject["prompt"]?.jsonPrimitive?.contentOrNull },
            )
        }
    }

    @Test
    fun `the reviewer queue lists open assignments by due instant and drops decided ones`()
    {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val due = Instant.now().plus(Duration.ofDays(3))
        val assigned = support.assign(services, fixture, review, dueAt = due)

        dataSource.connection.use { connection ->
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at, revoked_at, revoked_reason)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 5, now(), now(), now(), 'record review')
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.runtime.template.organizationId,
            )
        }

        val queued = QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(support.reviewer(fixture)) }
        assertEquals(listOf(assigned.assignment!!.id), queued.map { it.assignment.id })
        assertEquals(
            InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED),
            queued.single().executionStanding,
        )
        assertEquals(due.epochSecond, queued.single().assignment.dueAt!!.toInstant().epochSecond)
        assertEquals(1, queued.single().itemCount)

        support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.SATISFIED),
            "accept"
        )
        assertTrue(QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(support.reviewer(fixture)) }
            .isEmpty())
    }

    @Test
    fun `a member of a reviewer group finds the group's open assignment in the reviewer queue`() {
        val fixture = support.fixture()
        val memberId = UUID.randomUUID()
        val groupPartyId = UUID.randomUUID()
        reviewerGroup(fixture, memberId, groupPartyId)
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, memberId))
        )
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review, partyId = groupPartyId)
        val member =
            RequestAccessContext(PrincipalRef.user(memberId), AuthorizationContext(sessionRef = "member-session"))
        val outsider = RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(sessionRef = "outsider-session")
        )

        val queued = QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(member) }

        assertEquals(listOf(assigned.assignment!!.id), queued.map { it.assignment.id })
        assertTrue(QuarkusTransaction.requiringNew().call { services.reviewQueries.queue(outsider) }.isEmpty())
    }

    @Test
    fun `respondent results list remediations only for the findings shown to the respondent while reviewers see every remediation`() {
        val scenario = returnedAndResubmitted()
        val fixture = scenario.fixture
        val hiding = runtime.build(
            fixture.requestId,
            centralAuthorization = support.authorization(
                support.roleDenials(fixture),
                setOf(
                    Triple(
                        fixture.runtime.contributorUserId,
                        Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                        fixture.attestationRequirementId
                    )
                ),
            ),
        )

        val returned = QuarkusTransaction.requiringNew()
            .call { hiding.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }
            .single { it.review.id == scenario.first.id }
        assertEquals(listOf(scenario.shown.id), returned.visibleFindings.map { it.id })
        assertEquals(listOf(scenario.shown.id), returned.remediations.map { it.findingId })

        val asReviewer = QuarkusTransaction.requiringNew().call {
            hiding.reviewQueries.review(fixture.requestId, scenario.first.id, support.reviewer(fixture))
        }
        assertEquals(
            setOf(scenario.shown.id, scenario.reviewersOnly.id, scenario.hiddenRequirement.id),
            asReviewer.remediations.map { it.findingId }.toSet(),
        )
    }

    @Test
    fun `a resubmission review lists the earlier findings of its chain and their remediations for the items its reviewer may review`() {
        val scenario = returnedAndResubmitted()
        val fixture = scenario.fixture
        val services = scenario.services
        val second = support.reviewsOf(fixture).last()
        val firstFindings = listOf(scenario.shown.id, scenario.reviewersOnly.id, scenario.hiddenRequirement.id)

        val secondView = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, second.id, support.reviewer(fixture)) }
        assertEquals(firstFindings, secondView.earlierFindings.map { it.id })
        assertEquals(firstFindings.toSet(), secondView.earlierRemediations.map { it.findingId }.toSet())
        assertTrue(
            QuarkusTransaction.requiringNew()
                .call { services.reviewQueries.review(fixture.requestId, second.id, support.owner(fixture)) }
                .earlierFindings.isEmpty(),
        )

        val assigned = support.assign(services, fixture, second)
        val documentItem = support.item(fixture, scenario.resubmittedPackageId, fixture.documentRequirementId)
        val attestationItem = support.item(fixture, scenario.resubmittedPackageId, fixture.attestationRequirementId)
        val retest = QuarkusTransaction.requiringNew().call {
            services.reviewFindings.record(
                support.findingCommand(fixture, second, documentItem, InformationRequestFindingCorrectionScope.NONE)
                    .copy(
                        retestsFindingId = secondView.earlierFindings.first().id,
                        retestResult = InformationRequestRetestResult.UNRESOLVED,
                    ),
            )
        }.finding!!
        assertEquals(scenario.shown.id, retest.retestsFindingId)
        val followUp =
            support.finding(services, fixture, second, documentItem, InformationRequestFindingCorrectionScope.RESPONSE)
        support.record(
            services,
            fixture,
            assigned,
            support.draft(
                services,
                fixture,
                assigned,
                outcomes = mapOf(
                    documentItem to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                    attestationItem to InformationRequestReviewOutcome.SATISFIED,
                ),
            ),
            "return-again",
        )
        support.patchNarrative(services, fixture, fixture.documentRequirementId, "The note corrected again")
        support.submitWhole(services, fixture)
        val third = support.reviewsOf(fixture).last()

        val full = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, third.id, support.reviewer(fixture)) }
        assertEquals(firstFindings + listOf(retest.id, followUp.id), full.earlierFindings.map { it.id })
        assertEquals((firstFindings + followUp.id).toSet(), full.earlierRemediations.map { it.findingId }.toSet())

        val hiding = runtime.build(
            fixture.requestId,
            centralAuthorization = support.authorization(
                support.roleDenials(fixture),
                setOf(
                    Triple(
                        fixture.reviewerUserId,
                        Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
                        fixture.attestationRequirementId
                    )
                ),
            ),
        )
        val partial = QuarkusTransaction.requiringNew()
            .call { hiding.reviewQueries.review(fixture.requestId, third.id, support.reviewer(fixture)) }
        assertEquals(
            listOf(scenario.shown.id, scenario.reviewersOnly.id, retest.id, followUp.id),
            partial.earlierFindings.map { it.id },
        )
        assertEquals(
            setOf(scenario.shown.id, scenario.reviewersOnly.id, followUp.id),
            partial.earlierRemediations.map { it.findingId }.toSet(),
        )
    }

    @Test
    fun `only an administrator may reconsider, and only the latest rejected or returned review of a current package on an open request`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val itemId = support.item(fixture, packageId, fixture.documentRequirementId)
        fun reconsiderable(reviewId: UUID, access: RequestAccessContext = support.owner(fixture)) =
            QuarkusTransaction.requiringNew()
                .call { services.reviewQueries.review(fixture.requestId, reviewId, access) }.canReconsider

        fun reject(target: InformationRequestReview, key: String) {
            val assigned = support.assign(services, fixture, target)
            support.finding(services, fixture, target, itemId, InformationRequestFindingCorrectionScope.NONE)
            support.record(
                services,
                fixture,
                assigned,
                support.draft(services, fixture, assigned, InformationRequestReviewOutcome.REJECTED),
                key
            )
        }

        fun reconsideration(reviewId: UUID, key: String) = ReopenInformationRequestReviewCommand(
            requestId = fixture.requestId,
            reviewId = reviewId,
            kind = InformationRequestReviewKind.RECONSIDERATION,
            reason = "The recorded item was complete",
            access = support.owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(reviewId)),
            idempotencyKey = key,
        )

        assertFalse(reconsiderable(review.id))
        reject(review, "reject-first")
        assertTrue(reconsiderable(review.id))
        assertFalse(reconsiderable(review.id, support.reviewer(fixture)))

        val reopened = QuarkusTransaction.requiringNew()
            .call { services.reviewCycles.reopen(reconsideration(review.id, "reconsider-first")) }
        assertFalse(reconsiderable(review.id))
        assertFalse(reconsiderable(reopened.review.id))
        val superseded = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { services.reviewCycles.reopen(reconsideration(review.id, "reconsider-again")) }
        }
        assertEquals(InformationRequestErrorCatalog.REVIEW_NOT_REOPENABLE, superseded.reasonCode)

        reject(support.reviewOf(reopened.review.id), "reject-second")
        assertTrue(reconsiderable(reopened.review.id))
        QuarkusTransaction.requiringNew().run {
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = fixture.requestId,
                    reasonCode = "no-longer-needed",
                    access = support.owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId))),
                    ),
                    idempotencyKey = "cancel-request",
                ),
            )
        }
        assertFalse(reconsiderable(reopened.review.id))
    }

    @Test
    fun `a returned file is named by its version and declared file name for reviewers, respondents, and the submission view`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val versionId = fixture.runtime.evidenceVersionId
        support.finding(
            services,
            fixture,
            review,
            documentItem,
            InformationRequestFindingCorrectionScope.EVIDENCE_VERSION,
            evidenceVersionId = versionId,
        )
        support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-file"
        )

        val asReviewer = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.review(fixture.requestId, review.id, support.reviewer(fixture)) }
        val returned = asReviewer.correction!!.evidence.single()
        assertEquals(versionId, returned.evidenceVersionId)
        assertEquals(fixture.artifactId, returned.evidenceArtifactId)
        assertEquals(1, returned.versionNumber)
        assertEquals("process-record.pdf", returned.declaredFileName)
        assertEquals("process-record.pdf", asReviewer.evidenceVersions[versionId]?.declaredFileName)

        val asRespondent = QuarkusTransaction.requiringNew()
            .call { services.reviewQueries.results(fixture.requestId, support.contributor(fixture)) }.single()
        assertEquals(listOf(returned), asRespondent.correction!!.evidence)
        val submitted = QuarkusTransaction.requiringNew()
            .call { services.submissionQueries.packages(fixture.requestId, support.contributor(fixture)) }.single()
        assertEquals("process-record.pdf", submitted.evidenceVersions[versionId]?.declaredFileName)
    }

    private fun returnedAndResubmitted(): ReturnedReviewScenario {
        val fixture = support.fixture(beforePublish = { connection, runtime -> reviewAttestation(connection, runtime) })
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        val packageId = support.submitWhole(services, fixture)
        val first = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, first)
        val documentItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val attestationItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        val shown =
            support.finding(services, fixture, first, documentItem, InformationRequestFindingCorrectionScope.RESPONSE)
        val reviewersOnly = support.finding(
            services,
            fixture,
            first,
            documentItem,
            InformationRequestFindingCorrectionScope.RESPONSE,
            visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
        )
        val hiddenRequirement = support.finding(
            services,
            fixture,
            first,
            attestationItem,
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-both"
        )
        support.patchNarrative(services, fixture, fixture.documentRequirementId, "The corrected note")
        val resubmitted = support.submitWhole(services, fixture)
        return ReturnedReviewScenario(fixture, services, first, shown, reviewersOnly, hiddenRequirement, resubmitted)
    }

    private fun respondentComment(fixture: ReviewFixture, reviewId: UUID, itemId: UUID, findingId: UUID) =
        RecordInformationRequestReviewCommentCommand(
            requestId = fixture.requestId,
            reviewId = reviewId,
            submissionItemId = itemId,
            findingId = findingId,
            body = "The note now states what was asked",
            access = support.contributor(fixture),
            idempotencyKey = "respondent-reply-${UUID.randomUUID()}",
        )

    private fun reviewerGroup(fixture: ReviewFixture, memberId: UUID, partyId: UUID) {
        val groupId = UUID.randomUUID()
        val shareId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                memberId,
                "group-reviewer-${memberId.toString().take(8)}@process.test",
            )
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO principal_group (id, name, scope, owner_app_user_id, externally_published, is_active, created_date)
                VALUES (?, 'Process reviewers', 'PERSONAL', ?, FALSE, TRUE, now())
                """.trimIndent(),
                groupId,
                fixture.runtime.template.userId,
            )
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO principal_group_member (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
                VALUES (?, ?, 'USER', ?, 'MEMBER', now(), TRUE)
                """.trimIndent(),
                UUID.randomUUID(),
                groupId,
                memberId,
            )
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'PRINCIPAL_GROUP', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                fixture.requestId,
                groupId,
            )
            com.docuhyphen.app.api.migration.execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'REVIEWER', 'PRINCIPAL_GROUP', ?, ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                partyId,
                fixture.requestId,
                groupId,
                shareId,
            )
        }
    }
}
