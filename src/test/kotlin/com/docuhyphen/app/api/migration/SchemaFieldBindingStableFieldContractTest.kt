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

private class StableFieldPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<StableFieldPostgreSQLContainer>(imageName)

/**
 * A Schema Version must describe each stable Field at most once. The released schema only prevents
 * binding the same immutable Field Contract twice, so two contract versions of one Field Definition
 * could both appear in a single version.
 *
 * These contract tests prove that the invariant reaches a database that already holds such a
 * conflict, that the resolution is recorded rather than silent, that no entered value is destroyed,
 * and that a database with nothing to resolve gains the same invariant.
 */
class SchemaFieldBindingStableFieldContractTest
{
    private val invariantMigrationVersion = "77"
    private val previousVersion = "76"

    @Test
    fun `an existing duplicate stable field binding is recorded and resolved before the invariant applies`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = previousVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                // The released schema accepts both contract versions of one field in one version.
                insertBinding(connection, fixture.conflictedBindingV1Id, fixture.conflictedVersionId,
                    fixture.referenceContractV1Id, order = 0)
                insertBinding(connection, fixture.conflictedBindingV2Id, fixture.conflictedVersionId,
                    fixture.referenceContractV2Id, order = 1)
                insertBinding(connection, fixture.conflictedReviewBindingId, fixture.conflictedVersionId,
                    fixture.reviewContractId, order = 2)

                insertBinding(connection, fixture.cleanReferenceBindingId, fixture.cleanVersionId,
                    fixture.referenceContractV2Id, order = 0)
                insertBinding(connection, fixture.cleanReviewBindingId, fixture.cleanVersionId,
                    fixture.reviewContractId, order = 1)

                // Both sides of the conflict already hold an answer.
                insertValue(connection, fixture, fixture.valueOnV1Id, fixture.conflictedBindingV1Id,
                    fixture.referenceContractV1Id, "First answer")
                insertValue(connection, fixture, fixture.valueOnV2Id, fixture.conflictedBindingV2Id,
                    fixture.referenceContractV2Id, "Second answer")

