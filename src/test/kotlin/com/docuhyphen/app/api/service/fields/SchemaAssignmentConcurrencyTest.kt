package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.fields.PublishedSchemaAssignmentCommand
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class SchemaAssignmentConcurrencyTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var schemaAssignmentService: SchemaAssignmentService

    @Test
    fun `two assignments racing for one resource leave one assignment and refuse the other as a conflict`() {
        lateinit var answers: FieldAnswerSqlFixture
        val runtime = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template)
            })
        }
        val requestId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                requestId,
                runtime.exchangeId,
                runtime.template.versionId,
                runtime.template.organizationId,
            )
        }
        val command = PublishedSchemaAssignmentCommand(
            resource = FieldsResourceRef("INFORMATION_REQUEST", requestId),
            access = FieldsAccessContext(PrincipalRef.user(runtime.template.userId), AuthorizationContext()),
            schemaVersionId = answers.schemaVersionId,
        )
        val firstAssigned = CountDownLatch(1)
        val firstMayCommit = CountDownLatch(1)

        val first = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.begin()
                    try {
                        schemaAssignmentService.assignSchemaVersionForCreation(command)
                        firstAssigned.countDown()
                        firstMayCommit.await(30, TimeUnit.SECONDS)
                        QuarkusTransaction.commit()
                    } catch (failure: Throwable) {
                        runCatching { QuarkusTransaction.rollback() }
                        throw failure
                    }
                }
            }.also { firstAssigned.countDown() }
        }
        assertTrue(firstAssigned.await(30, TimeUnit.SECONDS), "The first assignment never ran")
        val second = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        schemaAssignmentService.assignSchemaVersionForCreation(command)
                    }
                }
            }
        }
        awaitLockWaiterOrCompletion(second)
        firstMayCommit.countDown()

        val firstResult = first.get(90, TimeUnit.SECONDS)
        val secondResult = second.get(90, TimeUnit.SECONDS)

        assertTrue(firstResult.isSuccess, "The first assignment stands: ${firstResult.exceptionOrNull()}")
        val refusal = secondResult.exceptionOrNull()
        assertTrue(
            generateSequence(refusal) { it.cause }.any { it is SchemaAssignmentConflictException },
            "The second assignment is refused as a conflict rather than failing in the database: $refusal",
        )
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT COUNT(*) FROM schema_assignment WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?",
                    requestId,
                ),
            )
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
}
