package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.RecordPreservationHoldStatus
import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.recordpreservation.model.PlacedHold
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RecordPreservationConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var administration: RecordPreservationAdministration

    private val ownerId = UUID.randomUUID()
    private val owner = RecordOwnerRef.user(ownerId)

    @BeforeEach
    fun currentOwner() {
        val access = mock<RecordOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(any(), any())).thenReturn(PrincipalRef.user(ownerId))
        QuarkusMock.installMockForType(access, RecordOwnerScopeAccess::class.java)
        QuarkusMock.installMockForType(
            mock<RecordPreservationEntitlementGuard>(),
            RecordPreservationEntitlementGuard::class.java
        )
    }

    @Test
    fun `two changes racing on one read version of a hold let the first apply and refuse the second as stale`() {
        val placed = place("place-for-stale-race")
        val read = CommandPrecondition.ExpectedRevision(placed.etag)

        val (first, second) = race(
            {
                administration.changeHoldScope(
                    placed.id,
                    RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    "widened",
                    read,
                    "stale-scope"
                )
            },
            { administration.releaseHold(placed.id, "review closed", read, "stale-release") },
        )

        first.getOrThrow()
        val refusal = second.exceptionOrNull()
        assertTrue(refusal is CommandPreconditionException, refusal.toString())
        assertEquals(CommandPreconditionException.Kind.STALE, (refusal as CommandPreconditionException).kind)
        val hold = QuarkusTransaction.requiringNew().call { administration.hold(placed.id).hold }
        assertEquals(RecordPreservationHoldStatus.ACTIVE, hold.status)
        assertEquals(RecordPreservationScope.DESCENDANTS_AND_REFERENCES, hold.scope)
        assertEquals(2L, hold.holdRevision)
    }

    @Test
    fun `a retried scope change racing with its original replays it instead of being refused`() {
        val placed = place("place-for-retry-race")
        val read = CommandPrecondition.ExpectedRevision(placed.etag)
        val change = {
            administration.changeHoldScope(
                placed.id,
                RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                "widened",
                read,
                "retried-scope"
            )
        }

        val (first, second) = race(change, change)

        assertEquals(2L, first.getOrThrow().hold.holdRevision)
        assertEquals(2L, second.getOrThrow().hold.holdRevision)
        assertEquals(
            1,
            rows(
                "SELECT count(*) FROM audit_legal_hold_event WHERE hold_id = ? AND event_kind = 'SCOPE_CHANGED'",
                placed.id
            )
        )
    }

    @Test
    fun `a retried hold placement racing with its original places one hold and replays it`() {
        val placement = {
            administration.placeHold(
                "APP_USER",
                ownerId.toString(),
                RecordPreservationScope.RESOURCE,
                "records kept for a review",
                null,
                null,
                "retried-placement",
            )
        }

        val (first, second) = race(placement, placement)

        assertEquals(first.getOrThrow().hold.id, second.getOrThrow().hold.id)
        assertEquals(
            1,
            rows("SELECT count(*) FROM audit_legal_hold WHERE owner_kind = 'USER' AND owner_id = ?", ownerId)
        )
    }

    @Test
    fun `two schedule publications racing on one read version publish one version and refuse the other as stale`() {
        val read = CommandPrecondition.ExpectedRevision(
            QuarkusTransaction.requiringNew().call { administration.schedule(RESOURCE_TYPE).etag },
        )

        val (first, second) = race(
            { administration.publishSchedule(RESOURCE_TYPE, 30, 90, read, "publish-first") },
            { administration.publishSchedule(RESOURCE_TYPE, 60, 120, read, "publish-second") },
        )

        assertEquals(1, first.getOrThrow().current?.versionNumber)
        val refusal = second.exceptionOrNull()
        assertTrue(refusal is CommandPreconditionException, refusal.toString())
        assertEquals(CommandPreconditionException.Kind.STALE, (refusal as CommandPreconditionException).kind)
        assertEquals(
            1,
            rows(
                "SELECT count(*) FROM record_retention_schedule WHERE owner_kind = 'USER' AND owner_id = ?",
                ownerId,
            ),
        )
    }

    private fun place(key: String): PlacedHold {
        val view = QuarkusTransaction.requiringNew().call {
            administration.placeHold(
                "APP_USER",
                ownerId.toString(),
                RecordPreservationScope.RESOURCE,
                "records kept for a review",
                null,
                null,
                key,
            )
        }
        return PlacedHold(view.hold.id, RecordPreservationETag.holdOf(view.hold))
    }

    private fun <T> race(first: () -> T, second: () -> T): Pair<Result<T>, Result<T>> {
        val applied = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstOutcome = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().call {
                        val result = first()
                        applied.countDown()
                        release.await(30, TimeUnit.SECONDS)
                        result
                    }
                }
            }.also { applied.countDown() }
        }
        while (!applied.await(100, TimeUnit.MILLISECONDS)) {
            if (firstOutcome.isDone) break
        }
        val secondOutcome = CompletableFuture.supplyAsync {
            runCatching { withRequestContext { QuarkusTransaction.requiringNew().call { second() } } }
        }
        awaitLockWaiterOrCompletion(secondOutcome)
        assertTrue(!secondOutcome.isDone, "the second command waits for the first one's transaction")
        release.countDown()
        return firstOutcome.get(30, TimeUnit.SECONDS) to secondOutcome.get(30, TimeUnit.SECONDS)
    }

    private fun awaitLockWaiterOrCompletion(pending: CompletableFuture<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pending.isDone && System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting > 0) return
            Thread.onSpinWait()
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

    private fun rows(sql: String, id: UUID): Int =
        dataSource.connection.use { connection -> queryInt(connection, sql, id) }

    private companion object {
        const val RESOURCE_TYPE = "INFORMATION_REQUEST"
    }
}
