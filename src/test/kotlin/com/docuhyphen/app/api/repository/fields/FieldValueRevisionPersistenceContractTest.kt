package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.FieldValueRevisionSelection
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.fields.FieldValueSetETag
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import javax.sql.DataSource

private class FieldValueRevisionPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<FieldValueRevisionPostgreSQLContainer>(imageName)

class FieldValueRevisionPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = FieldValueRevisionPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_field_revision_test")
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
 * A recorded revision travels through the mapped entity, not only through the service. This proves
 * the typed answer and the canonical authorship survive the round trip to PostgreSQL and back, that
 * the database refuses to let a recorded revision be rewritten, and that history outlives the live
 * answer it records: removing a Schema Assignment removes the answer, its set, and the assignment,
 * while what was answered remains.
 *
 * The count of changes a set of answers has been through travels the same route, and the database
 * refuses to let that count move backwards no matter which layer asks. Two callers changing the same
 * set at the same moment are held apart by the locking read, so the second decides against what the
 * first committed rather than against the state they both started from.
 */
@QuarkusTest
@QuarkusTestResource(FieldValueRevisionPostgreSQLResource::class, restrictToAnnotatedClass = true)
class FieldValueRevisionPersistenceContractTest
{
    /** How long the first caller keeps its lock, so the second reaches its own read while held. */
    private val lockHoldMillis = 400L

    @Inject
    lateinit var revisionRepository: FieldValueRevisionRepository

    @Inject
    lateinit var revisionSelectionRepository: FieldValueRevisionSelectionRepository

    @Inject
    lateinit var valueSetRepository: FieldValueSetRepository

    @Inject
    lateinit var dataSource: DataSource

    private val organizationId = UUID.fromString("21000000-0000-0000-0000-000000000001")
    private val fieldDefinitionId = UUID.fromString("22000000-0000-0000-0000-000000000001")
    private val fieldContractId = UUID.fromString("23000000-0000-0000-0000-000000000001")
    private val schemaDefinitionId = UUID.fromString("24000000-0000-0000-0000-000000000001")
    private val schemaVersionId = UUID.fromString("25000000-0000-0000-0000-000000000001")
    private val bindingId = UUID.fromString("26000000-0000-0000-0000-000000000001")
    private val participantId = UUID.fromString("27000000-0000-0000-0000-000000000001")

    @Test
    fun `a recorded answer and its authorship survive the round trip through the entity`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        val recordedAt = Timestamp.from(Instant.parse("2026-08-31T08:15:30Z"))
        val revisionId = QuarkusTransaction.requiringNew().call {
            val saved = revisionRepository.save(
                revision(assignmentId, valueSetId, recordedAt, revisionNumber = 1),
            )
            revisionSelectionRepository.save(
                FieldValueRevisionSelection().apply {
                    this.fieldValueRevisionId = saved.id
                    this.optionCode = "chosen-option"
                    this.displayOrder = 0
                },
            )
            saved.id
        }

