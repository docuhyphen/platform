package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnershipChangePolicy
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestOwnershipChangeTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var configuredPolicy: RecordOwnershipChangePolicy

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Test
    fun `by default a removed member keeps the parties they hold in the organization's requests`()
    {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val service = service(configuredPolicy)

        val outcomes = QuarkusTransaction.requiringNew()
            .call { service.memberRemoved(fixture.template.organizationId, fixture.contributorUserId) }

        assertEquals(listOf(RecordOwnershipChangeDecision.RETAIN), outcomes.map { it.decision })
        assertTrue(
            QuarkusTransaction.requiringNew()
                .call { requireNotNull(partyRepository.findById(fixture.contributorPartyId)).active })
    }

    @Test
    fun `a revoking policy revokes only the removed member's parties in that organization's requests`()
    {
        lateinit var elsewhere: SubmissionRuntimeSqlFixture
        val borrowedPartyId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            val home = SubmissionRuntimeSqlFixture(connection)
            elsewhere = SubmissionRuntimeSqlFixture(connection)
            elsewhere.insertPartyForUser(borrowedPartyId, "CONTRIBUTOR", home.contributorUserId)
            home
        }
        val service = service(revokingPolicy())

        val outcomes = QuarkusTransaction.requiringNew()
            .call { service.memberRemoved(fixture.template.organizationId, fixture.contributorUserId) }

        assertEquals(listOf(fixture.contributorPartyId), outcomes.map { it.resourceId })
        QuarkusTransaction.requiringNew().run {
            assertFalse(requireNotNull(partyRepository.findById(fixture.contributorPartyId)).active)
            assertTrue(requireNotNull(partyRepository.findById(fixture.attestorPartyId)).active)
            assertTrue(requireNotNull(partyRepository.findById(borrowedPartyId)).active)
            assertEquals(2, requireNotNull(requestRepository.findById(fixture.requestId)).partyRevision)
        }
    }

    @Test
    fun `a revoking removal locks the Exchanges of the requests it revokes in identifier order`() {
        val (higher, lower) = memberOnTwoExchanges()
        val service = service(revokingPolicy())

        val outcomes = dataSource.connection.use { blocker ->
            blocker.autoCommit = false
            lockExchange(blocker, lower.exchangeId)
            val removal = CompletableFuture.supplyAsync {
                withRequestContext {
                    QuarkusTransaction.requiringNew()
                        .call { service.memberRemoved(higher.template.organizationId, higher.contributorUserId) }
                }
            }
            awaitLockWaiters(1)
            val higherFree = lockableNow(higher.exchangeId)
            blocker.rollback()
            blocker.autoCommit = true
            assertTrue(higherFree, "the removal held a later Exchange while it waited for an earlier one")
            removal.get(30, TimeUnit.SECONDS)
        }

        assertEquals(
            listOf(RecordOwnershipChangeDecision.REVOKE, RecordOwnershipChangeDecision.REVOKE),
            outcomes.map { it.decision },
        )
    }

    private fun service(policy: RecordOwnershipChangePolicy) =
        InformationRequestOwnershipChangeService(partyRepository, requestRepository, policy, partyRevocations, gate)

    private fun revokingPolicy(): RecordOwnershipChangePolicy =
        mock<RecordOwnershipChangePolicy>().also {
            whenever(it.decide(any())).thenReturn(RecordOwnershipChangeDecision.REVOKE)
        }

    private fun memberOnTwoExchanges(): Pair<SubmissionRuntimeSqlFixture, SubmissionRuntimeSqlFixture> {
        while (true) {
            val pair = dataSource.connection.use { connection ->
                val first = SubmissionRuntimeSqlFixture(connection)
                val second =
                    SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = first.template.organizationId)
                if (first.exchangeId > second.exchangeId) {
                    second.insertPartyForUser(UUID.randomUUID(), "CONTRIBUTOR", first.contributorUserId)
                    first to second
                } else null
            }
            if (pair != null) return pair
        }
    }

    private fun lockExchange(connection: java.sql.Connection, exchangeId: UUID) {
        connection.prepareStatement("SELECT id FROM exchange WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, exchangeId)
            statement.executeQuery().close()
        }
    }

    private fun lockableNow(exchangeId: UUID): Boolean =
        dataSource.connection.use { probe ->
            probe.autoCommit = false
            try {
                probe.prepareStatement("SELECT id FROM exchange WHERE id = ? FOR UPDATE NOWAIT").use { statement ->
                    statement.setObject(1, exchangeId)
                    statement.executeQuery().close()
                }
                true
            } catch (_: SQLException) {
                false
            } finally {
                probe.rollback()
                probe.autoCommit = true
            }
        }

    private fun awaitLockWaiters(expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting >= expected) return
            Thread.sleep(50)
        }
    }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }
}
