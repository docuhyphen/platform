package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestDelegatedAuthorityDto
import com.docuhyphen.app.api.model.dto.InformationRequestReviewDto
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDraftPatch
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewDecisionsCommand
import com.docuhyphen.app.api.model.informationrequest.review.SaveInformationRequestReviewDraftCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.resource.informationrequest.party.InformationRequestDelegatedAuthorityResource
import com.docuhyphen.app.api.resource.informationrequest.review.InformationRequestReviewResource
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityService
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewRecordAudienceTransactionTest {
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
    lateinit var delegatedAuthorities: InformationRequestDelegatedAuthorityService

    private val support by lazy {
        InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository)
    }

    @Test
    fun `a reviewer reloads and retries its final decisions after they close the request without regaining review`() {
        val scenario = closedScenario()
        val reviewer = scenario.reviewer(verified = true)
        assertEquals(InformationRequestState.CLOSED, support.state(scenario.fixture))

        val reloaded = detail(scenario, reviewer)
        assertEquals(setOf(scenario.protectedItem, scenario.openItem), visibleItems(reloaded))
        assertEquals(setOf(PROTECTED_COMMENT, OPEN_COMMENT), reloaded.comments.map { it.body }.toSet())
        assertEquals(
            setOf(PROTECTED_ASSESSMENT, OPEN_ASSESSMENT),
            reloaded.decisions.mapNotNull { it.narrative }.toSet()
        )
        assertEquals(0, reloaded.itemsAwaitingStepUp)
        assertFalse(reloaded.canManage)
        assertEquals(listOf(scenario.reviewId), listed(scenario, reviewer).map { it.review.id })

        val replayed = QuarkusTransaction.requiringNew()
            .call { scenario.services.reviewDecisions.recordDecisions(scenario.finalDecisions) }
        assertEquals(scenario.reviewId, replayed.review.id)
        assertEquals(InformationRequestState.CLOSED, replayed.request.state)

        assertArchived(reviewer, Action.INFORMATION_REQUEST_REVIEW, ResourceRef.informationRequest(scenario.requestId))
        assertArchived(
            reviewer,
            Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
            ResourceRef.informationRequestRequirement(scenario.openRequirementId),
        )
    }

    @Test
    fun `a closed protected item still needs recent verification before its review content is read`() {
        val scenario = closedScenario()

        val unverified = detail(scenario, scenario.reviewer(verified = false))

        assertEquals(setOf(scenario.openItem), visibleItems(unverified))
        assertEquals(1, unverified.itemsAwaitingStepUp)
        assertEquals(listOf(OPEN_COMMENT), unverified.comments.map { it.body })
        assertEquals(listOf(OPEN_ASSESSMENT), unverified.decisions.mapNotNull { it.narrative })
    }

    @Test
    fun `request administrators read closed reviews and recorded delegated authorities without managing either`() {
        val scenario = closedScenario()

        listOf(scenario.exchangeOwner(), scenario.manager()).forEach { administrator ->
            val read = detail(scenario, administrator)
            assertTrue(visibleItems(read).isEmpty())
            assertTrue(read.comments.isEmpty())
            assertFalse(read.canManage)
            assertFalse(read.canReconsider)
            assertEquals(listOf(scenario.reviewId), listed(scenario, administrator).map { it.review.id })
            assertEquals(listOf(scenario.authorityId), authorities(scenario, administrator).map { it.id })
            val request = ResourceRef.informationRequest(scenario.requestId)
            assertArchived(administrator, Action.INFORMATION_REQUEST_MANAGE_REVIEWS, request)
            assertArchived(administrator, Action.INFORMATION_REQUEST_MANAGE_PARTIES, request)
            assertThrows(ForbiddenException::class.java) {
                delegatedAuthorities.grant(
                    GrantInformationRequestDelegatedAuthorityCommand(
                        requestId = scenario.requestId,
                        assignedPartyId = scenario.fixture.runtime.contributorPartyId,
                        delegatePrincipal = PrincipalRef.user(scenario.managerId),
                        access = administrator,
                        precondition = CommandPrecondition.ExpectedRevision("\"parties\""),
                        idempotencyKey = "closed-grant-${UUID.randomUUID()}",
                    ),
                )
            }
        }
        assertEquals(403, authorityStatus(scenario, scenario.reviewer(verified = true)))
        listOf(scenario.contributor(), scenario.attestor()).forEach { party ->
            assertEquals(403, detailStatus(scenario, party))
            assertEquals(403, listStatus(scenario, party))
            assertEquals(403, authorityStatus(scenario, party))
        }
    }

    @Test
    fun `closed review records follow the Exchange's retained access and end with the reader's Share`() {
        val scenario = closedScenario()
        val reviewer = scenario.reviewer(verified = true)
        val owner = scenario.exchangeOwner()

        exchangeStatus(scenario, "ENDED")
        assertEquals(setOf(scenario.protectedItem, scenario.openItem), visibleItems(detail(scenario, reviewer)))
        assertEquals(listOf(scenario.reviewId), listed(scenario, owner).map { it.review.id })
        assertEquals(listOf(scenario.authorityId), authorities(scenario, owner).map { it.id })

        exchangeStatus(scenario, "RESCINDED")
        assertEquals(403, detailStatus(scenario, reviewer))
        assertEquals(scenario.reviewId, detail(scenario, owner).review.id)
        assertEquals(listOf(scenario.authorityId), authorities(scenario, owner).map { it.id })

        exchangeStatus(scenario, "ENDED")
        revokeShares("INFORMATION_REQUEST", scenario.requestId, scenario.fixture.reviewerUserId)
        assertEquals(403, detailStatus(scenario, reviewer))
        assertEquals(403, listStatus(scenario, reviewer))
        revokeShares("EXCHANGE", scenario.fixture.runtime.exchangeId, scenario.fixture.runtime.template.userId)
        assertEquals(403, listStatus(scenario, owner))
        assertEquals(403, authorityStatus(scenario, owner))
    }

    @Test
    fun `a reviewer may ask for a supplemental request from the review it reads`() {
        val scenario = closedScenario()

        val read = detail(scenario, scenario.reviewer(verified = true))

        assertTrue(read.canRequestSupplement)
        assertEquals(currentRequestETag(scenario), read.requestETag)
    }

    private fun currentRequestETag(scenario: ClosedScenario): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(scenario.requestId)))
        }

    private fun closedScenario(): ClosedScenario {
        val managerId = UUID.randomUUID()
        val managerPartyId = UUID.randomUUID()
        val delegateId = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, configured ->
                reviewAttestation(connection, configured)
                execute(
                    connection,
                    "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = ? WHERE id = ?",
                    COMPARTMENT_KEY,
                    configured.documentBindingId,
                )
            },
            prepare = { configured ->
                configured.insertActingParty(managerPartyId, "DECISION_MAKER", managerId)
                configured.insertUser(delegateId)
            },
        )
        val authorityId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            grantShares(connection, fixture, managerPartyId, managerId)
            recordAuthority(connection, fixture, authorityId, delegateId)
        }
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        val reviewer = access(fixture.reviewerUserId, verified = true)
        val packageId = submit(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val protectedItem = support.item(fixture, packageId, fixture.documentRequirementId)
        val openItem = support.item(fixture, packageId, fixture.attestationRequirementId)
        comment(services, fixture, review.id, protectedItem, PROTECTED_COMMENT, reviewer)
        comment(services, fixture, review.id, openItem, OPEN_COMMENT, reviewer)
        val draftETag = QuarkusTransaction.requiringNew().call {
            services.reviewDecisions.saveDraft(
                SaveInformationRequestReviewDraftCommand(
                    requestId = fixture.requestId,
                    reviewId = review.id,
                    assignmentId = assigned.assignment!!.id,
                    patches = listOf(
                        InformationRequestReviewDraftPatch(
                            protectedItem,
                            InformationRequestReviewOutcome.SATISFIED,
                            PROTECTED_ASSESSMENT,
                        ),
                        InformationRequestReviewDraftPatch(
                            openItem,
                            InformationRequestReviewOutcome.SATISFIED,
                            OPEN_ASSESSMENT,
                        ),
                    ),
                    access = reviewer,
                    precondition = CommandPrecondition.ExpectedRevision(requireNotNull(assigned.draftETag)),
                ),
            )
        }.draftETag
        val finalDecisions = RecordInformationRequestReviewDecisionsCommand(
            requestId = fixture.requestId,
            reviewId = review.id,
            assignmentId = assigned.assignment!!.id,
            access = reviewer,
            precondition = CommandPrecondition.ExpectedRevision(draftETag),
            idempotencyKey = "final-decisions-${UUID.randomUUID()}",
        )
        QuarkusTransaction.requiringNew().call { services.reviewDecisions.recordDecisions(finalDecisions) }
        return ClosedScenario(
            fixture,
            services,
            review.id,
            protectedItem,
            openItem,
            finalDecisions,
            managerId,
            authorityId,
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
        reviewId: UUID,
        itemId: UUID,
        body: String,
        reviewer: RequestAccessContext,
    ) {
        QuarkusTransaction.requiringNew().run {
            services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = fixture.requestId,
                    reviewId = reviewId,
                    submissionItemId = itemId,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    body = body,
                    access = reviewer,
                    idempotencyKey = "comment-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun grantShares(connection: Connection, fixture: ReviewFixture, managerPartyId: UUID, managerId: UUID) {
        val runtimeFixture = fixture.runtime
        listOf(
            Triple(runtimeFixture.contributorPartyId, runtimeFixture.contributorUserId, "CONTRIBUTOR"),
            Triple(runtimeFixture.attestorPartyId, runtimeFixture.attestorUserId, "ATTESTOR"),
            Triple(fixture.reviewerPartyId, fixture.reviewerUserId, "REVIEWER"),
            Triple(managerPartyId, managerId, "DECISION_MAKER"),
        ).forEach { (partyId, userId, role) ->
            val shareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                fixture.requestId,
                userId,
                role,
            )
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

    private fun recordAuthority(connection: Connection, fixture: ReviewFixture, authorityId: UUID, delegateId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_delegated_authority
                (id, information_request_id, assigned_party_id, delegate_principal_kind, delegate_principal_id,
                 active, grantor_principal_kind, grantor_principal_id, effective_at, recorded_at, updated_at)
            VALUES (?, ?, ?, 'USER', ?, TRUE, 'USER', ?, now(), now(), now())
            """.trimIndent(),
            authorityId,
            fixture.requestId,
            fixture.runtime.contributorPartyId,
            delegateId,
            fixture.runtime.template.userId,
        )
    }

    private fun exchangeStatus(scenario: ClosedScenario, status: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET status = ? WHERE id = ?",
                status,
                scenario.fixture.runtime.exchangeId
            )
        }
    }

    private fun revokeShares(resourceType: String, resourceId: UUID, userId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share SET status = 'REVOKED', revoked_at = now()
                WHERE resource_type = ? AND resource_id = ? AND principal_kind = 'USER' AND principal_id = ?
                """.trimIndent(),
                resourceType,
                resourceId,
                userId,
            )
        }
    }

    private fun detail(scenario: ClosedScenario, access: RequestAccessContext): InformationRequestReviewDto {
        val response = detailResponse(scenario, access)
        assertEquals(200, response.status, "${response.entity}")
        return response.entity as InformationRequestReviewDto
    }

    private fun detailStatus(scenario: ClosedScenario, access: RequestAccessContext): Int =
        detailResponse(scenario, access).status

    private fun detailResponse(scenario: ClosedScenario, access: RequestAccessContext): Response =
        QuarkusTransaction.requiringNew().call {
            reviewResource(scenario, access).detail(scenario.requestId.toString(), scenario.reviewId.toString())
        }

    private fun listed(scenario: ClosedScenario, access: RequestAccessContext): List<InformationRequestReviewDto> {
        val response = listResponse(scenario, access)
        assertEquals(200, response.status, "${response.entity}")
        @Suppress("UNCHECKED_CAST")
        return (response.entity as Array<InformationRequestReviewDto>).toList()
    }

    private fun listStatus(scenario: ClosedScenario, access: RequestAccessContext): Int =
        listResponse(scenario, access).status

    private fun listResponse(scenario: ClosedScenario, access: RequestAccessContext): Response =
        QuarkusTransaction.requiringNew()
            .call { reviewResource(scenario, access).list(scenario.requestId.toString()) }

    private fun authorities(
        scenario: ClosedScenario,
        access: RequestAccessContext
    ): List<InformationRequestDelegatedAuthorityDto> {
        val response = authorityResponse(scenario, access)
        assertEquals(200, response.status, "${response.entity}")
        @Suppress("UNCHECKED_CAST")
        return (response.entity as Array<InformationRequestDelegatedAuthorityDto>).toList()
    }

    private fun authorityStatus(scenario: ClosedScenario, access: RequestAccessContext): Int =
        authorityResponse(scenario, access).status

    private fun authorityResponse(scenario: ClosedScenario, access: RequestAccessContext): Response =
        InformationRequestDelegatedAuthorityResource(delegatedAuthorities, contextOf(access))
            .list(scenario.requestId.toString())

    private fun reviewResource(scenario: ClosedScenario, access: RequestAccessContext) =
        InformationRequestReviewResource(
            scenario.services.reviewAssignments,
            scenario.services.reviewDecisions,
            scenario.services.reviewFindings,
            scenario.services.reviewComments,
            scenario.services.reviewCycles,
            scenario.services.reviewQueries,
            contextOf(access),
        )

    private fun contextOf(access: RequestAccessContext): InformationRequestAccessContextFactory =
        mock { on { currentAuthenticated() } doReturn access }

    private fun visibleItems(review: InformationRequestReviewDto): Set<UUID> =
        review.items.filter { it.contentVisible }.map { it.submissionItemId }.toSet()

    private fun assertArchived(access: RequestAccessContext, action: Action, resource: ResourceRef) {
        val decision = QuarkusTransaction.requiringNew()
            .call { authorizationService.authorize(access.principal, action, resource, access.authorization) }
        assertEquals(Decision.REASON_EXCHANGE_ARCHIVED, (decision as? Decision.Deny)?.reasonCode, "$action: $decision")
    }

    private fun access(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "closed-review-session", mfaSatisfied = verified),
        )

    @Suppress("LongParameterList")
    private inner class ClosedScenario(
        val fixture: ReviewFixture,
        val services: InformationRequestRuntimeServices,
        val reviewId: UUID,
        val protectedItem: UUID,
        val openItem: UUID,
        val finalDecisions: RecordInformationRequestReviewDecisionsCommand,
        val managerId: UUID,
        val authorityId: UUID,
    ) {
        val requestId: UUID get() = fixture.requestId
        val openRequirementId: UUID get() = fixture.attestationRequirementId

        fun reviewer(verified: Boolean) = access(fixture.reviewerUserId, verified)

        fun exchangeOwner() = access(fixture.runtime.template.userId, verified = true)

        fun manager() = access(managerId, verified = true)

        fun contributor() = access(fixture.runtime.contributorUserId, verified = true)

        fun attestor() = access(fixture.runtime.attestorUserId, verified = true)
    }

    private companion object {
        const val COMPARTMENT_KEY = "restricted-record"
        const val PROTECTED_COMMENT = "Restricted record note kept for reviewers"
        const val OPEN_COMMENT = "Open assertion note kept for reviewers"
        const val PROTECTED_ASSESSMENT = "Restricted record checked against the request"
        const val OPEN_ASSESSMENT = "Open assertion checked against the request"
    }
}