        val stored = requireNotNull(revisionRepository.findById(revisionId))
        assertEquals(1, stored.revisionNumber)
        assertEquals("Recorded answer", stored.textValue)
        assertEquals(FieldValueProvenance.USER, stored.provenance)
        assertEquals(
            PrincipalKind.PARTICIPANT, stored.recordedByPrincipalKind,
            "A participant is expressible as an author",
        )
        assertEquals(participantId, stored.recordedByPrincipalId)
        assertNull(
            stored.recordedByAppUserId,
            "A participant never occupies the registered-user key",
        )
        assertEquals(recordedAt.toInstant(), stored.recordedAt.toInstant())
        assertEquals(
            listOf("chosen-option"),
            revisionSelectionRepository.findByRevision(revisionId).map { it.optionCode },
        )
        assertEquals(stored.id, revisionRepository.findLatest(valueSetId, fieldContractId)?.id)
    }

    @Test
    fun `the database refuses to rewrite a recorded revision`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        val revisionId = QuarkusTransaction.requiringNew().call {
            revisionRepository.save(
                revision(assignmentId, valueSetId, Timestamp.from(Instant.now()), revisionNumber = 1),
            ).id
        }

        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().call {
                val stored = requireNotNull(revisionRepository.findById(revisionId))
                stored.textValue = "Rewritten answer"
                revisionRepository.update(stored)
            }
        }

        assertEquals(
            "Recorded answer", requireNotNull(revisionRepository.findById(revisionId)).textValue,
            "The refused rewrite must leave the recorded answer as it was",
        )
    }

    @Test
    fun `history outlives the answer, the value set, and the assignment it records`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        val revisionId = QuarkusTransaction.requiringNew().call {
            revisionRepository.save(
                revision(assignmentId, valueSetId, Timestamp.from(Instant.now()), revisionNumber = 1),
            ).id
        }

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM field_value_set WHERE id = '$valueSetId'")
                statement.executeUpdate("DELETE FROM schema_assignment WHERE id = '$assignmentId'")
            }
        }

        val surviving = requireNotNull(revisionRepository.findById(revisionId)) {
            "What a resource once answered stays part of the record"
        }
        assertEquals(valueSetId, surviving.fieldValueSetId)
        assertEquals(assignmentId, surviving.schemaAssignmentId)
        assertTrue(revisionRepository.findByAssignment(assignmentId).isNotEmpty())
    }

    @Test
    fun `the count of changes a set has been through survives the round trip`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        // Each step reads in its own transaction, so a later assertion never reads an earlier
        // step's cached copy of the set instead of the committed row.
        assertEquals(
            1L,
            QuarkusTransaction.requiringNew().call { requireNotNull(valueSetRepository.findRoot(assignmentId)).revision },
            "A set that has never changed is in its first state",
        )

        QuarkusTransaction.requiringNew().call {
            val set = requireNotNull(valueSetRepository.findRoot(assignmentId))
            set.revision += 1
            valueSetRepository.update(set)
        }

        assertEquals(
            2L,
            QuarkusTransaction.requiringNew().call { requireNotNull(valueSetRepository.findById(valueSetId)).revision },
        )
    }

    @Test
    fun `the database refuses to move a set back to an earlier state`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        QuarkusTransaction.requiringNew().call {
            val set = requireNotNull(valueSetRepository.findById(valueSetId))
            set.revision = 5
            valueSetRepository.update(set)
        }

        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().call {
                val set = requireNotNull(valueSetRepository.findById(valueSetId))
                set.revision = 4
                valueSetRepository.update(set)
            }
        }

        assertEquals(
            5L, requireNotNull(valueSetRepository.findById(valueSetId)).revision,
            "A validator already handed out must never become current again",
        )
    }

    @Test
    fun `two saves arriving together are serialized by the locking read`()
    {
        dataSource.connection.use(::resetAndSeedFixtures)
        val assignmentId = UUID.randomUUID()
        val valueSetId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            insertAssignment(connection, assignmentId, UUID.randomUUID())
            insertRootValueSet(connection, valueSetId, assignmentId)
        }

        // Both callers read the same state and decide to change it, which is the ordinary case of one
        // person saving while another already has.
        val stateBothRead = FieldsPrecondition.ExpectedRevision(
            FieldValueSetETag.of(
                QuarkusTransaction.requiringNew().call { requireNotNull(valueSetRepository.findRoot(assignmentId)) },
            ),
        )

        val firstHasLocked = CountDownLatch(1)
        val outcomes = ConcurrentLinkedQueue<Result<Long>>()
        val first = Thread {
            outcomes += runCatching {
                QuarkusTransaction.requiringNew().call {
                    val set = requireNotNull(valueSetRepository.findRootForUpdate(assignmentId))
                    firstHasLocked.countDown()
                    // Held long enough for the second caller to reach its own locking read.
                    Thread.sleep(lockHoldMillis)
                    stateBothRead.requireSatisfiedBy(FieldValueSetETag.of(set))
                    set.revision += 1
                    valueSetRepository.update(set).revision
                }
            }
        }
        val second = Thread {
            firstHasLocked.await()
            outcomes += runCatching {
                QuarkusTransaction.requiringNew().call {
                    val set = requireNotNull(valueSetRepository.findRootForUpdate(assignmentId))
                    stateBothRead.requireSatisfiedBy(FieldValueSetETag.of(set))
                    set.revision += 1
                    valueSetRepository.update(set).revision
                }
            }
        }

        first.start()
        second.start()
        first.join()
        second.join()

        assertEquals(
            1, outcomes.count { it.isSuccess },
            "Exactly one of two saves that named the same state may store its change",
        )
        val refused = outcomes.single { it.isFailure }.exceptionOrNull()
        assertTrue(
            generateSequence(refused) { it.cause }.any { it is FieldsPreconditionException },
            "The save that lost the race is refused for naming a state the answers have moved past",
        )
        assertEquals(
            2L, QuarkusTransaction.requiringNew().call { requireNotNull(valueSetRepository.findById(valueSetId)).revision },
            "One change stored means one step forward, never two states sharing one validator",
        )
    }

    private fun revision(
        assignmentId: UUID,
        valueSetId: UUID,
        recordedAt: Timestamp,
        revisionNumber: Int,
    ) = FieldValueRevision().apply {
        this.fieldValueId = UUID.randomUUID()
        this.fieldValueSetId = valueSetId
        this.schemaAssignmentId = assignmentId
        this.schemaFieldBindingId = bindingId
        this.fieldContractId = this@FieldValueRevisionPersistenceContractTest.fieldContractId
        this.revisionNumber = revisionNumber
        this.valueType = FieldValueType.SHORT_TEXT
        this.textValue = "Recorded answer"
        this.isCleared = false
        this.provenance = FieldValueProvenance.USER
        this.recordedByPrincipalKind = PrincipalKind.PARTICIPANT
        this.recordedByPrincipalId = participantId
        this.recordedBySessionRef = "session-reference"
        this.recordedAt = recordedAt
    }

    private fun resetAndSeedFixtures(connection: Connection)
    {
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM field_value_revision_selection")
            statement.executeUpdate("DELETE FROM field_value_revision")
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
            statement.setString(3, "REG-PROCESS-REVISION")
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
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Recorded note', ?)
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

    private fun insertRootValueSet(connection: Connection, valueSetId: UUID, assignmentId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_value_set (id, schema_assignment_id, set_kind, occurrence_path,
                                         created_at, updated_at)
            VALUES (?, ?, 'ROOT', NULL, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, valueSetId)
            statement.setObject(2, assignmentId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }
}
