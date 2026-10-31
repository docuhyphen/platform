package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.SupersedeInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SupersedeReplacementScenario
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SupersedeSourceWrites
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
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
class InformationRequestSupersedeReplacementAuthorizationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `a manager of the source alone cannot point it at a replacement they hold nothing on`() {
        val scenario = scenario()
        val replacementId = scenario.draftInSameExchange()
        val before = writesOf(scenario)

        assertTrue(permits(scenario.manager, Action.INFORMATION_REQUEST_SUPERSEDE, scenario.sourceId))
        assertFalse(permits(scenario.manager, Action.INFORMATION_REQUEST_VIEW, replacementId))
        val refusal = assertThrows(ForbiddenException::class.java) {
            supersede(scenario, scenario.manager, replacementId, "supersede-unshared")
        }

        assertEquals("Access denied to the replacement Information Request", refusal.message)
        assertSourceUnchanged(scenario, before)
    }

    @Test
    fun `a manager who may only view the replacement cannot supersede with it`() {
        val scenario = scenario()
        val replacementId = scenario.draftInSameExchange()
        dataSource.connection.use { connection -> share(connection, replacementId, scenario.manager, "REVIEWER") }
        val before = writesOf(scenario)

        assertTrue(permits(scenario.manager, Action.INFORMATION_REQUEST_VIEW, replacementId))
        assertFalse(permits(scenario.manager, Action.INFORMATION_REQUEST_SUPERSEDE, replacementId))
        assertThrows(ForbiddenException::class.java) {
            supersede(scenario, scenario.manager, replacementId, "supersede-view-only")
        }

        assertSourceUnchanged(scenario, before)
    }

    @Test
    fun `an unknown replacement and one in another Exchange are refused alike without saying whether they exist`() {
        val scenario = scenario()
        val elsewhere = dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection) }.requestId
        val before = writesOf(scenario)

        val unknown = assertThrows(ForbiddenException::class.java) {
            supersede(scenario, scenario.manager, UUID.randomUUID(), "supersede-unknown")
        }
        val foreign = assertThrows(ForbiddenException::class.java) {
            supersede(scenario, scenario.manager, elsewhere, "supersede-foreign")
        }

        assertEquals(unknown.message, foreign.message)
        assertSourceUnchanged(scenario, before)
    }

    @Test
    fun `a manager of both requests supersedes, replays the same command and refuses the key with another reason`() {
        val scenario = scenario()
        val replacementId = scenario.draftInSameExchange()
        dataSource.connection.use { connection -> share(connection, replacementId, scenario.manager, "DECISION_MAKER") }
        val etag = aggregateETag(scenario.sourceId)

        val superseded = supersede(scenario, scenario.manager, replacementId, "supersede-both", etag = etag)
        val replayed = supersede(scenario, scenario.manager, replacementId, "supersede-both", etag = etag)

        assertEquals(replacementId, superseded.request.supersededByRequestId)
        assertEquals(superseded.requestETag, replayed.requestETag)
        assertEquals("SUPERSEDED", state(scenario.sourceId))
        assertEquals(1, transitions(scenario.sourceId))
        assertThrows(CommandReceiptConflictException::class.java) {
            supersede(
                scenario, scenario.manager, replacementId, "supersede-both", etag = etag, reason = "duplicate_request",
            )
        }
        assertEquals(1, transitions(scenario.sourceId))
    }

    @Test
    fun `the Exchange owner supersedes through the authority every request of the Exchange inherits`() {
        val scenario = scenario()
        val replacementId = scenario.draftInSameExchange()

        val superseded = supersede(scenario, scenario.owner, replacementId, "supersede-by-owner")

        assertEquals(replacementId, superseded.request.supersededByRequestId)
        assertEquals("SUPERSEDED", state(scenario.sourceId))
    }

    @Test
    fun `a superseding follow-up made by a manager of the source alone still supersedes the source with it`() {
        val scenario = scenario()

        val created = QuarkusTransaction.requiringNew().call {
            scenario.services.successors.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = scenario.sourceId,
                    kind = InformationRequestLineageKind.SUPERSEDING,
                    reasonCode = REASON,
                    access = access(scenario.manager),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
                    idempotencyKey = "superseding-follow-up",
                ),
            )
        }

        assertEquals("SUPERSEDED", state(scenario.sourceId))
        assertEquals(created.successor.id.toString(), supersededBy(scenario.sourceId))
        assertTrue(permits(scenario.manager, Action.INFORMATION_REQUEST_SUPERSEDE, created.successor.id))
    }

    private fun supersede(
        scenario: SupersedeReplacementScenario,
        caller: PrincipalRef,
        replacementId: UUID,
        key: String,
        etag: String = aggregateETag(scenario.sourceId),
        reason: String = REASON,
    ) = QuarkusTransaction.requiringNew().call {
        scenario.services.lifecycle.supersede(
            SupersedeInformationRequestCommand(
                requestId = scenario.sourceId,
                supersededByRequestId = replacementId,
                reasonCode = reason,
                access = access(caller),
                precondition = CommandPrecondition.ExpectedRevision(etag),
                idempotencyKey = key,
            ),
        )
    }

    private fun assertSourceUnchanged(scenario: SupersedeReplacementScenario, before: SupersedeSourceWrites) {
        assertEquals("ISSUED", state(scenario.sourceId))
        assertNull(supersededBy(scenario.sourceId))
        assertEquals(0, transitions(scenario.sourceId))
        assertEquals(before, writesOf(scenario))
    }

    private fun scenario(): SupersedeReplacementScenario {
        val managerId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { fixture ->
                val managerPartyId = UUID.randomUUID()
                fixture.insertActingParty(managerPartyId, "DECISION_MAKER", managerId)
                val shareId = share(connection, fixture.requestId, PrincipalRef.user(managerId), "DECISION_MAKER")
                execute(
                    connection,
                    "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                    shareId,
                    managerPartyId
                )
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
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        return SupersedeReplacementScenario(fixture, services, PrincipalRef.user(managerId))
    }

    private fun SupersedeReplacementScenario.draftInSameExchange(): UUID {
        val draftId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                draftId,
                fixture.exchangeId,
                fixture.template.versionId,
                fixture.template.organizationId,
            )
        }
        return draftId
    }

    private fun share(connection: Connection, requestId: UUID, principal: PrincipalRef, role: String): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            principal.kind.name,
            principal.id,
            role,
        )
        return shareId
    }

    private fun permits(principal: PrincipalRef, action: Action, requestId: UUID): Boolean =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                action,
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(sessionRef = SESSION),
            ) is Decision.Allow
        }

    private fun state(requestId: UUID): String? = dataSource.connection.use { connection ->
        queryString(connection, "SELECT state FROM information_request WHERE id = ?", requestId)
    }

    private fun supersededBy(requestId: UUID): String? = dataSource.connection.use { connection ->
        queryString(
            connection,
            "SELECT superseded_by_request_id::text FROM information_request WHERE id = ?",
            requestId
        )
    }

    private fun transitions(requestId: UUID): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation = 'SUPERSEDE'",
            requestId,
        )
    }

    private fun writesOf(scenario: SupersedeReplacementScenario): SupersedeSourceWrites =
        dataSource.connection.use { connection ->
            SupersedeSourceWrites(
                aggregateRevision = queryInt(
                    connection,
                    "SELECT aggregate_revision FROM information_request WHERE id = ?",
                    scenario.sourceId,
                ),
                receipts = queryInt(
                    connection,
                    "SELECT count(*) FROM command_receipt WHERE actor_id = ? AND operation_name = 'supersede-information-request'",
                    scenario.manager.id,
                ),
            )
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = SESSION))

    private companion object {
        const val REASON = "requirements_changed"
        const val SESSION = "supersede-session"
    }
}
