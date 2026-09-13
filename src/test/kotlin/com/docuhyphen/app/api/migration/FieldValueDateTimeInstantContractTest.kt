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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private class DateTimeInstantPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DateTimeInstantPostgreSQLContainer>(imageName)

/**
 * A stored date-time answer must be an explicit moment, and the offset it was submitted with must
 * have somewhere to live. The released column is a naive TIMESTAMP with no offset anywhere, so these
 * contract tests prove that an existing answer is carried over as the UTC reading it effectively
 * was, that a database with nothing to carry over gains the same shape, and that the offset column
 * only accepts an offset a reading can really carry.
 */
class FieldValueDateTimeInstantContractTest
{
    private val instantMigrationVersion = "78"
    private val previousVersion = "77"

    @Test
    fun `an existing naive date-time answer is carried over as the UTC moment it was`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = previousVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertNaiveDateTimeValue(connection, fixture, "2026-08-31 08:15:30")
            }

            flyway(postgres, target = instantMigrationVersion).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals(
                    Instant.parse("2026-08-31T08:15:30Z"),
                    storedMoment(connection, fixture.valueId),
                )
                // Nothing ever recorded the submitted offset for an existing answer, so it stays
                // absent rather than being invented.
                assertNull(storedOffsetMinutes(connection, fixture.valueId))

                assertStoresAMomentWithItsOffset(connection, fixture)
                assertRefusesAnImpossibleOffset(connection, fixture)
                assertRefusesAnOffsetWithoutAMoment(connection, fixture)
            }
        }
    }

    @Test
    fun `a database with no date-time answers gains the same shape`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = instantMigrationVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                assertStoresAMomentWithItsOffset(connection, fixture)
                assertRefusesAnImpossibleOffset(connection, fixture)
                assertRefusesAnOffsetWithoutAMoment(connection, fixture)
            }
        }
    }

    /** The same moment written at two offsets must store as one moment and keep both offsets. */
    private fun assertStoresAMomentWithItsOffset(connection: Connection, fixture: Fixture)
    {
        val moment = Instant.parse("2026-08-31T08:15:30Z")
        val atPlusTwo = UUID.randomUUID()
        val atMinusFive = UUID.randomUUID()

        insertMoment(connection, fixture, atPlusTwo, moment, offsetMinutes = 120)
        insertMoment(connection, fixture, atMinusFive, moment, offsetMinutes = -300)

        assertEquals(moment, storedMoment(connection, atPlusTwo))
        assertEquals(moment, storedMoment(connection, atMinusFive))
        assertEquals(120, storedOffsetMinutes(connection, atPlusTwo))
        assertEquals(-300, storedOffsetMinutes(connection, atMinusFive))
    }

    private fun assertRefusesAnImpossibleOffset(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertMoment(
                connection, fixture, UUID.randomUUID(),
                Instant.parse("2026-08-31T08:15:30Z"), offsetMinutes = 1500,
            )
        }
        assertTrue(
            refused.message?.contains("ck_field_value_datetime_offset") == true,
            "Expected the offset rule to refuse the row: ${refused.message}",
        )
    }

    private fun assertRefusesAnOffsetWithoutAMoment(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertMoment(connection, fixture, UUID.randomUUID(), moment = null, offsetMinutes = 120)
        }
        assertTrue(
            refused.message?.contains("ck_field_value_datetime_offset") == true,
            "Expected the offset rule to refuse an offset with no moment: ${refused.message}",
        )
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
        val valueId: UUID = UUID.randomUUID()
    }

    private fun withPostgres(block: (DateTimeInstantPostgreSQLContainer) -> Unit)
    {
        val postgres = DateTimeInstantPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_datetime_instant_test")
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

    private fun flyway(postgres: DateTimeInstantPostgreSQLContainer, target: String? = null): Flyway
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
            statement.setString(3, "REG-PROCESS-MOMENT")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', 'observed-at', 'PUBLISHED', ?, ?)
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
            VALUES (?, ?, 1, 'DATE_TIME', 'Observed at', ?)
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
            statement.setObject(1, fixture.bindingId)
            statement.setObject(2, fixture.schemaVersionId)
            statement.setObject(3, fixture.fieldContractId)
            statement.setObject(4, fixture.fieldDefinitionId)
            statement.executeUpdate()
        }

        insertAssignment(connection, fixture, fixture.resourceId, fixture.assignmentId)
    }

    private fun insertAssignment(
        connection: Connection,
        fixture: Fixture,
        resourceId: UUID,
        assignmentId: UUID = UUID.randomUUID(),
    ): UUID
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
        return assignmentId
    }

    /** Writes the released naive form, which is all the schema can express before the migration. */
    private fun insertNaiveDateTimeValue(connection: Connection, fixture: Fixture, naiveReading: String)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_value (id, schema_assignment_id, schema_field_binding_id,
                                     field_contract_id, resource_type, resource_id, value_type,
                                     datetime_value, provenance, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'EXCHANGE', ?, 'DATE_TIME', CAST(? AS timestamp), 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.valueId)
            statement.setObject(2, fixture.assignmentId)
            statement.setObject(3, fixture.bindingId)
            statement.setObject(4, fixture.fieldContractId)
            statement.setObject(5, fixture.resourceId)
            statement.setString(6, naiveReading)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    /**
     * One value row per assignment and contract is already an invariant, so each moment written here
     * gets its own assignment against its own resource.
     */
    private fun insertMoment(
        connection: Connection,
        fixture: Fixture,
        id: UUID,
        moment: Instant?,
        offsetMinutes: Int,
    )
    {
        val now = Timestamp.from(Instant.now())
        val resourceId = UUID.randomUUID()
        val assignmentId = insertAssignment(connection, fixture, resourceId)

        connection.prepareStatement(
            """
            INSERT INTO field_value (id, schema_assignment_id, schema_field_binding_id,
                                     field_contract_id, resource_type, resource_id, value_type,
                                     datetime_value, datetime_offset_minutes, provenance,
                                     created_at, updated_at)
            VALUES (?, ?, ?, ?, 'EXCHANGE', ?, 'DATE_TIME', ?, ?, 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, assignmentId)
            statement.setObject(3, fixture.bindingId)
            statement.setObject(4, fixture.fieldContractId)
            statement.setObject(5, resourceId)
            statement.setObject(6, moment?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
            statement.setInt(7, offsetMinutes)
            statement.setTimestamp(8, now)
            statement.setTimestamp(9, now)
            statement.executeUpdate()
        }
    }

    private fun storedMoment(connection: Connection, valueId: UUID): Instant? =
        connection.prepareStatement("SELECT datetime_value FROM field_value WHERE id = ?").use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null
                else rows.getObject(1, OffsetDateTime::class.java)?.toInstant()
            }
        }

    private fun storedOffsetMinutes(connection: Connection, valueId: UUID): Int? =
        connection.prepareStatement(
            "SELECT datetime_offset_minutes FROM field_value WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null
                else rows.getObject(1, Integer::class.java)?.toInt()
            }
        }
}
