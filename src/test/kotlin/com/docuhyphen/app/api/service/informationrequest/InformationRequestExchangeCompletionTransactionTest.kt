package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestExchangeCompletionTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var exchangeRepository: ExchangeRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var completion: InformationRequestExchangeCompletionService

    private val system = PrincipalRef(com.docuhyphen.app.api.model.entity.PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))

    @Test
    fun `an open gating request refuses ending before anything is cancelled`()
    {
        val (runtimeFixture, nongatingId) = fixtureWithNongatingRequest()

        val refusal = assertThrows(InformationRequestExchangeCompletionException::class.java) {
            QuarkusTransaction.requiringNew().run { prepare(runtimeFixture, cancelRemaining = true) }
        }

        assertEquals(InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED, refusal.reasonCode)
        assertEquals(listOf(runtimeFixture.requestId), refusal.requestIds)
        assertEquals(InformationRequestState.ISSUED, stateOf(nongatingId))
    }

    @Test
    fun `a remaining nongating request refuses ending unless cancellation is stated, which cancels it in the same transaction`()
    {
        val (runtimeFixture, nongatingId) = fixtureWithNongatingRequest()
        close(runtimeFixture.requestId)

        val refusal = assertThrows(InformationRequestExchangeCompletionException::class.java) {
            QuarkusTransaction.requiringNew().run { prepare(runtimeFixture, cancelRemaining = false) }
        }
        assertEquals(InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION, refusal.reasonCode)
        assertEquals(listOf(nongatingId), refusal.requestIds)
        assertEquals(InformationRequestState.ISSUED, stateOf(nongatingId))

        QuarkusTransaction.requiringNew().run { prepare(runtimeFixture, cancelRemaining = true) }

        QuarkusTransaction.requiringNew().run {
            assertEquals(InformationRequestState.CANCELLED, requireNotNull(requestRepository.findById(nongatingId)).state)
            val cancellation = transitionRepository.findForRequest(nongatingId).single { it.mutation == InformationRequestMutation.CANCEL }
            assertEquals("EXCHANGE_ENDED", cancellation.reasonCode)
            assertEquals(InformationRequestState.ISSUED, cancellation.fromState)
        }
    }

    @Test
    fun `a gating request that already ended no longer holds the gate`()
    {
        val runtimeFixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                runtimeFixture.requestId,
            )
        }

        QuarkusTransaction.requiringNew().run { prepare(runtimeFixture, cancelRemaining = false) }
    }

    @Test
    fun `the completion gate changes once per key under its precondition and never on a finished request`()
    {
        val runtimeFixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val services = runtime.build(runtimeFixture.requestId)
        val gates = InformationRequestCompletionGateService(
            services.gate, requestRepository, runtime.commandReceiptService, runtime.transitionHistory, runtime.clock,
        )
        val owner = RequestAccessContext(PrincipalRef.user(runtimeFixture.template.userId), AuthorizationContext(sessionRef = "owner"))
        val etag = QuarkusTransaction.requiringNew().call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(runtimeFixture.requestId))) }
        val command = ChangeInformationRequestCompletionGateCommand(
            requestId = runtimeFixture.requestId,
            gatesExchangeClosure = false,
            access = owner,
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = "gate-off",
        )

        val changed = QuarkusTransaction.requiringNew().call { gates.change(command) }
        val replayed = QuarkusTransaction.requiringNew().call { gates.change(command) }

        assertFalse(changed.request.gatesExchangeClosure)
        assertEquals(changed.requestETag, replayed.requestETag)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                1,
                transitionRepository.findForRequest(runtimeFixture.requestId).count { it.mutation == InformationRequestMutation.CHANGE_COMPLETION_GATE },
            )
        }
        assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call { gates.change(command.copy(gatesExchangeClosure = true, idempotencyKey = "gate-on")) }
        }
        assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call {
                gates.change(command.copy(gatesExchangeClosure = true, idempotencyKey = "gate-unstated", precondition = CommandPrecondition.Absent))
            }
        }

        close(runtimeFixture.requestId)
        val closedETag = QuarkusTransaction.requiringNew().call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(runtimeFixture.requestId))) }
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                gates.change(command.copy(idempotencyKey = "gate-closed", precondition = CommandPrecondition.ExpectedRevision(closedETag)))
            }
        }
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, refusal.reasonCode)
    }

    private fun prepare(runtimeFixture: SubmissionRuntimeSqlFixture, cancelRemaining: Boolean)
    {
        val exchange = requireNotNull(exchangeRepository.findByIdForUpdate(runtimeFixture.exchangeId))
        completion.prepareEnding(exchange, system, cancelRemaining)
    }

    private fun fixtureWithNongatingRequest(): Pair<SubmissionRuntimeSqlFixture, UUID>
    {
        val nongatingId = UUID.randomUUID()
        val runtimeFixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { fixture ->
                val now = Timestamp.from(Instant.now())
                execute(
                    connection,
                    """
                    INSERT INTO information_request
                        (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                         gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
                    VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', FALSE, 1, 1, ?, ?, ?)
                    """.trimIndent(),
                    nongatingId,
                    fixture.exchangeId,
                    fixture.template.versionId,
                    fixture.template.organizationId,
                    now,
                    now,
                    now,
                )
            }
        }
        return runtimeFixture to nongatingId
    }

    private fun close(requestId: UUID)
    {
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CLOSED', closed_at = now(), aggregate_revision = aggregate_revision + 1 WHERE id = ?", requestId)
        }
    }

    private fun stateOf(requestId: UUID): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(requestId)).state }
}
