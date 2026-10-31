package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class FieldValueBoundBindingContractTest {
    private val previousVersion = "163"

    @Test
    fun `a clean schema keeps no binding conflict report and refuses an answer that names no binding`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals(0, conflictReportTables(connection))
                val fields = BoundFields(connection)

                refusedBy(connection, "schema_field_binding_id") {
                    fields.insertValue(
                        UUID.randomUUID(),
                        fields.referenceContractV2Id,
                        null,
                        "Answer naming no binding"
                    )
                }
            }
        }
    }

    @Test
    fun `an upgrade removes answers the assigned version does not bind and points every other answer at its binding`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, previousVersion).migrate()

            lateinit var fields: BoundFields
            val boundAnswerId = UUID.randomUUID()
            val earlierContractAnswerId = UUID.randomUUID()
            val unboundAnswerId = UUID.randomUUID()
            val unpointedAnswerId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                fields = BoundFields(connection)
                fields.reportConflict()
                fields.insertValue(
                    boundAnswerId,
                    fields.referenceContractV2Id,
                    fields.referenceBindingId,
                    "Bound answer"
                )
                fields.insertValue(
                    earlierContractAnswerId,
                    fields.referenceContractV1Id,
                    null,
                    "Earlier contract answer"
                )
                fields.insertRevision(earlierContractAnswerId, fields.referenceContractV1Id, "Earlier contract answer")
                fields.insertValue(unboundAnswerId, fields.unboundContractId, null, null, "SINGLE_SELECT")
                execute(
                    connection,
                    "INSERT INTO field_value_selection (id, field_value_id, option_code, display_order) VALUES (?, ?, 'first-option', 0)",
                    UUID.randomUUID(),
                    unboundAnswerId,
                )
                fields.insertValue(unpointedAnswerId, fields.countContractId, null, "Answer missing its pointer")
            }

            submissionFlyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals(0, conflictReportTables(connection))
                assertEquals(
                    setOf(boundAnswerId.toString(), unpointedAnswerId.toString()),
                    queryStrings(
                        connection,
                        "SELECT id::text FROM field_value WHERE schema_assignment_id = ?",
                        fields.assignmentId
                    ),
                )
                assertEquals(
                    fields.referenceBindingId.toString(),
                    queryString(
                        connection,
                        "SELECT schema_field_binding_id::text FROM field_value WHERE id = ?",
                        boundAnswerId
                    ),
                )
                assertEquals(
                    fields.countBindingId.toString(),
                    queryString(
                        connection,
                        "SELECT schema_field_binding_id::text FROM field_value WHERE id = ?",
                        unpointedAnswerId
                    ),
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM field_value_selection WHERE field_value_id = ?",
                        unboundAnswerId
                    ),
                )
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM field_value_revision WHERE field_value_id = ?",
                        earlierContractAnswerId
                    ),
                    "What was once answered stays on record after the live answer is removed",
                )
                refusedBy(connection, "schema_field_binding_id") {
                    fields.insertValue(
                        UUID.randomUUID(),
                        fields.referenceContractV2Id,
                        null,
                        "Answer naming no binding",
                        into = connection,
                    )
                }
            }
        }
    }

    private fun conflictReportTables(connection: Connection): Int =
        queryInt(
            connection,
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'schema_field_binding_conflict'",
        )

    private class BoundFields(private val connection: Connection) {
        private val now: Timestamp = Timestamp.from(Instant.now())
        private val organizationId: UUID = UUID.randomUUID()
        private val referenceFieldId: UUID = UUID.randomUUID()
        private val unboundFieldId: UUID = UUID.randomUUID()
        private val countFieldId: UUID = UUID.randomUUID()
        private val schemaDefinitionId: UUID = UUID.randomUUID()
        private val schemaVersionId: UUID = UUID.randomUUID()
        private val rootSetId: UUID = UUID.randomUUID()
        private val resourceId: UUID = UUID.randomUUID()
        val referenceContractV1Id: UUID = UUID.randomUUID()
        val referenceContractV2Id: UUID = UUID.randomUUID()
        val unboundContractId: UUID = UUID.randomUUID()
        val countContractId: UUID = UUID.randomUUID()
        val referenceBindingId: UUID = UUID.randomUUID()
        val countBindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()

        init {
            execute(
                connection,
                """
                INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process Owner', ?, TRUE, TRUE, ?)
                """.trimIndent(),
                organizationId,
                "REG-${organizationId.toString().take(8)}",
                now,
            )
            insertField(referenceFieldId, "reference-code")
            insertField(unboundFieldId, "unbound-choice")
            insertField(countFieldId, "recorded-count")
            insertContract(referenceContractV1Id, referenceFieldId, 1, "SHORT_TEXT")
            insertContract(referenceContractV2Id, referenceFieldId, 2, "SHORT_TEXT")
            insertContract(unboundContractId, unboundFieldId, 1, "SINGLE_SELECT")
            insertContract(countContractId, countFieldId, 1, "SHORT_TEXT")
            execute(
                connection,
                """
                INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                                               target_resource_type, status, created_at, updated_at)
                VALUES (?, 'ORGANIZATION', ?, 'process', 'process-data', 'Process data', 'EXCHANGE', 'PUBLISHED', ?, ?)
                """.trimIndent(),
                schemaDefinitionId,
                organizationId,
                now,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at, created_at)
                VALUES (?, ?, 2, 'PUBLISHED', ?, ?)
                """.trimIndent(),
                schemaVersionId,
                schemaDefinitionId,
                now,
                now,
            )
            insertBinding(referenceBindingId, referenceContractV2Id, referenceFieldId, 0)
            insertBinding(countBindingId, countContractId, countFieldId, 1)
            execute(
                connection,
                """
                INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id,
                                               assignment_source, assigned_at)
                VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', ?)
                """.trimIndent(),
                assignmentId,
                resourceId,
                schemaVersionId,
                organizationId,
                now,
            )
            execute(
                connection,
                "INSERT INTO field_value_set (id, schema_assignment_id, set_kind, created_at, updated_at) VALUES (?, ?, 'ROOT', ?, ?)",
                rootSetId,
                assignmentId,
                now,
                now,
            )
        }

        fun insertValue(
            id: UUID,
            contractId: UUID,
            bindingId: UUID?,
            text: String?,
            type: String = "SHORT_TEXT",
            into: Connection = connection,
        ) {
            execute(
                into,
                """
                INSERT INTO field_value (id, field_value_set_id, schema_assignment_id, schema_field_binding_id,
                                         field_contract_id, resource_type, resource_id, value_type, text_value,
                                         provenance, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, ?, ?, 'USER', ?, ?)
                """.trimIndent(),
                id,
                rootSetId,
                assignmentId,
                bindingId,
                contractId,
                resourceId,
                type,
                text,
                now,
                now,
            )
        }

        fun insertRevision(valueId: UUID, contractId: UUID, text: String) {
            execute(
                connection,
                """
                INSERT INTO field_value_revision (id, field_value_id, field_value_set_id, schema_assignment_id,
                                                  field_contract_id, revision_number, value_type, text_value, is_cleared,
                                                  provenance, recorded_at)
                VALUES (?, ?, ?, ?, ?, 1, 'SHORT_TEXT', ?, FALSE, 'USER', ?)
                """.trimIndent(),
                UUID.randomUUID(),
                valueId,
                rootSetId,
                assignmentId,
                contractId,
                text,
                now,
            )
        }

        fun reportConflict() {
            execute(
                connection,
                """
                INSERT INTO schema_field_binding_conflict (id, schema_version_id, field_definition_id, field_contract_id,
                                                           binding_id, display_order, is_required, is_read_only,
                                                           visibility, resolution, detected_at)
                VALUES (?, ?, ?, ?, ?, 0, FALSE, FALSE, 'INTERNAL', 'REMOVED', ?)
                """.trimIndent(),
                UUID.randomUUID(),
                schemaVersionId,
                referenceFieldId,
                referenceContractV1Id,
                UUID.randomUUID(),
                now,
            )
        }

        private fun insertField(id: UUID, key: String) {
            execute(
                connection,
                """
                INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status, created_at, updated_at)
                VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'PUBLISHED', ?, ?)
                """.trimIndent(),
                id,
                organizationId,
                key,
                now,
                now,
            )
        }

        private fun insertContract(id: UUID, fieldId: UUID, version: Int, type: String) {
            execute(
                connection,
                """
                INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label, created_at)
                VALUES (?, ?, ?, ?, 'Recorded value', ?)
                """.trimIndent(),
                id,
                fieldId,
                version,
                type,
                now,
            )
        }

        private fun insertBinding(id: UUID, contractId: UUID, fieldId: UUID, order: Int) {
            execute(
                connection,
                """
                INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, field_definition_id,
                                                  display_order, is_required, is_read_only, visibility)
                VALUES (?, ?, ?, ?, ?, FALSE, FALSE, 'INTERNAL')
                """.trimIndent(),
                id,
                schemaVersionId,
                contractId,
                fieldId,
                order,
            )
        }
    }
}
