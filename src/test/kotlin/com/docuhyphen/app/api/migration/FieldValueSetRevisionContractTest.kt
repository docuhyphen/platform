package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class ValueSetRevisionPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<ValueSetRevisionPostgreSQLContainer>(imageName)

/**
 * A client that edits a set of answers has to be able to say which state of that set it edited, and
 * the only durable thing it can name is a count the database keeps. The released schema keeps no
 * such count: the set carries a timestamp, which two changes within the same clock tick share and
 * which a clock correction can move backwards.
 *
 * These contract tests prove that every set already stored gains that count, that a new set starts
 * at it, and that the database itself refuses to let a count move backwards, so a stale validator
 * can never be made to look current.
 */
class FieldValueSetRevisionContractTest
{
    private val releasedVersion = "80"

    @Test
    fun `every set already stored gains its first revision without changing its answers`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            val movedAt: Timestamp
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertValueSet(connection, fixture.assignmentId, "ROOT")
                insertValueSet(connection, fixture.otherAssignmentId, "ROOT")
                insertValue(connection, fixture, fixture.firstValueId, fixture.textContractId, "First answer")
                movedAt = updatedAtOf(connection, requireNotNull(rootSetOf(connection, fixture.assignmentId)))
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val rootSetId = requireNotNull(rootSetOf(connection, fixture.assignmentId))
                assertEquals(1L, revisionOf(connection, rootSetId), "An existing set has been through one state")
                assertEquals(1L, revisionOf(connection, requireNotNull(rootSetOf(connection, fixture.otherAssignmentId))))

