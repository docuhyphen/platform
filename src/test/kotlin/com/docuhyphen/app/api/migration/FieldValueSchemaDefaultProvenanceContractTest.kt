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

private class FieldValuePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<FieldValuePostgreSQLContainer>(imageName)

/**
 * Verifies that widening the field value provenance vocabulary lets a schema-configured default be
 * recorded as such, keeps every previously recorded provenance valid, and still rejects a value
 * whose provenance is not a known origin.
 */
class FieldValueSchemaDefaultProvenanceContractTest
{
    private val provenanceMigrationVersion = "76"
    private val previousVersion = "75"

    @Test
    fun `schema default provenance is accepted only after the migration and existing rows survive`()
    {
        val postgres = FieldValuePostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_field_value_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            flyway(postgres, target = previousVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertFieldValue(connection, fixture, fixture.userValueId, "USER")

                // The vocabulary in place before the migration has no place for a value that came
                // from the schema's own configured default. A distinct field contract is used so the
                // only rule that can reject the row is the provenance check.
                val refused = assertThrows<SQLException> {
                    insertFieldValue(connection, fixture, UUID.randomUUID(), "SCHEMA_DEFAULT", UUID.randomUUID())
                }
                assertTrue(
                    refused.message?.contains("ck_field_value_provenance") == true,
                    "Expected the provenance check to refuse the row: ${refused.message}",
                )
            }

            // Stops at this migration so the contract stays about the provenance widening alone.
            flyway(postgres, target = provenanceMigrationVersion).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("USER", provenanceOf(connection, fixture.userValueId))

                insertFieldValue(
                    connection, fixture, fixture.defaultValueId, "SCHEMA_DEFAULT", UUID.randomUUID(),
                )
                assertEquals("SCHEMA_DEFAULT", provenanceOf(connection, fixture.defaultValueId))

                // Every origin recorded before the change is still a legal value.
                listOf("BLUEPRINT_DEFAULT", "API", "WORKFLOW_ACTION", "CALCULATED", "MIGRATION")
                    .forEach { provenance ->
                        val id = UUID.randomUUID()
                        insertFieldValue(connection, fixture, id, provenance, UUID.randomUUID())
                        assertEquals(provenance, provenanceOf(connection, id))
                    }

                assertThrows<SQLException> {
                    insertFieldValue(connection, fixture, UUID.randomUUID(), "GUESSED", UUID.randomUUID())
                }
            }
        }
        finally
        {
            postgres.stop()
        }
    }

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val fieldContractId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val bindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
        val userValueId: UUID = UUID.randomUUID()
        val defaultValueId: UUID = UUID.randomUUID()
    }

    private fun flyway(postgres: FieldValuePostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "REG-PROCESS-OWNER")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', 'note', 'PUBLISHED', ?, ?)
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
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Note', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.fieldContractId)
            statement.setObject(2, fixture.fieldDefinitionId)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key,
                                           display_name, target_resource_type, status, created_at, updated_at)
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
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, display_order,
                                              is_required, is_read_only, default_value_json, visibility)
            VALUES (?, ?, ?, 0, false, true, '"Reference copy"', 'INTERNAL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.bindingId)
            statement.setObject(2, fixture.schemaVersionId)
            statement.setObject(3, fixture.fieldContractId)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind,
                                           scope_org_id, assignment_source, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.assignmentId)
            statement.setObject(2, fixture.resourceId)
            statement.setObject(3, fixture.schemaVersionId)
            statement.setObject(4, fixture.organizationId)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    /**
     * One value row against the fixture's assignment. A distinct [contractId] is required for each
     * extra row because a resource holds at most one value per field contract.
     */
    private fun insertFieldValue(
        connection: Connection,
        fixture: Fixture,
        id: UUID,
        provenance: String,
        contractId: UUID? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        val effectiveContractId = contractId?.also { extraContract(connection, fixture, it) }
            ?: fixture.fieldContractId

        connection.prepareStatement(
            """
            INSERT INTO field_value (id, schema_assignment_id, schema_field_binding_id, field_contract_id,
                                     resource_type, resource_id, value_type, text_value, provenance,
                                     created_at, updated_at)
            VALUES (?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', 'Reference copy', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, fixture.assignmentId)
            statement.setObject(3, fixture.bindingId)
            statement.setObject(4, effectiveContractId)
            statement.setObject(5, fixture.resourceId)
            statement.setString(6, provenance)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun extraContract(connection: Connection, fixture: Fixture, contractId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label,
                                        created_at)
            VALUES (?, ?, (SELECT COALESCE(MAX(contract_version), 0) + 1 FROM field_contract
                           WHERE field_definition_id = ?), 'SHORT_TEXT', 'Note', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, contractId)
            statement.setObject(2, fixture.fieldDefinitionId)
            statement.setObject(3, fixture.fieldDefinitionId)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun provenanceOf(connection: Connection, id: UUID): String =
        connection.prepareStatement("SELECT provenance FROM field_value WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getString(1)
            }
        }
}
