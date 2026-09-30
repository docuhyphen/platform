package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionGrantRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestExchangeSummaryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoticeStateReader
import com.docuhyphen.app.api.service.informationrequest.InformationRequestOperationsService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestVolumeTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var grantRepository: RequestExecutionGrantRepository
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var eventRepository: InformationRequestClockEventRepository
    @Inject lateinit var policies: InformationRequestClockPolicyService
    @Inject lateinit var noticeStates: InformationRequestNoticeStateReader
    @Inject lateinit var deliveries: DomainEventDeliveryStandingService
    @Inject lateinit var titleReader: InformationRequestTitleReader
    @Inject lateinit var partyRepository: InformationRequestPartyRepository
    @Inject lateinit var principalDisplayService: PrincipalDisplayService
    @Inject lateinit var listings: InformationRequestExchangeSummaryService

    @Test
    fun `the operations queue and quota counts stay prompt for five hundred requests and an Exchange lists twenty-five`()
    {
        val runtime = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val organizationId = runtime.template.organizationId
        val listedExchangeId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertRequests(connection, runtime, runtime.exchangeId, REQUESTS - LISTED)
            insertExchange(connection, runtime, listedExchangeId)
            insertRequests(connection, runtime, listedExchangeId, LISTED)
        }
        val operations = operations(organizationId, runtime.template.userId)

        val (page, queueTime) = timed {
            QuarkusTransaction.requiringNew().call { operations.queue(InformationRequestOperationsFilter(limit = 25, offset = 0)) }
        }
        val (last, lastPageTime) = timed {
            QuarkusTransaction.requiringNew().call { operations.queue(InformationRequestOperationsFilter(limit = 25, offset = REQUESTS)) }
        }
        val (listing, listingTime) = timed {
            QuarkusTransaction.requiringNew().call {
                listings.listForExchange(
                    listedExchangeId,
                    RequestAccessContext(PrincipalRef.user(runtime.template.userId), AuthorizationContext(activeOrgId = organizationId)),
                )
            }
        }
        val (open, countTime) = timed {
            QuarkusTransaction.requiringNew().call {
                requestRepository.countOpenForOwner(InformationRequestOwnerType.ORGANIZATION, organizationId) to
                    grantRepository.committedEvidenceBytes(SubscriptionOwnerType.ORGANIZATION, organizationId)
            }
        }

        println(
            "INFORMATION_REQUEST_VOLUME requests=${REQUESTS + 1} queueMs=${queueTime.toMillis()} " +
                "lastPageMs=${lastPageTime.toMillis()} listingMs=${listingTime.toMillis()} " +
                "listed=${listing.requests.size} countsMs=${countTime.toMillis()}",
        )
        assertEquals(25, page.rows.size)
        assertEquals(REQUESTS + 1L, page.total.toLong())
        assertEquals(1, last.rows.size)
        assertEquals(REQUESTS + 1L, open.first)
        assertEquals(LISTED, listing.requests.size)
        assertTrue(queueTime < BOUND, "queue took $queueTime")
        assertTrue(lastPageTime < BOUND, "last page took $lastPageTime")
        assertTrue(listingTime < BOUND, "listing took $listingTime")
        assertTrue(countTime < BOUND, "quota counts took $countTime")
    }

    private fun <T> timed(block: () -> T): Pair<T, Duration>
    {
        val started = System.nanoTime()
        val result = block()
        return result to Duration.ofNanos(System.nanoTime() - started)
    }

    private fun insertExchange(connection: java.sql.Connection, runtime: SubmissionRuntimeSqlFixture, exchangeId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process records', 'Please respond',
                    'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
            exchangeId,
            runtime.template.organizationId,
            runtime.template.userId,
        )
        execute(
            connection,
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            exchangeId,
            runtime.template.userId,
        )
    }

    private fun insertRequests(connection: java.sql.Connection, runtime: SubmissionRuntimeSqlFixture, exchangeId: UUID, count: Int)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', FALSE, 1, 1, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            repeat(count) { index ->
                val at = Timestamp.from(Instant.now().minusSeconds(index.toLong()))
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, exchangeId)
                statement.setObject(3, runtime.template.versionId)
                statement.setObject(4, runtime.template.organizationId)
                statement.setTimestamp(5, at)
                statement.setTimestamp(6, at)
                statement.setTimestamp(7, at)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun operations(organizationId: UUID, userId: UUID): InformationRequestOperationsService
    {
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, organizationId)
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)).thenReturn(PrincipalRef.user(userId))
        return InformationRequestOperationsService(
            access, requestRepository, clockRepository, eventRepository, policies, noticeStates, deliveries, Clock.systemUTC(),
            titleReader,
            partyRepository,
            principalDisplayService,
        )
    }

    private companion object
    {
        const val REQUESTS = 500
        const val LISTED = 25
        val BOUND: Duration = Duration.ofSeconds(5)
    }
}
