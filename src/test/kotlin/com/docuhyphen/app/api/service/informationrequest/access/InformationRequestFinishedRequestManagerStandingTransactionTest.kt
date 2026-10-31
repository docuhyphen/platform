package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestFinishedRequestManagerStandingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var standing: InformationRequestCallerStandingService

    @Inject
    lateinit var header: InformationRequestHeaderProjectionService

    @Inject
    lateinit var parties: InformationRequestPartyQueryService

    @ParameterizedTest
    @CsvSource(
        "CLOSED, closed_at, ACCEPTED_STARTED",
        "CANCELLED, cancelled_at, ACCEPTED_STARTED",
        "CLOSED, closed_at, ENDED"
    )
    fun `the owner of a finished request keeps the manager standing, the owner details and every party's identity`(
        state: String,
        finishedAt: String,
        parentStatus: String,
    ) {
        val fixture = finished(state, finishedAt, parentStatus)
        val owner = access(fixture.template.userId)

        val callerStanding =
            QuarkusTransaction.requiringNew().call { standing.standingOf(request(fixture), owner, emptySet()) }
        val shownHeader = QuarkusTransaction.requiringNew().call { header.headerOf(request(fixture), owner) }
        val listed =
            QuarkusTransaction.requiringNew().call { parties.listForManagement(fixture.requestId, owner).parties }

        assertTrue(callerStanding.permissions.canManage)
        assertEquals(fixture.template.organizationId, shownHeader.ownerOrganizationId)
        assertEquals(fixture.template.versionId, shownHeader.templateVersionId)
        assertEquals(
            setOf(fixture.contributorUserId, fixture.attestorUserId),
            listed.filter { it.roleKey != InformationRequestShareRoleKey.SUBJECT }.mapNotNull { it.principalId }
                .toSet(),
        )
    }

    @Test
    fun `a respondent of a finished request still sees no manager standing, no owner details and only its own identity, with its Template Version`() {
        val fixture = finished("CLOSED", "closed_at", "ACCEPTED_STARTED")
        val contributor = access(fixture.contributorUserId)

        val callerStanding =
            QuarkusTransaction.requiringNew().call { standing.standingOf(request(fixture), contributor, emptySet()) }
        val shownHeader = QuarkusTransaction.requiringNew().call { header.headerOf(request(fixture), contributor) }
        val listed =
            QuarkusTransaction.requiringNew().call { parties.listForManagement(fixture.requestId, contributor).parties }

        assertFalse(callerStanding.permissions.canManage)
        assertNull(shownHeader.ownerOrganizationId)
        assertEquals(fixture.template.versionId, shownHeader.templateVersionId)
        assertEquals(setOf(fixture.contributorUserId), listed.mapNotNull { it.principalId }.toSet())
    }

    private fun finished(state: String, finishedAt: String, parentStatus: String): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.exchangeId,
                fixture.template.userId,
            )
            val contributorShareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                contributorShareId,
                fixture.requestId,
                fixture.contributorUserId,
            )
            execute(
                connection,
                "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                contributorShareId,
                fixture.contributorPartyId,
            )
            execute(
                connection,
                "UPDATE information_request SET state = ?, $finishedAt = now() WHERE id = ?",
                state,
                fixture.requestId,
            )
            execute(connection, "UPDATE exchange SET status = ? WHERE id = ?", parentStatus, fixture.exchangeId)
            fixture
        }

    private fun request(fixture: SubmissionRuntimeSqlFixture) =
        requireNotNull(requestRepository.findById(fixture.requestId))

    private fun access(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "finished-request-session"))
}
