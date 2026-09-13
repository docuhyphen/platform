package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class CommandReceiptPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<CommandReceiptPostgreSQLContainer>(imageName)

class CommandReceiptContractTest
{
    @Test
    fun `a command receipt is unique inside resource operation actor and idempotency key scope`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            val resourceId = UUID.randomUUID()
            val actorId = UUID.randomUUID()
            val key = "issue-key"

            postgres.createConnection("").use { connection ->
                insertReceipt(connection, resourceId, "issue", "USER", actorId, key, "first")

                refused(connection) {
                    insertReceipt(connection, resourceId, "issue", "USER", actorId, key, "first")
                }

                insertReceipt(connection, UUID.randomUUID(), "issue", "USER", actorId, key, "first")
                insertReceipt(connection, resourceId, "cancel", "USER", actorId, key, "first")
                insertReceipt(connection, resourceId, "issue", "ACCESS_SESSION", UUID.randomUUID(), key, "first")
            }
        }
    }

    private fun withPostgres(block: (CommandReceiptPostgreSQLContainer) -> Unit)
    {
        val postgres = CommandReceiptPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_command_receipt_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")
        postgres.start()
        try
        {
            block(postgres)
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: CommandReceiptPostgreSQLContainer): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()

    private fun insertReceipt(
        connection: Connection,
        resourceId: UUID,
        operationName: String,
        actorKind: String,
        actorId: UUID,
        idempotencyKey: String,
        fingerprint: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO command_receipt
                (id, resource_type, resource_id, operation_name, actor_kind, actor_id,
                 idempotency_key, request_fingerprint_sha256, result_resource_type,
                 result_resource_id, result_revision, result_etag, created_at, completed_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, ?, ?, ?, ?, 'INFORMATION_REQUEST', ?, 1, '"v1"', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, resourceId)
            statement.setString(3, operationName)
            statement.setString(4, actorKind)
            statement.setObject(5, actorId)
            statement.setString(6, idempotencyKey)
            statement.setString(7, fingerprint)
            statement.setObject(8, UUID.randomUUID())
            statement.setTimestamp(9, now)
            statement.setTimestamp(10, now)
            statement.executeUpdate()
        }
    }

    private fun refused(connection: Connection, block: () -> Unit)
    {
        connection.autoCommit = false
        val refusal = try
        {
            assertThrows<SQLException>(block)
        }
        finally
        {
            connection.rollback()
            connection.autoCommit = true
        }
        assertTrue(refusal.message.orEmpty().contains("uq_command_receipt_scope_key"), refusal.message)
    }
}
