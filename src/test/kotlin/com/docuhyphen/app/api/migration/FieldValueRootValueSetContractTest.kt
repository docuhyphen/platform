package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class RootValueSetPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<RootValueSetPostgreSQLContainer>(imageName)

/**
 * Typed answers must belong to a named set of answers rather than directly to the assignment, so a
 * process that repeats a group of questions can hold one answer per question per repetition. The
 * released schema keys an answer by its assignment and field contract, which allows exactly one
 * answer per question per resource forever.
 *
 * These contract tests prove that every answer already stored moves into its assignment's single
 * root set without changing, that a database with nothing to carry over gains the same shape, and
 * that uniqueness now belongs to the set rather than to the assignment.
 */
class FieldValueRootValueSetContractTest
{
    private val releasedVersion = "78"

    @Test
    fun `existing answers move into the single root set of their assignment`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertAssignedValue(connection, fixture, fixture.firstValueId, fixture.assignmentId, fixture.textContractId, "First answer")
                insertAssignedValue(connection, fixture, fixture.secondValueId, fixture.assignmentId, fixture.numberContractId, "Second answer")
                insertAssignedValue(connection, fixture, fixture.otherValueId, fixture.otherAssignmentId, fixture.textContractId, "Other answer")
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val rootSetId = requireNotNull(rootSetOf(connection, fixture.assignmentId)) {
                    "Every assignment must own a root set after the upgrade"
                }
                val otherRootSetId = requireNotNull(rootSetOf(connection, fixture.otherAssignmentId))

                assertEquals(1, rootSetCount(connection, fixture.assignmentId))
                assertEquals(rootSetId, valueSetOf(connection, fixture.firstValueId))
                assertEquals(rootSetId, valueSetOf(connection, fixture.secondValueId))
                assertEquals(otherRootSetId, valueSetOf(connection, fixture.otherValueId))