                // Giving a set a count must not disturb what it holds or when it last moved.
                assertEquals("First answer", storedText(connection, fixture.firstValueId))
                assertEquals(movedAt, updatedAtOf(connection, rootSetId))
            }
        }
    }

    @Test
    fun `a new set starts at its first revision and cannot be created below it`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                val defaulted = insertValueSet(connection, fixture.assignmentId, "ROOT")
                assertEquals(1L, revisionOf(connection, defaulted))

                val refused = assertThrows<SQLException> {
                    insertValueSet(connection, fixture.otherAssignmentId, "ROOT", revision = 0)
                }
                assertTrue(
                    refused.message.orEmpty().contains("ck_field_value_set_revision"),
                    "A set that has never changed is still at its first revision: ${refused.message}",
                )
            }
        }
    }

    @Test
    fun `a revision moves forward or stays put but never backwards`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                val setId = insertValueSet(connection, fixture.assignmentId, "ROOT")

                setRevision(connection, setId, 2)
                setRevision(connection, setId, 5)
                assertEquals(5L, revisionOf(connection, setId))

                // Rewriting the set without touching its count is an ordinary update.
                setRevision(connection, setId, 5)
                assertEquals(5L, revisionOf(connection, setId))

                val refused = assertThrows<SQLException> { setRevision(connection, setId, 4) }
                assertTrue(
                    refused.message.orEmpty().contains("cannot move backwards"),
                    "A validator already handed out must never become current again: ${refused.message}",
                )
                assertEquals(5L, revisionOf(connection, setId), "The refused update left the count alone")
            }
        }
    }

    @Test
    fun `each set counts its own changes`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                val rootSetId = insertValueSet(connection, fixture.assignmentId, "ROOT")
                val occurrenceSetId = insertValueSet(
                    connection, fixture.assignmentId, "OCCURRENCE", occurrencePath = "items[0]",
                )

                setRevision(connection, rootSetId, 9)

                assertEquals(9L, revisionOf(connection, rootSetId))
                assertEquals(1L, revisionOf(connection, occurrenceSetId), "A repetition counts only its own changes")
            }
        }
    }

    // ── Fixture ─────────────────────────────────────────────────────────────────

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val textContractId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val textBindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val otherAssignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
        val otherResourceId: UUID = UUID.randomUUID()
        val firstValueId: UUID = UUID.randomUUID()
        val assignedAt: Timestamp = Timestamp.from(Instant.parse("2026-01-05T10:15:30Z"))
    }

    private fun withPostgres(block: (ValueSetRevisionPostgreSQLContainer) -> Unit)
    {
        val postgres = ValueSetRevisionPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_value_set_revision_test")
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

    private fun flyway(postgres: ValueSetRevisionPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertFieldsFixture(connection: Connection, fixture: Fixture)
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete,
                                      created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.organizationId)
            statement.setString(2, "Process Owner Org")
            statement.setString(3, "REG-PROCESS-SET-REVISION")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', 'recorded-note', 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.fieldDefinitionId)
            statement.setObject(2, fixture.organizationId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label,
                                        created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Recorded note', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.textContractId)
            statement.setObject(2, fixture.fieldDefinitionId)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key,
                                           display_name, target_resource_type, status, created_at,
                                           updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', 'process-data', 'Process data', 'EXCHANGE',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.schemaDefinitionId)
            statement.setObject(2, fixture.organizationId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at,
                                        created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.schemaVersionId)
            statement.setObject(2, fixture.schemaDefinitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id,
                                              field_definition_id, display_order, is_required,
                                              is_read_only, visibility)
            VALUES (?, ?, ?, ?, 0, false, false, 'INTERNAL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.textBindingId)
            statement.setObject(2, fixture.schemaVersionId)
            statement.setObject(3, fixture.textContractId)
            statement.setObject(4, fixture.fieldDefinitionId)
            statement.executeUpdate()
        }

        insertAssignment(connection, fixture, fixture.assignmentId, fixture.resourceId)
        insertAssignment(connection, fixture, fixture.otherAssignmentId, fixture.otherResourceId)
    }

    private fun insertAssignment(connection: Connection, fixture: Fixture, assignmentId: UUID, resourceId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id,
                                           scope_kind, scope_org_id, assignment_source, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, assignmentId)
            statement.setObject(2, resourceId)
            statement.setObject(3, fixture.schemaVersionId)
            statement.setObject(4, fixture.organizationId)
            statement.setTimestamp(5, fixture.assignedAt)
            statement.executeUpdate()
        }
    }

    /** Writes an answer into the assignment's root set, which the released schema already requires. */
    private fun insertValue(
        connection: Connection,
        fixture: Fixture,
        valueId: UUID,
        contractId: UUID,
        text: String,
    )
    {
        val rootSetId = requireNotNull(rootSetOf(connection, fixture.assignmentId))
        connection.prepareStatement(
            """
            INSERT INTO field_value (id, field_value_set_id, schema_assignment_id,
                                     schema_field_binding_id, field_contract_id, resource_type,
                                     resource_id, value_type, text_value, provenance, created_at,
                                     updated_at)
            VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, valueId)
            statement.setObject(2, rootSetId)
            statement.setObject(3, fixture.assignmentId)
            statement.setObject(4, fixture.textBindingId)
            statement.setObject(5, contractId)
            statement.setObject(6, fixture.resourceId)
            statement.setString(7, text)
            statement.setTimestamp(8, fixture.assignedAt)
            statement.setTimestamp(9, fixture.assignedAt)
            statement.executeUpdate()
        }
    }

    private fun insertValueSet(
        connection: Connection,
        assignmentId: UUID,
        kind: String,
        occurrencePath: String? = null,
        revision: Long? = null,
    ): UUID
    {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        val columns = if (revision == null) "" else ", revision"
        val placeholder = if (revision == null) "" else ", ?"
        connection.prepareStatement(
            """
            INSERT INTO field_value_set (id, schema_assignment_id, set_kind, occurrence_path,
                                         created_at, updated_at$columns)
            VALUES (?, ?, ?, ?, ?, ?$placeholder)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, assignmentId)
            statement.setString(3, kind)
            statement.setString(4, occurrencePath)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            revision?.let { statement.setLong(7, it) }
            statement.executeUpdate()
        }
        return id
    }

    private fun setRevision(connection: Connection, valueSetId: UUID, revision: Long)
    {
        connection.prepareStatement("UPDATE field_value_set SET revision = ? WHERE id = ?").use { statement ->
            statement.setLong(1, revision)
            statement.setObject(2, valueSetId)
            statement.executeUpdate()
        }
    }

    private fun rootSetOf(connection: Connection, assignmentId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT id FROM field_value_set WHERE schema_assignment_id = ? AND set_kind = 'ROOT'",
        ).use { statement ->
            statement.setObject(1, assignmentId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.getObject(1, UUID::class.java)
            }
        }

    private fun revisionOf(connection: Connection, valueSetId: UUID): Long =
        connection.prepareStatement("SELECT revision FROM field_value_set WHERE id = ?").use { statement ->
            statement.setObject(1, valueSetId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }

    private fun updatedAtOf(connection: Connection, valueSetId: UUID): Timestamp =
        connection.prepareStatement("SELECT updated_at FROM field_value_set WHERE id = ?").use { statement ->
            statement.setObject(1, valueSetId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getTimestamp(1)
            }
        }

    private fun storedText(connection: Connection, valueId: UUID): String? =
        connection.prepareStatement("SELECT text_value FROM field_value WHERE id = ?").use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.getString(1)
            }
        }
}
