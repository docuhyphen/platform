package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class AuditOwnerPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<AuditOwnerPostgreSQLContainer>(imageName)

class AuditPersonalOwnerContractTest
{
    private val releasedVersion = "83"

    @Test
    fun `released platform and organization audit events gain explicit owners`()
    {
        withPostgres { postgres ->
            flyway(postgres, releasedVersion).migrate()
            val organizationId = UUID.randomUUID()
            val platformEventId = UUID.randomUUID()
            val organizationEventId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertOutbox(connection, platformEventId, null)
                insertOutbox(connection, organizationEventId, organizationId)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("PLATFORM", ownerType(connection, "audit_outbox", platformEventId))
                assertNull(ownerId(connection, "audit_outbox", platformEventId))
                assertEquals("ORGANIZATION", ownerType(connection, "audit_outbox", organizationEventId))
                assertEquals(organizationId, ownerId(connection, "audit_outbox", organizationEventId))
            }
        }
    }

    @Test
    fun `legacy writers gain explicit owners during a rolling deployment`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            val organizationId = UUID.randomUUID()
            val platformEventId = UUID.randomUUID()
            val organizationEventId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertOutbox(connection, platformEventId, null)
                insertOutbox(connection, organizationEventId, organizationId)

                assertEquals("PLATFORM", ownerType(connection, "audit_outbox", platformEventId))
                assertNull(ownerId(connection, "audit_outbox", platformEventId))
                assertEquals("ORGANIZATION", ownerType(connection, "audit_outbox", organizationEventId))
                assertEquals(organizationId, ownerId(connection, "audit_outbox", organizationEventId))
            }
        }
    }

    @Test
    fun `personal audit rows name exactly one user owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            val ownerUserId = UUID.randomUUID()
            val eventId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertOwnedOutbox(connection, eventId, "USER", ownerUserId, null)
                assertEquals("USER", ownerType(connection, "audit_outbox", eventId))
                assertEquals(ownerUserId, ownerId(connection, "audit_outbox", eventId))
                assertNull(organizationId(connection, "audit_outbox", eventId))

                refused(connection, "ck_audit_outbox_owner") {
                    insertOwnedOutbox(connection, UUID.randomUUID(), "USER", null, null)
                }
                refused(connection, "ck_audit_outbox_owner") {
                    insertOwnedOutbox(connection, UUID.randomUUID(), "USER", ownerUserId, UUID.randomUUID())
                }
                refused(connection, "ck_audit_outbox_owner") {
                    insertOwnedOutbox(connection, UUID.randomUUID(), "PLATFORM", ownerUserId, null)
                }
            }
        }
    }

    @Test
    fun `organization and personal audit owners with the same id remain distinct`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()
            val sharedId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertOwnedOutbox(connection, UUID.randomUUID(), "ORGANIZATION", sharedId, sharedId)
                insertOwnedOutbox(connection, UUID.randomUUID(), "USER", sharedId, null)

                connection.prepareStatement(
                    "SELECT owner_type, COUNT(*) FROM audit_outbox WHERE owner_id = ? GROUP BY owner_type ORDER BY owner_type",
                ).use { statement ->
                    statement.setObject(1, sharedId)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals("ORGANIZATION", rows.getString(1))
                        assertEquals(1, rows.getInt(2))
                        assertTrue(rows.next())
                        assertEquals("USER", rows.getString(1))
                        assertEquals(1, rows.getInt(2))
                    }
                }
            }
        }
    }

    private fun withPostgres(block: (AuditOwnerPostgreSQLContainer) -> Unit)
    {
        val postgres = AuditOwnerPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_audit_owner_test")
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

    private fun flyway(postgres: AuditOwnerPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertOutbox(connection: Connection, eventId: UUID, organizationId: UUID?)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO audit_outbox
                (id, event_id, idempotency_key, event_type_key, category, outcome, organization_id,
                 payload_json, occurred_at, recorded_at, catalog_version)
            VALUES (?, ?, ?, 'auth.login.succeeded', 'AUTHENTICATION', 'SUCCESS', ?, '{}', ?, ?, 14)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, eventId)
            statement.setString(3, eventId.toString())
            statement.setObject(4, organizationId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertOwnedOutbox(
        connection: Connection,
        eventId: UUID,
        ownerType: String,
        ownerId: UUID?,
        organizationId: UUID?,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO audit_outbox
                (id, event_id, idempotency_key, event_type_key, category, outcome, owner_type,
                 owner_id, organization_id, payload_json, occurred_at, recorded_at, catalog_version)
            VALUES (?, ?, ?, 'auth.login.succeeded', 'AUTHENTICATION', 'SUCCESS', ?, ?, ?, '{}', ?, ?, 14)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, eventId)
            statement.setString(3, eventId.toString())
            statement.setString(4, ownerType)
            statement.setObject(5, ownerId)
            statement.setObject(6, organizationId)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun refused(connection: Connection, expectedConstraint: String, block: () -> Unit)
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
        assertTrue(refusal.message.orEmpty().contains(expectedConstraint), refusal.message)
    }

    private fun ownerType(connection: Connection, table: String, eventId: UUID): String? =
        value(connection, table, "owner_type", eventId) as? String

    private fun ownerId(connection: Connection, table: String, eventId: UUID): UUID? =
        value(connection, table, "owner_id", eventId) as? UUID

    private fun organizationId(connection: Connection, table: String, eventId: UUID): UUID? =
        value(connection, table, "organization_id", eventId) as? UUID

    private fun value(connection: Connection, table: String, column: String, eventId: UUID): Any?
    {
        connection.prepareStatement("SELECT $column FROM $table WHERE event_id = ?").use { statement ->
            statement.setObject(1, eventId)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                return rows.getObject(1)
            }
        }
    }
}
