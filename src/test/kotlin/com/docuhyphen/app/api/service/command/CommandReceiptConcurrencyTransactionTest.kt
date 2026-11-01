package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.model.CommandReceiptOverlap
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class CommandReceiptConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var receipts: CommandReceiptService

    private val race by lazy { OverlappingCommandRace(dataSource) }

    @Test
    fun `an overlapping retry waits for the first attempt and replays its recorded result`() {
        val command = receiptRequest(UUID.randomUUID(), "retry-${UUID.randomUUID()}", "same-body")

        val overlap = overlap(command, command)

        assertFalse(overlap.secondFinishedFirst, "the retry waits for the attempt holding its key")
        val first = overlap.first.getOrThrow()
        val second = overlap.second.getOrThrow()
        assertTrue(first is CommandReceiptDecision.Recorded, first.toString())
        assertTrue(second is CommandReceiptDecision.Replayed, second.toString())
        assertEquals(
            (first as CommandReceiptDecision.Recorded).receipt.id,
            (second as CommandReceiptDecision.Replayed).receipt.id
        )
        assertEquals(overlap.firstResult, second.result)
        assertEquals(1, overlap.mutations)
        assertEquals(1, storedReceipts(command))
    }

    @Test
    fun `an overlapping reuse of a key with another body is refused as a conflict after the first records`() {
        val resourceId = UUID.randomUUID()
        val key = "reuse-${UUID.randomUUID()}"

        val overlap =
            overlap(receiptRequest(resourceId, key, "first-body"), receiptRequest(resourceId, key, "second-body"))

        assertFalse(overlap.secondFinishedFirst, "the reuse waits for the attempt holding its key")
        assertTrue(overlap.first.getOrThrow() is CommandReceiptDecision.Recorded)
        assertTrue(overlap.second.exceptionOrNull() is CommandReceiptConflictException, overlap.second.toString())
        assertEquals(1, overlap.mutations)
    }

    @Test
    fun `a command under another key does not wait for a key held by an unfinished attempt`() {
        val resourceId = UUID.randomUUID()

        val overlap = overlap(
            receiptRequest(resourceId, "held-${UUID.randomUUID()}", "same-body"),
            receiptRequest(resourceId, "other-${UUID.randomUUID()}", "same-body"),
        )

        assertTrue(overlap.secondFinishedFirst, "another key is not serialized behind the held key")
        assertTrue(overlap.first.getOrThrow() is CommandReceiptDecision.Recorded)
        assertTrue(overlap.second.getOrThrow() is CommandReceiptDecision.Recorded)
        assertEquals(2, overlap.mutations)
    }

    @Test
    fun `a retry waiting behind an attempt that rolls back runs the command itself`() {
        val command = receiptRequest(UUID.randomUUID(), "rolled-back-${UUID.randomUUID()}", "same-body")

        val overlap = overlap(command, command, firstFails = true)

        assertTrue(overlap.first.exceptionOrNull() is IllegalStateException, overlap.first.toString())
        assertTrue(overlap.second.getOrThrow() is CommandReceiptDecision.Recorded, overlap.second.toString())
        assertEquals(2, overlap.mutations)
        assertEquals(1, storedReceipts(command))
    }

    private fun overlap(
        firstCommand: CommandReceiptRequest,
        secondCommand: CommandReceiptRequest,
        firstFails: Boolean = false,
    ): CommandReceiptOverlap {
        val mutations = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstResult = result(firstCommand)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = CompletableFuture.supplyAsync({
                runCatching {
                    race.inNewTransaction {
                        receipts.runOnce(firstCommand) {
                            mutations.incrementAndGet()
                            entered.countDown()
                            check(release.await(30, TimeUnit.SECONDS)) { "The first attempt was never released" }
                            check(!firstFails) { "The first attempt failed after taking its key" }
                            CommandMutationResult("first", firstResult)
                        }
                    }
                }.also { entered.countDown() }
            }, pool)
            assertTrue(entered.await(30, TimeUnit.SECONDS))
            val second = CompletableFuture.supplyAsync({
                runCatching {
                    race.inNewTransaction {
                        receipts.runOnce(secondCommand) {
                            mutations.incrementAndGet()
                            CommandMutationResult("second", result(secondCommand))
                        }
                    }
                }
            }, pool)
            race.awaitLockWaiters(1, listOf(second))
            val secondFinishedFirst = second.isDone
            release.countDown()
            return CommandReceiptOverlap(
                first.get(60, TimeUnit.SECONDS),
                second.get(60, TimeUnit.SECONDS),
                firstResult,
                secondFinishedFirst,
                mutations.get(),
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    private fun receiptRequest(resourceId: UUID, key: String, body: String) = CommandReceiptRequest(
        resource = ResourceRef(ResourceType.INFORMATION_REQUEST, resourceId),
        operation = "overlap-check",
        actor = CommandActorRef.principal(PrincipalRef.user(ACTOR_ID)),
        idempotencyKey = key,
        requestFingerprint = CommandRequestFingerprint.sha256Hex(body),
    )

    private fun result(command: CommandReceiptRequest) = CommandResultReference(
        resourceType = ResourceType.INFORMATION_REQUEST,
        resourceId = command.resource.id,
        revision = 2,
        etag = "\"${command.idempotencyKey}:${command.requestFingerprint.take(8)}\"",
    )

    private fun storedReceipts(command: CommandReceiptRequest): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM command_receipt WHERE resource_id = ? AND idempotency_key = ?",
                command.resource.id,
                command.idempotencyKey,
            )
        }

    private companion object {
        val ACTOR_ID: UUID = UUID.randomUUID()
    }
}
