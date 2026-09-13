package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class InformationRequestParentTerminationPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestParentTerminationPostgreSQLContainer>(imageName)

private const val DEADLOCK_SQL_STATE = "40P01"

class InformationRequestParentTerminationConcurrencyPostgresContractTest
{
    @Test
    fun `taking the request row before its parent Exchange deadlocks against a concurrent termination`()
    {
        withPostgres("docuhyphen_request_parent_lock_inversion") { postgres, fixture ->
            val bothHoldFirstLock = CountDownLatch(2)
            val executor = Executors.newFixedThreadPool(2)
            val outcomes = try
            {
                listOf(
                    executor.submit(
                        Callable {
                            runSequence(postgres) { connection ->
                                lockRequest(connection, fixture.requestId)
                                bothHoldFirstLock.countDown()
                                bothHoldFirstLock.await(30, TimeUnit.SECONDS)
                                lockExchange(connection, fixture.exchangeId)
                            }
                        },
                    ),
                    executor.submit(
                        Callable {
                            runSequence(postgres) { connection ->
                                lockExchange(connection, fixture.exchangeId)
                                bothHoldFirstLock.countDown()
                                bothHoldFirstLock.await(30, TimeUnit.SECONDS)
                                lockRequest(connection, fixture.requestId)
                            }
                        },
                    ),
                ).map { it.get() }
            }
            finally
            {
                executor.shutdownNow()
            }

            assertEquals(1, outcomes.count { it == DEADLOCK_SQL_STATE })
        }
    }

    @Test
    fun `locking the parent Exchange first lets a termination and a request command serialize`()
    {
        withPostgres("docuhyphen_request_parent_lock_order") { postgres, fixture ->
            val terminationHoldsParent = CountDownLatch(1)
            val commandReachedParent = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val outcomes = try
            {
                val termination = executor.submit(
                    Callable {
                        runSequence(postgres) { connection ->
                            lockExchange(connection, fixture.exchangeId)
                            terminationHoldsParent.countDown()
                            commandReachedParent.await(30, TimeUnit.SECONDS)
                            terminateExchange(connection, fixture.exchangeId)
                            lockRequest(connection, fixture.requestId)
                            cancelRequest(connection, fixture.requestId)
                        }
                    },
                )
                val command = executor.submit(
                    Callable {
                        terminationHoldsParent.await(30, TimeUnit.SECONDS)
                        runSequence(postgres) { connection ->
                            commandReachedParent.countDown()
                            val parentStatus = lockExchange(connection, fixture.exchangeId)
                            if (parentStatus != "ACCEPTED_STARTED") throw ParentTerminatedException(parentStatus)
                            lockRequest(connection, fixture.requestId)
                            saveResponseRevision(connection, fixture.requestId)
                        }
                    },
                )
                listOf(termination.get(), command.get())
            }
            finally
            {
                executor.shutdownNow()
            }

            assertTrue(outcomes.none { it == DEADLOCK_SQL_STATE })
            assertEquals("RESCINDED", outcomes[1])
            postgres.createConnection("").use { connection ->
                assertEquals("CANCELLED", requestState(connection, fixture.requestId))
                assertEquals(1L, requestResponseRevision(connection, fixture.requestId))
            }
        }
    }

    private fun withPostgres(
        databaseName: String,
        body: (InformationRequestParentTerminationPostgreSQLContainer, Fixture) -> Unit,
    )
    {
        val postgres = InformationRequestParentTerminationPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName(databaseName)
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")
        postgres.start()
        try
        {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            val fixture = postgres.createConnection("").use { Fixture(it) }
            body(postgres, fixture)
        }
        finally
        {
            postgres.stop()
        }
    }

    private class ParentTerminatedException(val parentStatus: String) : RuntimeException(parentStatus)

    private fun runSequence(
        postgres: InformationRequestParentTerminationPostgreSQLContainer,
        sequence: (Connection) -> Unit,
    ): String =
        postgres.createConnection("").use { connection ->
            connection.autoCommit = false
            try
            {
                sequence(connection)
                connection.commit()
                "COMMITTED"
            }
            catch (exception: ParentTerminatedException)
            {
                connection.rollback()
                exception.parentStatus
            }
            catch (exception: SQLException)
            {
                connection.rollback()
                exception.sqlState ?: "SQL_ERROR"
            }
        }

    private fun lockExchange(connection: Connection, exchangeId: UUID): String =
        connection.prepareStatement("SELECT status FROM exchange WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, exchangeId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getString(1)
            }
        }

    private fun lockRequest(connection: Connection, requestId: UUID): String =
        connection.prepareStatement("SELECT state FROM information_request WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getString(1)
            }
        }

    private fun terminateExchange(connection: Connection, exchangeId: UUID)
    {
        connection.prepareStatement("UPDATE exchange SET status = 'RESCINDED' WHERE id = ?").use { statement ->
            statement.setObject(1, exchangeId)
            statement.executeUpdate()
        }
    }

    private fun cancelRequest(connection: Connection, requestId: UUID)
    {
        connection.prepareStatement(
            """
            UPDATE information_request
            SET state = 'CANCELLED', cancelled_at = ?, aggregate_revision = aggregate_revision + 1
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, requestId)
            statement.executeUpdate()
        }
    }

    private fun saveResponseRevision(connection: Connection, requestId: UUID)
    {
        connection.prepareStatement(
            "UPDATE information_request SET response_revision = response_revision + 1 WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, requestId)
            statement.executeUpdate()
        }
    }

    private fun requestState(connection: Connection, requestId: UUID): String =
        connection.prepareStatement("SELECT state FROM information_request WHERE id = ?").use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getString(1)
            }
        }

    private fun requestResponseRevision(connection: Connection, requestId: UUID): Long =
        connection.prepareStatement("SELECT response_revision FROM information_request WHERE id = ?").use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }

    private class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val versionId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertVersion(connection, versionId, definitionId, userId)
            insertRequest(connection, requestId, exchangeId, versionId, organizationId)
        }
    }

    private companion object
    {
        fun insertOrganization(connection: Connection, id: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO organization
                    (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Parent termination organization', ?, TRUE, TRUE, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setString(2, "REG-${id.toString().take(8)}")
                statement.setTimestamp(3, Timestamp.from(Instant.now()))
                statement.executeUpdate()
            }
        }

        fun insertUser(connection: Connection, id: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "parent-owner-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }

        fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO exchange
                    (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                     created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, organizationId)
                statement.setObject(3, userId)
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }

        fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_definition
                    (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                     status, created_at, updated_at)
                VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern',
                        'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, organizationId)
                statement.setString(3, "collection-${id.toString().take(8)}")
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }

        fun insertVersion(connection: Connection, id: UUID, definitionId: UUID, actorId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_version
                    (id, template_definition_id, version_number, status, created_at,
                     published_at, published_by_app_user_id)
                VALUES (?, ?, 1, 'PUBLISHED', ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, definitionId)
                statement.setTimestamp(3, now)
                statement.setTimestamp(4, now)
                statement.setObject(5, actorId)
                statement.executeUpdate()
            }
        }

        fun insertRequest(connection: Connection, id: UUID, exchangeId: UUID, versionId: UUID, ownerId: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                     state, gates_exchange_closure, aggregate_revision, party_revision,
                     issued_at, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'ISSUED', TRUE, 1, 1, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, id)
                statement.setObject(2, exchangeId)
                statement.setObject(3, versionId)
                statement.setObject(4, ownerId)
                statement.setTimestamp(5, now)
                statement.setTimestamp(6, now)
                statement.setTimestamp(7, now)
                statement.executeUpdate()
            }
        }
    }
}


