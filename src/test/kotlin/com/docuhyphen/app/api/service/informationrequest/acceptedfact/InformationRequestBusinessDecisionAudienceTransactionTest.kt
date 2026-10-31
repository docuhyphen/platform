package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecision
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestBusinessDecisionAudienceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `business decisions are read only by the people who manage the request`() {
        val scenario = scenario()
        val recorded = QuarkusTransaction.requiringNew()
            .call { scenario.services.businessDecisions.record(command(scenario, scenario.manager, "record-once")) }
            .decision

        listOf(scenario.manager, scenario.owner).forEach { reader ->
            assertEquals(listOf(recorded.id), decisions(scenario, reader).map { it.id })
        }
        listOf(scenario.contributor, scenario.attestor, scenario.reviewer).forEach { reader ->
            assertTrue(permits(reader, Action.INFORMATION_REQUEST_VIEW, scenario.requestId))
            assertThrows(ForbiddenException::class.java) { decisions(scenario, reader) }
        }
    }

    @Test
    fun `a recorded decision is replayed only while its caller still manages the request`() {
        val scenario = scenario()
        val command = command(scenario, scenario.manager, "record-then-step-down")
        QuarkusTransaction.requiringNew().call { scenario.services.businessDecisions.record(command) }

        withdrawRole(scenario, scenario.managerId, "DECISION_MAKER")

        assertTrue(permits(scenario.manager, Action.INFORMATION_REQUEST_VIEW, scenario.requestId))
        assertTrue(!permits(scenario.manager, Action.INFORMATION_REQUEST_RECORD_DECISION, scenario.requestId))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.businessDecisions.record(command) }
        }
    }

    @Test
    fun `decision references that only move a separator between them are a different command`() {
        val scenario = scenario()
        val original = command(scenario, scenario.manager, "reference-retry").copy(
            reasonReference = "reason|segment",
            externalReference = "record",
        )
        val recorded = QuarkusTransaction.requiringNew().call { scenario.services.businessDecisions.record(original) }
        val replayed = QuarkusTransaction.requiringNew().call { scenario.services.businessDecisions.record(original) }

        assertEquals(recorded.decision.id, replayed.decision.id)
        assertEquals("reason|segment", recorded.decision.reasonReference)
        assertEquals("record", recorded.decision.externalReference)
        val moved = original.copy(reasonReference = "reason", externalReference = "segment|record")
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.businessDecisions.record(moved) }
        }
    }

    private fun decisions(scenario: Scenario, reader: RequestAccessContext): List<InformationRequestBusinessDecision> =
        QuarkusTransaction.requiringNew()
            .call { scenario.services.businessDecisions.decisions(scenario.requestId, reader) }

    private fun command(scenario: Scenario, access: RequestAccessContext, key: String) =
        RecordInformationRequestBusinessDecisionCommand(
            requestId = scenario.requestId,
            owningProcessKey = "process.outcome",
            outcomeCode = "outcome.recorded",
            reasonReference = "reason-reference",
            externalReference = "external-reference",
            kind = InformationRequestBusinessDecisionKind.ORIGINAL,
            priorDecisionId = null,
            decidedAt = Instant.now().minus(Duration.ofMinutes(5)),
            access = access,
            idempotencyKey = key,
        )

    private fun scenario(): Scenario {
        val managerId = UUID.randomUUID()
        val reviewerId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { fixture ->
                val managerPartyId = UUID.randomUUID()
                val managerContributorPartyId = UUID.randomUUID()
                val reviewerPartyId = UUID.randomUUID()
                fixture.insertActingParty(managerPartyId, "DECISION_MAKER", managerId)
                fixture.insertPartyForUser(managerContributorPartyId, "CONTRIBUTOR", managerId)
                fixture.insertActingParty(reviewerPartyId, "REVIEWER", reviewerId)
                listOf(
                    Triple(fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR"),
                    Triple(fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR"),
                    Triple(managerPartyId, managerId, "DECISION_MAKER"),
                    Triple(managerContributorPartyId, managerId, "CONTRIBUTOR"),
                    Triple(reviewerPartyId, reviewerId, "REVIEWER"),
                ).forEach { (partyId, userId, role) ->
                    val shareId = requestShare(connection, fixture.requestId, userId, role)
                    execute(
                        connection,
                        "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                        shareId,
                        partyId
                    )
                }
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
            }
        }
        return Scenario(
            fixture,
            runtime.build(fixture.requestId, centralAuthorization = authorizationService),
            managerId,
            reviewerId,
        )
    }

    private fun requestShare(connection: Connection, requestId: UUID, userId: UUID, role: String): UUID {
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

    private fun withdrawRole(scenario: Scenario, userId: UUID, role: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share SET status = 'REVOKED', revoked_at = now()
                WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND principal_id = ? AND role_name = ?
                """.trimIndent(),
                scenario.requestId,
                userId,
                role,
            )
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE WHERE information_request_id = ? AND principal_id = ? AND role_key = ?",
                scenario.requestId,
                userId,
                role,
            )
        }
    }

    private fun permits(access: RequestAccessContext, action: Action, requestId: UUID): Boolean =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                access.principal,
                action,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ) is Decision.Allow
        }

    private class Scenario(
        val fixture: SubmissionRuntimeSqlFixture,
        val services: InformationRequestRuntimeServices,
        val managerId: UUID,
        reviewerId: UUID,
    ) {
        val requestId: UUID get() = fixture.requestId
        val manager = access(managerId)
        val owner = access(fixture.template.userId)
        val contributor = access(fixture.contributorUserId)
        val attestor = access(fixture.attestorUserId)
        val reviewer = access(reviewerId)

        private fun access(userId: UUID) =
            RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "decision-session"))
    }
}