                assertEquals(3, bindingCount(connection, fixture.conflictedVersionId))
            }

            flyway(postgres, target = invariantMigrationVersion).migrate()

            postgres.createConnection("").use { connection ->
                // The newer contract wins the tie because both sides carry the same number of values.
                assertEquals(
                    listOf(fixture.referenceContractV2Id, fixture.reviewContractId),
                    boundContracts(connection, fixture.conflictedVersionId),
                )

                assertEquals(
                    listOf("REMOVED" to fixture.referenceContractV1Id, "RETAINED" to fixture.referenceContractV2Id),
                    reportedConflicts(connection, fixture.conflictedVersionId),
                )

                // A resolved conflict never destroys an entered answer; only the pointer to the
                // binding that no longer exists is cleared.
                assertEquals("First answer", valueTextOf(connection, fixture.valueOnV1Id))
                assertNull(bindingPointerOf(connection, fixture.valueOnV1Id))
                assertEquals(fixture.referenceContractV1Id, valueContractOf(connection, fixture.valueOnV1Id))
                assertEquals(fixture.conflictedBindingV2Id, bindingPointerOf(connection, fixture.valueOnV2Id))

                // A version that never held a conflict keeps every binding it had.
                assertEquals(
                    listOf(fixture.referenceContractV2Id, fixture.reviewContractId),
                    boundContracts(connection, fixture.cleanVersionId),
                )
                assertTrue(reportedConflicts(connection, fixture.cleanVersionId).isEmpty())

                assertEquals(
                    listOf(fixture.referenceFieldId, fixture.reviewFieldId),
                    boundFieldDefinitions(connection, fixture.cleanVersionId),
                )

                assertRefusesDuplicateStableField(connection, fixture)
                assertRefusesMismatchedStableField(connection, fixture)
            }
        }
    }

    @Test
    fun `a database with no bindings to resolve gains the same invariant`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = invariantMigrationVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                assertEquals(0, totalReportedConflicts(connection))

                insertFieldsFixture(connection, fixture)
                insertBinding(connection, fixture.cleanReferenceBindingId, fixture.cleanVersionId,
                    fixture.referenceContractV2Id, order = 0, fieldDefinitionId = fixture.referenceFieldId)
                insertBinding(connection, fixture.cleanReviewBindingId, fixture.cleanVersionId,
                    fixture.reviewContractId, order = 1, fieldDefinitionId = fixture.reviewFieldId)

                assertEquals(
                    listOf(fixture.referenceFieldId, fixture.reviewFieldId),
                    boundFieldDefinitions(connection, fixture.cleanVersionId),
                )

                assertRefusesDuplicateStableField(connection, fixture)
                assertRefusesMismatchedStableField(connection, fixture)
            }
        }
    }

    private fun assertRefusesDuplicateStableField(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertBinding(connection, UUID.randomUUID(), fixture.cleanVersionId,
                fixture.referenceContractV1Id, order = 9, fieldDefinitionId = fixture.referenceFieldId)
        }
        assertTrue(
            refused.message?.contains("ux_binding_field_definition") == true,
            "Expected the stable field invariant to refuse the row: ${refused.message}",
        )
    }

    private fun assertRefusesMismatchedStableField(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertBinding(connection, UUID.randomUUID(), fixture.emptyVersionId,
                fixture.referenceContractV1Id, order = 0, fieldDefinitionId = fixture.reviewFieldId)
        }
        assertTrue(
            refused.message?.contains("fk_binding_contract_definition") == true,
            "Expected the contract consistency rule to refuse the row: ${refused.message}",
        )
    }

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val referenceFieldId: UUID = UUID.randomUUID()
        val referenceContractV1Id: UUID = UUID.randomUUID()
        val referenceContractV2Id: UUID = UUID.randomUUID()
        val reviewFieldId: UUID = UUID.randomUUID()
        val reviewContractId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val conflictedVersionId: UUID = UUID.randomUUID()
        val cleanVersionId: UUID = UUID.randomUUID()
        val emptyVersionId: UUID = UUID.randomUUID()
        val conflictedBindingV1Id: UUID = UUID.randomUUID()
        val conflictedBindingV2Id: UUID = UUID.randomUUID()
        val conflictedReviewBindingId: UUID = UUID.randomUUID()
        val cleanReferenceBindingId: UUID = UUID.randomUUID()
        val cleanReviewBindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
        val valueOnV1Id: UUID = UUID.randomUUID()
        val valueOnV2Id: UUID = UUID.randomUUID()
    }

    private fun withPostgres(block: (StableFieldPostgreSQLContainer) -> Unit)
    {
        val postgres = StableFieldPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_stable_field_test")
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

    private fun flyway(postgres: StableFieldPostgreSQLContainer, target: String? = null): Flyway
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

        insertFieldDefinition(connection, fixture.referenceFieldId, fixture.organizationId, "reference-code")
        insertFieldDefinition(connection, fixture.reviewFieldId, fixture.organizationId, "review-note")

        insertContract(connection, fixture.referenceContractV1Id, fixture.referenceFieldId, 1, "Reference code")
        insertContract(connection, fixture.referenceContractV2Id, fixture.referenceFieldId, 2, "Reference code")
        insertContract(connection, fixture.reviewContractId, fixture.reviewFieldId, 1, "Review note")

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

        insertSchemaVersion(connection, fixture.conflictedVersionId, fixture.schemaDefinitionId, 1)
        insertSchemaVersion(connection, fixture.cleanVersionId, fixture.schemaDefinitionId, 2)
        insertSchemaVersion(connection, fixture.emptyVersionId, fixture.schemaDefinitionId, 3)

        connection.prepareStatement(
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind,
                                           scope_org_id, assignment_source, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.assignmentId)
            statement.setObject(2, fixture.resourceId)
            statement.setObject(3, fixture.conflictedVersionId)
            statement.setObject(4, fixture.organizationId)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertFieldDefinition(connection: Connection, id: UUID, organizationId: UUID, key: String)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, key)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertContract(connection: Connection, id: UUID, definitionId: UUID, version: Int, label: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label,
                                        created_at)
            VALUES (?, ?, ?, 'SHORT_TEXT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, version)
            statement.setString(4, label)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertSchemaVersion(connection: Connection, id: UUID, definitionId: UUID, number: Int)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at,
                                        created_at)
            VALUES (?, ?, ?, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, number)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    /**
     * @param fieldDefinitionId supplied only once the invariant column exists; before the migration
     *   the column is not part of the table.
     */
    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        contractId: UUID,
        order: Int,
        fieldDefinitionId: UUID? = null,
    )
    {
        val columns = if (fieldDefinitionId == null) "" else ", field_definition_id"
        val placeholder = if (fieldDefinitionId == null) "" else ", ?"
        connection.prepareStatement(
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, display_order,
                                              is_required, is_read_only, visibility$columns)
            VALUES (?, ?, ?, ?, false, false, 'INTERNAL'$placeholder)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setObject(3, contractId)
            statement.setInt(4, order)
            fieldDefinitionId?.let { statement.setObject(5, it) }
            statement.executeUpdate()
        }
    }

    private fun insertValue(
        connection: Connection,
        fixture: Fixture,
        id: UUID,
        bindingId: UUID,
        contractId: UUID,
        text: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_value (id, schema_assignment_id, schema_field_binding_id, field_contract_id,
                                     resource_type, resource_id, value_type, text_value, provenance,
                                     created_at, updated_at)
            VALUES (?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, fixture.assignmentId)
            statement.setObject(3, bindingId)
            statement.setObject(4, contractId)
            statement.setObject(5, fixture.resourceId)
            statement.setString(6, text)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun bindingCount(connection: Connection, versionId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM schema_field_binding WHERE schema_version_id = ?",
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun boundContracts(connection: Connection, versionId: UUID): List<UUID> =
        connection.prepareStatement(
            """
            SELECT field_contract_id FROM schema_field_binding
            WHERE schema_version_id = ? ORDER BY display_order
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
            }
        }

    private fun boundFieldDefinitions(connection: Connection, versionId: UUID): List<UUID> =
        connection.prepareStatement(
            """
            SELECT field_definition_id FROM schema_field_binding
            WHERE schema_version_id = ? ORDER BY display_order
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
            }
        }

    private fun reportedConflicts(connection: Connection, versionId: UUID): List<Pair<String, UUID>> =
        connection.prepareStatement(
            """
            SELECT resolution, field_contract_id FROM schema_field_binding_conflict
            WHERE schema_version_id = ? ORDER BY resolution, field_contract_id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.getString(1) to rows.getObject(2, UUID::class.java))
                }
            }
        }

    private fun totalReportedConflicts(connection: Connection): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM schema_field_binding_conflict").use { statement ->
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun valueTextOf(connection: Connection, valueId: UUID): String? =
        singleValueColumn(connection, valueId, "text_value") { rows -> rows.getString(1) }

    private fun valueContractOf(connection: Connection, valueId: UUID): UUID? =
        singleValueColumn(connection, valueId, "field_contract_id") { rows -> rows.getObject(1, UUID::class.java) }

    private fun bindingPointerOf(connection: Connection, valueId: UUID): UUID? =
        singleValueColumn(connection, valueId, "schema_field_binding_id") { rows ->
            rows.getObject(1, UUID::class.java)
        }

    private fun <T> singleValueColumn(
        connection: Connection,
        valueId: UUID,
        column: String,
        read: (java.sql.ResultSet) -> T?,
    ): T? =
        connection.prepareStatement("SELECT $column FROM field_value WHERE id = ?").use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else read(rows)
            }
        }
}
