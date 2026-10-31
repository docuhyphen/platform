package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewDistinctReviewerTransactionTest {
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
    fun `a person who already holds an assignment in a stage is not assigned to it again through another party`() {
        val secondPartyId = UUID.randomUUID()
        val fixture = support.fixture(beforePublish = { connection, configured ->
            insertReviewStage(
                connection,
                configured.template.versionId,
                JOINT_STAGE,
                1,
                aggregation = "ALL",
                minimumReviewers = 2
            )
        })
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        support.assign(services, fixture, review, stageKey = JOINT_STAGE)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE, revoked_at = now(), party_revision = party_revision + 1 WHERE id = ?",
                fixture.reviewerPartyId,
            )
            reviewerParty(
                connection,
                fixture,
                secondPartyId,
                "USER",
                fixture.reviewerUserId
            )
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            support.assign(services, fixture, review, partyId = secondPartyId, stageKey = JOINT_STAGE)
        }

        assertEquals(InformationRequestErrorCatalog.REVIEWER_ALREADY_ASSIGNED, refusal.reasonCode)
    }

    @Test
    fun `a group member who decided a stage cannot decide it again for the group, so another member completes it`() {
        val groupId = UUID.randomUUID()
        val groupPartyId = UUID.randomUUID()
        val colleagueId = UUID.randomUUID()
        val fixture = support.fixture(
            beforePublish = { connection, configured ->
                insertReviewStage(
                    connection,
                    configured.template.versionId,
                    JOINT_STAGE,
                    1,
                    aggregation = "ALL",
                    minimumReviewers = 2
                )
            },
            prepare = { runtimeFixture -> runtimeFixture.insertUser(colleagueId) },
        )
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO principal_group (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
                VALUES (?, 'Review group', 'ORG', ?, FALSE, TRUE, now())
                """.trimIndent(),
                groupId,
                fixture.runtime.template.organizationId,
            )
            listOf(fixture.reviewerUserId, colleagueId).forEach { memberId ->
                execute(
                    connection,
                    """
                    INSERT INTO principal_group_member (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
                    VALUES (?, ?, 'USER', ?, 'MEMBER', now(), TRUE)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    groupId,
                    memberId,
                )
            }
            reviewerParty(connection, fixture, groupPartyId, "PRINCIPAL_GROUP", groupId)
        }
        val services = runtime.build(
            fixture.requestId,
            denies = support.roleDenials(fixture, setOf(fixture.reviewerUserId, colleagueId))
        )
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val individual = support.assign(services, fixture, review, stageKey = JOINT_STAGE)
        val group = support.assign(services, fixture, review, partyId = groupPartyId, stageKey = JOINT_STAGE)

        val first = support.record(
            services,
            fixture,
            individual,
            support.draft(services, fixture, individual, InformationRequestReviewOutcome.SATISFIED),
            "individual-decides"
        )
        val groupDraft = support.draft(services, fixture, group, InformationRequestReviewOutcome.SATISFIED)
        val again = assertThrows(InformationRequestLifecycleException::class.java) {
            support.record(services, fixture, group, groupDraft, "same-person-for-group")
        }
        val colleague = support.access(colleagueId)
        val settled =
            support.record(services, fixture, group, groupDraft, "colleague-for-group", reviewerAccess = colleague)

        assertEquals(InformationRequestReviewState.IN_REVIEW, first.review.state)
        assertEquals(InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED, again.reasonCode)
        assertEquals(InformationRequestReviewState.SATISFIED, settled.review.state)
        assertEquals(setOf(fixture.reviewerUserId.toString(), colleagueId.toString()), deciders(review.id))
    }

    private fun reviewerParty(
        connection: java.sql.Connection,
        fixture: ReviewFixture,
        partyId: UUID,
        principalKind: String,
        principalId: UUID
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'REVIEWER', ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            partyId,
            fixture.requestId,
            principalKind,
            principalId,
        )
    }

    private fun deciders(reviewId: UUID): Set<String> =
        dataSource.connection.use {
            queryStrings(
                it,
                "SELECT decided_by_principal_id::text FROM information_request_review_decision WHERE review_id = ? AND kind = 'REVIEWER'",
                reviewId
            )
        }

    private companion object {
        const val JOINT_STAGE = "joint-check"
    }
}
