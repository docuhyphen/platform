package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.SupersedeInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.command.model.OverlappingCommandOutcome
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestLifecycleConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    private val race by lazy { OverlappingCommandRace(dataSource) }

    @Test
    fun `overlapping retries of one issue command both return the recorded issuance`() {
        val (fixture, draftId) = draft()
        val services = runtime.build(draftId)
        val command = IssueInformationRequestCommand(
            requestId = draftId,
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(draftId)),
            idempotencyKey = "issue-${UUID.randomUUID()}",
        )

        val outcome =
            race.whileRowLocked("exchange", fixture.exchangeId, List(2) { { services.lifecycle.issue(command) } })

        assertReplayed(outcome)
        assertEquals(1, transitions(draftId, "ISSUE"))
        assertEquals(2, aggregateRevision(draftId))
    }

    @Test
    fun `overlapping retries of one cancel command both return the recorded cancellation`() {
        val fixture = issued()
        val services = runtime.build(fixture.requestId)
        val command = cancel(fixture, "cancel-${UUID.randomUUID()}", "author_cancelled")

        val outcome =
            race.whileRowLocked("exchange", fixture.exchangeId, List(2) { { services.lifecycle.cancel(command) } })

        assertReplayed(outcome)
        assertEquals(1, transitions(fixture.requestId, "CANCEL"))
        assertEquals(2, aggregateRevision(fixture.requestId))
    }

    @Test
    fun `overlapping retries of one supersede command both return the recorded supersession`() {
        val fixture = issued()
        val replacementId = insertDraft(fixture)
        val services = runtime.build(fixture.requestId)
        val command = SupersedeInformationRequestCommand(
            requestId = fixture.requestId,
            supersededByRequestId = replacementId,
            reasonCode = "replacement_created",
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
            idempotencyKey = "supersede-${UUID.randomUUID()}",
        )

        val outcome =
            race.whileRowLocked("exchange", fixture.exchangeId, List(2) { { services.lifecycle.supersede(command) } })

        assertReplayed(outcome)
        assertEquals(1, transitions(fixture.requestId, "SUPERSEDE"))
        assertEquals(2, aggregateRevision(fixture.requestId))
        QuarkusTransaction.requiringNew().run {
            assertEquals(replacementId, requestRepository.findById(fixture.requestId)?.supersededByRequestId)
        }
    }

    @Test
    fun `overlapping cancellations under different keys commit once and refuse the other as stale`() {
        val fixture = issued()
        val services = runtime.build(fixture.requestId)
        val etag = aggregateETag(fixture.requestId)
        val attempts = listOf("cancel-first", "cancel-second").map { key ->
            { services.lifecycle.cancel(cancel(fixture, "$key-${UUID.randomUUID()}", "author_cancelled", etag)) }
        }

        val outcome = race.whileRowLocked("exchange", fixture.exchangeId, attempts)

        assertTrue(outcome.overlapped, "both cancellations wait for the parent Exchange")
        assertEquals(1, outcome.results.count { it.isSuccess }, outcome.results.toString())
        val refusal = outcome.results.single { it.isFailure }.exceptionOrNull()
        assertTrue(refusal is CommandPreconditionException, refusal.toString())
        assertEquals(CommandPreconditionException.Kind.STALE, (refusal as CommandPreconditionException).kind)
        assertEquals(1, transitions(fixture.requestId, "CANCEL"))
    }

    @Test
    fun `an overlapping reuse of a cancel key with another reason is refused as a conflict`() {
        val fixture = issued()
        val services = runtime.build(fixture.requestId)
        val key = "cancel-${UUID.randomUUID()}"
        val etag = aggregateETag(fixture.requestId)
        val attempts = listOf("author_cancelled", "duplicate_request").map { reason ->
            { services.lifecycle.cancel(cancel(fixture, key, reason, etag)) }
        }

        val outcome = race.whileRowLocked("exchange", fixture.exchangeId, attempts)

        assertTrue(outcome.overlapped, "both cancellations wait for the parent Exchange")
        assertEquals(1, outcome.results.count { it.isSuccess }, outcome.results.toString())
        val refusal = outcome.results.single { it.isFailure }.exceptionOrNull()
        assertTrue(refusal is CommandReceiptConflictException, refusal.toString())
        assertEquals(1, transitions(fixture.requestId, "CANCEL"))
    }

    private fun assertReplayed(outcome: OverlappingCommandOutcome<InformationRequestLifecycleResult>) {
        assertTrue(outcome.overlapped, "both retries wait for the parent Exchange")
        assertTrue(outcome.results.all { it.isSuccess }, outcome.results.toString())
        assertEquals(1, outcome.results.map { it.getOrThrow().requestETag }.toSet().size, outcome.results.toString())
    }

    private fun cancel(
        fixture: SubmissionRuntimeSqlFixture,
        key: String,
        reasonCode: String,
        etag: String = aggregateETag(fixture.requestId),
    ) = CancelInformationRequestCommand(
        requestId = fixture.requestId,
        reasonCode = reasonCode,
        access = owner(fixture),
        precondition = CommandPrecondition.ExpectedRevision(etag),
        idempotencyKey = key,
    )

    private fun owner(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.template.userId),
            AuthorizationContext(sessionRef = "owner-session")
        )

    private fun issued(): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection) }

    private fun draft(): Pair<SubmissionRuntimeSqlFixture, UUID> {
        val fixture = issued()
        return fixture to insertDraft(fixture)
    }

    private fun insertDraft(fixture: SubmissionRuntimeSqlFixture): UUID {
        val draftId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
                """.trimIndent(),
                draftId,
                fixture.exchangeId,
                fixture.template.versionId,
                fixture.template.organizationId,
                fixture.template.now,
                fixture.template.now,
            )
        }
        return draftId
    }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId)))
        }

    private fun transitions(requestId: UUID, mutation: String): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation = ?",
                requestId,
                mutation,
            )
        }

    private fun aggregateRevision(requestId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(connection, "SELECT aggregate_revision FROM information_request WHERE id = ?", requestId)
        }
}
