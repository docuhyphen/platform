package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.*

private class DomainEventOutboxPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DomainEventOutboxPostgreSQLContainer>(imageName)

class DomainEventOutboxOwnerContractTest
{
    private val releasedVersion = "93"

    @Test
    fun `released platform and organization events gain explicit owners`()
    {
        withPostgres { postgres ->
            flyway(postgres, releasedVersion).migrate()
            val organizationId = UUID.randomUUID()
            val platformEventId = UUID.randomUUID()
            val organizationEventId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertLegacyEvent(connection, platformEventId, null)
                insertLegacyEvent(connection, organizationEventId, organizationId)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("PLATFORM", ownerKind(connection, platformEventId))
                assertNull(ownerId(connection, platformEventId))
                assertEquals("ORGANIZATION", ownerKind(connection, organizationEventId))
                assertEquals(organizationId, ownerId(connection, organizationEventId))
            }
        }
    }

    @Test
    fun `an event that does not state its owner is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val refusal = assertThrows<SQLException> {
                    insertUnownedEvent(connection, UUID.randomUUID())
                }
                assertTrue(refusal.message.orEmpty().contains("owner_kind"), refusal.message)
            }
        }
    }

    @Test
    fun `domain event rows name exactly one owner and keep no separate organization column`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            val ownerUserId = UUID.randomUUID()
            val organizationId = UUID.randomUUID()
            val personalEventId = UUID.randomUUID()
            val organizationEventId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertOwnedEvent(connection, personalEventId, "USER", ownerUserId)
                insertOwnedEvent(connection, organizationEventId, "ORGANIZATION", organizationId)
                assertEquals("USER", ownerKind(connection, personalEventId))
                assertEquals(ownerUserId, ownerId(connection, personalEventId))
                assertEquals(organizationId, ownerId(connection, organizationEventId))
                assertFalse(hasOrganizationColumn(connection))

                refused(connection) {
                    insertOwnedEvent(connection, UUID.randomUUID(), "USER", null)
                }
                refused(connection) {
                    insertOwnedEvent(connection, UUID.randomUUID(), "ORGANIZATION", null)
                }
                refused(connection) {
                    insertOwnedEvent(connection, UUID.randomUUID(), "PLATFORM", ownerUserId)
                }
            }
        }
    }

    private fun withPostgres(block: (DomainEventOutboxPostgreSQLContainer) -> Unit)
    {
        val postgres = DomainEventOutboxPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_domain_event_outbox_test")
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

    private fun flyway(postgres: DomainEventOutboxPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertLegacyEvent(connection: Connection, eventId: UUID, organizationId: UUID?)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO workflow_event_outbox
                (id, event_id, idempotency_key, event_type, organization_id, envelope_json,
                 status, attempt_count, created_at, next_attempt_at)
            VALUES (?, ?, ?, 'workflow.test', ?, '{}', 'PENDING', 0, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, eventId)
            statement.setString(3, "workflow:$eventId")
            statement.setObject(4, organizationId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertUnownedEvent(connection: Connection, eventId: UUID) {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO workflow_event_outbox
                (id, event_id, idempotency_key, event_type, envelope_json, status, attempt_count, created_at, next_attempt_at)
            VALUES (?, ?, ?, 'workflow.test', '{}', 'PENDING', 0, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, eventId)
            statement.setString(3, "workflow:$eventId")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertOwnedEvent(connection: Connection, eventId: UUID, ownerKind: String, ownerId: UUID?)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO workflow_event_outbox
                (id, event_id, idempotency_key, event_type, owner_kind, owner_id,
                 envelope_json, status, attempt_count, created_at, next_attempt_at)
            VALUES (?, ?, ?, 'information_request.request.create', ?, ?, '{}', 'PENDING', 0, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, eventId)
            statement.setString(3, "information_request:$eventId")
            statement.setString(4, ownerKind)
            statement.setObject(5, ownerId)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, now)
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
        assertTrue(refusal.message.orEmpty().contains("ck_workflow_event_outbox_owner"), refusal.message)
    }

    private fun ownerKind(connection: Connection, eventId: UUID): String? =
        value(connection, "owner_kind", eventId) as? String

    private fun ownerId(connection: Connection, eventId: UUID): UUID? =
        value(connection, "owner_id", eventId) as? UUID

    private fun hasOrganizationColumn(connection: Connection): Boolean =
        connection.metaData.getColumns(null, null, "workflow_event_outbox", "organization_id").use { it.next() }

    private fun value(connection: Connection, column: String, eventId: UUID): Any?
    {
        connection.prepareStatement("SELECT $column FROM workflow_event_outbox WHERE event_id = ?").use { statement ->
            statement.setObject(1, eventId)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                return rows.getObject(1)
            }
        }
    }
}
