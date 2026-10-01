package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeMetadataSeparationTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `an Exchange keeps its own Schema Assignment and answers while a request on it is answered, submitted, and closed`()
    {
        lateinit var exchangeAssignmentId: UUID
        val request = support.fieldRequest(prepare = { connection, prepared ->
            exchangeAssignmentId = assignExchangeMetadata(connection, prepared)
        })
        val before = exchangeMetadata(exchangeAssignmentId)
        val services = runtime.build(request.requestId)

        support.answerField(services, request, "Answer given to the request", "answer-field")
        support.assent(services, request, "assent")
        val submitted = support.submit(services, request, "submit")

        assertEquals(InformationRequestState.CLOSED, submitted.request.state)
        assertEquals(before, exchangeMetadata(exchangeAssignmentId))
        dataSource.connection.use { connection ->
            assertEquals(
                "Exchange metadata value",
                queryString(connection, "SELECT text_value FROM field_value WHERE schema_assignment_id = ?", exchangeAssignmentId),
            )
            assertEquals(
                "EXCHANGE",
                queryString(connection, "SELECT resource_type FROM schema_assignment WHERE id = ?", exchangeAssignmentId),
            )
            assertEquals(
                1,
                queryInt(connection, "SELECT COUNT(*) FROM information_request WHERE exchange_id = ?", request.runtime.exchangeId),
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    """
                    SELECT COUNT(*) FROM information_request_submission_item item
                    JOIN field_value_revision revision ON revision.id = item.field_value_revision_id
                    WHERE revision.schema_assignment_id = ?
                    """.trimIndent(),
                    exchangeAssignmentId,
                ),
            )
            assertEquals(
                "INFORMATION_REQUEST",
                queryString(
                    connection,
                    """
                    SELECT assignment.resource_type FROM information_request_submission_item item
                    JOIN field_value_revision revision ON revision.id = item.field_value_revision_id
                    JOIN schema_assignment assignment ON assignment.id = revision.schema_assignment_id
                    WHERE item.information_request_requirement_id = ?
                    """.trimIndent(),
                    request.answers.requirementId,
                ),
            )
        }
    }

    private fun assignExchangeMetadata(connection: Connection, prepared: ConformanceRequest): UUID
    {
        val now = prepared.runtime.template.now
        val schemaId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val bindingId = UUID.randomUUID()
        val assignmentId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        val valueId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                                           target_resource_type, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Exchange data', 'EXCHANGE', 'PUBLISHED', ?, ?)
            """.trimIndent(),
            schemaId,
            prepared.runtime.template.organizationId,
            "exchange-data-${schemaId.toString().take(8)}",
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            versionId,
            schemaId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, field_definition_id, display_order,
                                              is_required, is_read_only, visibility)
            VALUES (?, ?, ?, ?, 0, FALSE, FALSE, 'INTERNAL')
            """.trimIndent(),
            bindingId,
            versionId,
            prepared.answers.fieldContractId,
            prepared.answers.fieldDefinitionId,
        )
        execute(
            connection,
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id,
                                           assignment_source, assigned_by_principal_kind, assigned_by_principal_id, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', 'USER', ?, ?)
            """.trimIndent(),
            assignmentId,
            prepared.runtime.exchangeId,
            versionId,
            prepared.runtime.template.organizationId,
            prepared.runtime.template.userId,
            now,
        )
        execute(
            connection,
            "INSERT INTO field_value_set (id, schema_assignment_id, set_kind, created_at, updated_at) VALUES (?, ?, 'ROOT', ?, ?)",
            setId,
            assignmentId,
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO field_value (id, field_value_set_id, schema_assignment_id, schema_field_binding_id, field_contract_id,
                                     resource_type, resource_id, value_type, text_value, provenance, created_at, updated_at,
                                     updated_by_principal_kind, updated_by_principal_id)
            VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, 'SHORT_TEXT', 'Exchange metadata value', 'USER', ?, ?, 'USER', ?)
            """.trimIndent(),
            valueId,
            setId,
            assignmentId,
            bindingId,
            prepared.answers.fieldContractId,
            prepared.runtime.exchangeId,
            now,
            now,
            prepared.runtime.template.userId,
        )
        return assignmentId
    }

    private fun exchangeMetadata(assignmentId: UUID): String? =
        dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT concat_ws('|', assignment.resource_type, assignment.resource_id, assignment.schema_version_id,
                                 value_set.id, value_set.revision, value_set.updated_at, value.id, value.text_value,
                                 value.updated_at, (SELECT COUNT(*) FROM field_value_revision WHERE schema_assignment_id = assignment.id))
                FROM schema_assignment assignment
                JOIN field_value_set value_set ON value_set.schema_assignment_id = assignment.id
                JOIN field_value value ON value.field_value_set_id = value_set.id
                WHERE assignment.id = ?
                """.trimIndent(),
                assignmentId,
            )
        }
}
