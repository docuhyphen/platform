package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
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
class InformationRequestReviewerPartyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var reviewQueries: InformationRequestReviewQueryService

    private val support by lazy {
        InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository)
    }

    @Test
    fun `a reviewer party held by a participant cannot be assigned to a review stage`() {
        val participantPartyId = UUID.randomUUID()
        val fixture = support.fixture()
        dataSource.connection.use { connection -> participantReviewer(connection, fixture, participantPartyId) }
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            support.assign(services, fixture, review, partyId = participantPartyId)
        }

        assertEquals(InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED, refusal.reasonCode)
    }

    @Test
    fun `a reassigned reviewer finds the assignment in their queue through the central authorizer`() {
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))
        support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val successorId = UUID.randomUUID()
        dataSource.connection.use { connection -> reassignReviewer(connection, fixture, successorId) }

        val successorQueue = QuarkusTransaction.requiringNew().call { reviewQueries.queue(access(successorId)) }
        val formerQueue = QuarkusTransaction.requiringNew().call { reviewQueries.queue(access(fixture.reviewerUserId)) }

        assertEquals(listOf(assigned.assignment!!.id), successorQueue.map { it.assignment.id })
        assertEquals(emptyList<UUID>(), formerQueue.map { it.assignment.id })
    }

    private fun access(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "reviewer-$userId"))

    private fun participantReviewer(connection: java.sql.Connection, fixture: ReviewFixture, partyId: UUID) {
        val participantId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, LOWER(?), TRUE, now())
            """.trimIndent(),
            participantId,
            fixture.runtime.template.organizationId,
            "reviewer-${participantId.toString().take(8)}@process.test",
            "reviewer-${participantId.toString().take(8)}@process.test",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'REVIEWER', 'PARTICIPANT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            partyId,
            fixture.requestId,
            participantId,
        )
    }

    private fun reassignReviewer(connection: java.sql.Connection, fixture: ReviewFixture, successorId: UUID) {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
            successorId,
            "successor-${successorId.toString().take(8)}@process.test",
        )
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            fixture.requestId,
            successorId,
        )
        execute(
            connection,
            """
            UPDATE information_request_party
            SET principal_id = ?, share_id = ?, party_revision = party_revision + 1, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            successorId,
            shareId,
            fixture.reviewerPartyId,
        )
    }
}