                // Carrying an answer over must not rewrite what it says.
                assertEquals("First answer", storedText(connection, fixture.firstValueId))
                assertEquals("Second answer", storedText(connection, fixture.secondValueId))
                assertEquals("Other answer", storedText(connection, fixture.otherValueId))
            }
        }
    }

    @Test
    fun `an assignment with no answers still owns a root set`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection -> insertFieldsFixture(connection, fixture) }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertNotNull(rootSetOf(connection, fixture.assignmentId))
                assertNotNull(rootSetOf(connection, fixture.otherAssignmentId))
            }
        }
    }

    @Test
    fun `a database with no answers gains the same shape`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                val rootSetId = insertValueSet(connection, fixture.assignmentId, kind = "ROOT")

                assertRefusesASecondRootSet(connection, fixture)
                assertRefusesTwoAnswersForOneQuestionInOneSet(connection, fixture, rootSetId)
                assertAcceptsOneAnswerPerQuestionPerSet(connection, fixture, rootSetId)
                assertRefusesASetOfAnotherAssignment(connection, fixture, rootSetId)
                assertRefusesAnOccurrenceWithNoPath(connection, fixture)
            }
        }
    }

    @Test
    fun `an answer that names no set is refused rather than stored outside every set`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                val refused = assertThrows<SQLException> {
                    insertAssignedValue(
                        connection, fixture, UUID.randomUUID(), fixture.assignmentId,
                        fixture.textContractId, "Answer with no set",
                    )
                }
                assertTrue(
                    refused.message?.contains("field_value_set_id") == true,
                    "Expected the missing set to be named in the refusal: ${refused.message}",
                )
            }
        }
    }

    /** One assignment answers once as itself; a second root set would make its answers ambiguous. */
    private fun assertRefusesASecondRootSet(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertValueSet(connection, fixture.assignmentId, kind = "ROOT")
        }
        assertTrue(
            refused.message?.contains("ux_field_value_set_root") == true,
            "Expected the root rule to refuse a second root set: ${refused.message}",
        )
    }

    private fun assertRefusesTwoAnswersForOneQuestionInOneSet(
        connection: Connection,
        fixture: Fixture,
        rootSetId: UUID,
    )
    {
        insertSetValue(connection, fixture, UUID.randomUUID(), rootSetId, fixture.textContractId, "Answer")

        val refused = assertThrows<SQLException> {
            insertSetValue(connection, fixture, UUID.randomUUID(), rootSetId, fixture.textContractId, "Second answer")
        }
        assertTrue(
            refused.message?.contains("ux_field_value_set_binding") == true,
            "Expected the set rule to refuse a second answer to one question: ${refused.message}",
        )
    }

    /**
     * The capability the set exists for: one assignment may hold the same question more than once as
     * long as each answer belongs to its own repetition.
     */
    private fun assertAcceptsOneAnswerPerQuestionPerSet(
        connection: Connection,
        fixture: Fixture,
        rootSetId: UUID,
    )
    {
        val occurrenceSetId = insertValueSet(
            connection, fixture.assignmentId, kind = "OCCURRENCE", occurrencePath = "items[0]",
        )
        val occurrenceValueId = UUID.randomUUID()

        insertSetValue(connection, fixture, occurrenceValueId, occurrenceSetId, fixture.textContractId, "Repeated answer")

        assertEquals(occurrenceSetId, valueSetOf(connection, occurrenceValueId))
        assertEquals(1, rootSetCount(connection, fixture.assignmentId))
        assertEquals(rootSetId, rootSetOf(connection, fixture.assignmentId))
    }

    private fun assertRefusesASetOfAnotherAssignment(
        connection: Connection,
        fixture: Fixture,
        rootSetId: UUID,
    )
    {
        val refused = assertThrows<SQLException> {
            connection.prepareStatement(
                """
                INSERT INTO field_value (id, field_value_set_id, schema_assignment_id,
                                         schema_field_binding_id, field_contract_id, resource_type,
                                         resource_id, value_type, text_value, provenance, created_at,
                                         updated_at)
                VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', 'Crossed answer', 'USER', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, rootSetId)
                statement.setObject(3, fixture.otherAssignmentId)
                statement.setObject(4, fixture.numberBindingId)
                statement.setObject(5, fixture.numberContractId)
                statement.setObject(6, fixture.otherResourceId)
                statement.setTimestamp(7, now)
                statement.setTimestamp(8, now)
                statement.executeUpdate()
            }
        }
        assertTrue(
            refused.message?.contains("fk_field_value_set") == true,
            "Expected an answer to be refused a set belonging to another assignment: ${refused.message}",
        )
    }

    private fun assertRefusesAnOccurrenceWithNoPath(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertValueSet(connection, fixture.otherAssignmentId, kind = "OCCURRENCE")
        }
        assertTrue(
            refused.message?.contains("ck_field_value_set_occurrence") == true,
            "Expected a repetition with no path to be refused: ${refused.message}",
        )
    }

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val numberDefinitionId: UUID = UUID.randomUUID()
        val textContractId: UUID = UUID.randomUUID()
        val numberContractId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val textBindingId: UUID = UUID.randomUUID()
        val numberBindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
        val otherAssignmentId: UUID = UUID.randomUUID()
        val otherResourceId: UUID = UUID.randomUUID()
        val firstValueId: UUID = UUID.randomUUID()
        val secondValueId: UUID = UUID.randomUUID()
        val otherValueId: UUID = UUID.randomUUID()
    }

    private fun withPostgres(block: (RootValueSetPostgreSQLContainer) -> Unit)
    {
        val postgres = RootValueSetPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_root_value_set_test")
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

    private fun flyway(postgres: RootValueSetPostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "REG-PROCESS-VALUE-SET")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        insertFieldDefinition(connection, fixture.fieldDefinitionId, fixture.organizationId, "recorded-note")
        insertFieldDefinition(connection, fixture.numberDefinitionId, fixture.organizationId, "recorded-count")
        insertFieldContract(connection, fixture.textContractId, fixture.fieldDefinitionId, "Recorded note")
        insertFieldContract(connection, fixture.numberContractId, fixture.numberDefinitionId, "Recorded count")

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

        insertBinding(connection, fixture, fixture.textBindingId, fixture.textContractId, fixture.fieldDefinitionId, 0)
        insertBinding(connection, fixture, fixture.numberBindingId, fixture.numberContractId, fixture.numberDefinitionId, 1)

        insertAssignment(connection, fixture, fixture.assignmentId, fixture.resourceId)
        insertAssignment(connection, fixture, fixture.otherAssignmentId, fixture.otherResourceId)
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

    private fun insertFieldContract(connection: Connection, id: UUID, definitionId: UUID, label: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label,
                                        created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, label)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertBinding(
        connection: Connection,
        fixture: Fixture,
        bindingId: UUID,
        contractId: UUID,
        definitionId: UUID,
        order: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id,
                                              field_definition_id, display_order, is_required,
                                              is_read_only, visibility)
            VALUES (?, ?, ?, ?, ?, false, false, 'INTERNAL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, bindingId)
            statement.setObject(2, fixture.schemaVersionId)
            statement.setObject(3, contractId)
            statement.setObject(4, definitionId)
            statement.setInt(5, order)
            statement.executeUpdate()
        }
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
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    /** Writes the released form, which is all the schema can express before the upgrade. */
    private fun insertAssignedValue(
        connection: Connection,
        fixture: Fixture,
        valueId: UUID,
        assignmentId: UUID,
        contractId: UUID,
        text: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        val bindingId = if (contractId == fixture.textContractId) fixture.textBindingId else fixture.numberBindingId
        val resourceId = if (assignmentId == fixture.assignmentId) fixture.resourceId else fixture.otherResourceId

        connection.prepareStatement(
            """
            INSERT INTO field_value (id, schema_assignment_id, schema_field_binding_id,
                                     field_contract_id, resource_type, resource_id, value_type,
                                     text_value, provenance, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, valueId)
            statement.setObject(2, assignmentId)
            statement.setObject(3, bindingId)
            statement.setObject(4, contractId)
            statement.setObject(5, resourceId)
            statement.setString(6, text)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun insertSetValue(
        connection: Connection,
        fixture: Fixture,
        valueId: UUID,
        valueSetId: UUID,
        contractId: UUID,
        text: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        val bindingId = if (contractId == fixture.textContractId) fixture.textBindingId else fixture.numberBindingId

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
            statement.setObject(2, valueSetId)
            statement.setObject(3, fixture.assignmentId)
            statement.setObject(4, bindingId)
            statement.setObject(5, contractId)
            statement.setObject(6, fixture.resourceId)
            statement.setString(7, text)
            statement.setTimestamp(8, now)
            statement.setTimestamp(9, now)
            statement.executeUpdate()
        }
    }

    private fun insertValueSet(
        connection: Connection,
        assignmentId: UUID,
        kind: String,
        occurrencePath: String? = null,
    ): UUID
    {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_value_set (id, schema_assignment_id, set_kind, occurrence_path,
                                         created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, assignmentId)
            statement.setString(3, kind)
            statement.setString(4, occurrencePath)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
        return id
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

    private fun rootSetCount(connection: Connection, assignmentId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM field_value_set WHERE schema_assignment_id = ? AND set_kind = 'ROOT'",
        ).use { statement ->
            statement.setObject(1, assignmentId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun valueSetOf(connection: Connection, valueId: UUID): UUID? =
        connection.prepareStatement("SELECT field_value_set_id FROM field_value WHERE id = ?").use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.getObject(1, UUID::class.java)
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
