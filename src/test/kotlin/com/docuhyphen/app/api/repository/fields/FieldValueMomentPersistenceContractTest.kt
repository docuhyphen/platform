package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.service.fields.CanonicalDateTime
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

private class FieldValueMomentPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<FieldValueMomentPostgreSQLContainer>(imageName)

class FieldValueMomentPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = FieldValueMomentPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_field_moment_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

/**
 * A date-time answer travels through the mapped entity, not only through the codec. This proves the
 * moment a responder submitted survives the round trip to PostgreSQL and back, that the offset they
 * submitted comes back with it, and that a reading which carried no offset stays that way.
 */
@QuarkusTest
@QuarkusTestResource(FieldValueMomentPostgreSQLResource::class, restrictToAnnotatedClass = true)
class FieldValueMomentPersistenceContractTest
{
    @Inject
    lateinit var repository: FieldValueRepository

    @Inject
    lateinit var dataSource: DataSource

    private val organizationId = UUID.fromString("11000000-0000-0000-0000-000000000001")
    private val fieldDefinitionId = UUID.fromString("12000000-0000-0000-0000-000000000001")
    private val fieldContractId = UUID.fromString("13000000-0000-0000-0000-000000000001")
    private val schemaDefinitionId = UUID.fromString("14000000-0000-0000-0000-000000000001")
    private val schemaVersionId = UUID.fromString("15000000-0000-0000-0000-000000000001")
    private val bindingId = UUID.fromString("16000000-0000-0000-0000-000000000001")

    @Test
    fun `a submitted moment and its offset survive the round trip through the entity`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)

        val moment = Instant.parse("2026-08-31T08:15:30Z")
        val writtenAtPlusTwo = store(CanonicalDateTime.parse("2026-08-31T10:15:30+02:00")!!.let {
            CanonicalFieldValue(
                type = FieldValueType.DATE_TIME,
                isEmpty = false,
                datetimeValue = it.instant,
                datetimeOffsetMinutes = it.offsetMinutes,
            )
        })
        val writtenAtMinusFive = store(CanonicalDateTime.parse("2026-08-31T03:15:30-05:00")!!.let {
            CanonicalFieldValue(
                type = FieldValueType.DATE_TIME,
                isEmpty = false,
                datetimeValue = it.instant,
                datetimeOffsetMinutes = it.offsetMinutes,
            )
        })

        val atPlusTwo = requireNotNull(repository.findById(writtenAtPlusTwo))
        val atMinusFive = requireNotNull(repository.findById(writtenAtMinusFive))

        assertEquals(moment, atPlusTwo.datetimeValue?.toInstant())
        assertEquals(moment, atMinusFive.datetimeValue?.toInstant())
        assertEquals(120, atPlusTwo.datetimeOffsetMinutes)
        assertEquals(-300, atMinusFive.datetimeOffsetMinutes)

        assertEquals(
            "2026-08-31T10:15:30+02:00",
            CanonicalValueCodec.toJson(atPlusTwo, emptyList()).toString().trim('"'),
        )
        assertEquals(
            "2026-08-31T03:15:30-05:00",
            CanonicalValueCodec.toJson(atMinusFive, emptyList()).toString().trim('"'),
        )
    }

    @Test
    fun `a reading with no offset comes back as a UTC moment with no offset`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)

        val reading = requireNotNull(CanonicalDateTime.parse("2026-08-31T08:15:30"))
        val storedId = store(
            CanonicalFieldValue(
                type = FieldValueType.DATE_TIME,
                isEmpty = false,
                datetimeValue = reading.instant,
                datetimeOffsetMinutes = reading.offsetMinutes,
            ),
        )

        val stored = requireNotNull(repository.findById(storedId))

        assertEquals(Instant.parse("2026-08-31T08:15:30Z"), stored.datetimeValue?.toInstant())
        assertNull(stored.datetimeOffsetMinutes)
        assertEquals(
            "2026-08-31T08:15:30Z",
            CanonicalValueCodec.toJson(stored, emptyList()).toString().trim('"'),
        )
    }

    /** Each answer gets its own assignment because one value per set and contract is an invariant. */
    private fun store(canonical: CanonicalFieldValue): UUID
    {
        val resourceId = UUID.randomUUID()
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, resourceId)
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        return QuarkusTransaction.requiringNew().call {
            val value = FieldValue().apply {
                this.fieldValueSetId = valueSetId
                this.schemaAssignmentId = assignmentId
                this.schemaFieldBindingId = bindingId
                this.fieldContractId = this@FieldValueMomentPersistenceContractTest.fieldContractId
                this.resourceType = "EXCHANGE"
                this.resourceId = resourceId
            }
            CanonicalValueCodec.applyTo(value, canonical)
            repository.save(value).id
        }
    }

    private fun resetAndSeedFixtures(connection: Connection)
    {
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM field_value_selection")
            statement.executeUpdate("DELETE FROM field_value")
            statement.executeUpdate("DELETE FROM field_value_set")
            statement.executeUpdate("DELETE FROM schema_assignment")
            statement.executeUpdate("DELETE FROM schema_field_binding")
            statement.executeUpdate("DELETE FROM schema_version")
            statement.executeUpdate("DELETE FROM schema_definition")
            statement.executeUpdate("DELETE FROM field_contract")
            statement.executeUpdate("DELETE FROM field_definition")
        }

        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete,
                                      created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setString(2, "Process Owner Org")
            statement.setString(3, "REG-PROCESS-PERSISTED")
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
            statement.setObject(1, fieldDefinitionId)
            statement.setObject(2, organizationId)
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
            statement.setObject(1, fieldContractId)
            statement.setObject(2, fieldDefinitionId)
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
            statement.setObject(1, schemaDefinitionId)
            statement.setObject(2, organizationId)
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
            statement.setObject(1, schemaVersionId)
            statement.setObject(2, schemaDefinitionId)
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
            statement.setObject(1, bindingId)
            statement.setObject(2, schemaVersionId)
            statement.setObject(3, fieldContractId)
            statement.setObject(4, fieldDefinitionId)
            statement.executeUpdate()
        }
    }

    private fun insertRootValueSet(connection: Connection, valueSetId: UUID, assignmentId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO field_value_set (id, schema_assignment_id, set_kind, created_at, updated_at)
            VALUES (?, ?, 'ROOT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, valueSetId)
            statement.setObject(2, assignmentId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }

    private fun insertAssignment(connection: Connection, assignmentId: UUID, resourceId: UUID)
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
            statement.setObject(3, schemaVersionId)
            statement.setObject(4, organizationId)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }
}
