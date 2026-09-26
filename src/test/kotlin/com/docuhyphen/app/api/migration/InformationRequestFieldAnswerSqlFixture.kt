package com.docuhyphen.app.api.migration

import java.sql.Connection
import java.util.UUID

internal class FieldAnswerSqlFixture(
    private val connection: Connection,
    private val template: SubmissionTemplateSqlFixture,
    reviewed: Boolean = false,
    reusePurpose: String? = null,
)
{
    val fieldDefinitionId: UUID = UUID.randomUUID()
    val fieldContractId: UUID = UUID.randomUUID()
    val schemaDefinitionId: UUID = UUID.randomUUID()
    val schemaVersionId: UUID = UUID.randomUUID()
    val schemaFieldBindingId: UUID = UUID.randomUUID()
    val templateRequirementId: UUID = UUID.randomUUID()
    val bindingId: UUID = UUID.randomUUID()
    val requirementId: UUID = UUID.randomUUID()
    val revisionId: UUID = UUID.randomUUID()
    val assignmentId: UUID = UUID.randomUUID()
    val fieldValueId: UUID = UUID.randomUUID()
    val fieldValueRevisionId: UUID = UUID.randomUUID()
    val responseId: UUID = UUID.randomUUID()
    var valueSetId: UUID = UUID.randomUUID()
        private set

    init
    {
        val now = template.now
        execute(
            connection,
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            fieldDefinitionId,
            template.organizationId,
            "recorded-note-${fieldDefinitionId.toString().take(8)}",
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label, created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Recorded note', ?)
            """.trimIndent(),
            fieldContractId,
            fieldDefinitionId,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                                           target_resource_type, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Process data', 'INFORMATION_REQUEST', 'PUBLISHED', ?, ?)
            """.trimIndent(),
            schemaDefinitionId,
            template.organizationId,
            "process-data-${schemaDefinitionId.toString().take(8)}",
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            schemaVersionId,
            schemaDefinitionId,
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
            schemaFieldBindingId,
            schemaVersionId,
            fieldContractId,
            fieldDefinitionId,
        )
        execute(
            connection,
            "UPDATE information_request_template_version SET schema_version_id = ? WHERE id = ?",
            schemaVersionId,
            template.versionId,
        )
        template.insertRequirement(templateRequirementId, "recorded-note", "FIELD")
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id, template_section_id,
                 display_order, prompt, response_mode, requiredness, contributor_role, review_policy,
                 collected_field_definition_id)
            VALUES (?, ?, ?, ?, ?, 9, 'Record the note', 'PROVIDE', 'REQUIRED', 'CONTRIBUTOR', ?, ?)
            """.trimIndent(),
            bindingId,
            template.versionId,
            template.definitionId,
            templateRequirementId,
            template.sectionId,
            if (reviewed) "REQUIRED" else "NOT_REQUIRED",
            fieldDefinitionId,
        )
        reusePurpose?.let {
            execute(connection, "UPDATE information_request_template_version SET fact_reuse_purpose_key = ? WHERE id = ?", it, template.versionId)
        }
        template.insertDisposition(bindingId, template.versionId, "PROVIDED")
    }

    fun materialize(runtime: SubmissionRuntimeSqlFixture)
    {
        val now = template.now
        runtime.insertRequirementOccurrence(requirementId, revisionId, templateRequirementId, bindingId)
        execute(
            connection,
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id,
                                           assignment_source, assigned_by_principal_kind, assigned_by_principal_id, assigned_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, 'ORGANIZATION', ?, 'MANUAL', 'USER', ?, ?)
            """.trimIndent(),
            assignmentId,
            runtime.requestId,
            schemaVersionId,
            template.organizationId,
            template.userId,
            now,
        )
        valueSetId = queryString(
            connection,
            "SELECT id::text FROM field_value_set WHERE schema_assignment_id = ? AND set_kind = 'ROOT'",
            assignmentId,
        )?.let(UUID::fromString) ?: UUID.randomUUID().also { id ->
            execute(
                connection,
                """
                INSERT INTO field_value_set (id, schema_assignment_id, set_kind, created_at, updated_at)
                VALUES (?, ?, 'ROOT', ?, ?)
                """.trimIndent(),
                id,
                assignmentId,
                now,
                now,
            )
        }
        execute(
            connection,
            """
            INSERT INTO field_value (id, field_value_set_id, schema_assignment_id, schema_field_binding_id, field_contract_id,
                                     resource_type, resource_id, value_type, text_value, provenance, created_at, updated_at,
                                     updated_by_principal_kind, updated_by_principal_id)
            VALUES (?, ?, ?, ?, ?, 'INFORMATION_REQUEST', ?, 'SHORT_TEXT', 'Recorded answer', 'USER', ?, ?, 'USER', ?)
            """.trimIndent(),
            fieldValueId,
            valueSetId,
            assignmentId,
            schemaFieldBindingId,
            fieldContractId,
            runtime.requestId,
            now,
            now,
            runtime.contributorUserId,
        )
        execute(
            connection,
            """
            INSERT INTO field_value_revision (id, field_value_id, field_value_set_id, schema_assignment_id, schema_field_binding_id,
                                              field_contract_id, revision_number, value_type, text_value, is_cleared, provenance,
                                              recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, 'SHORT_TEXT', 'Recorded answer', FALSE, 'USER', 'USER', ?, ?)
            """.trimIndent(),
            fieldValueRevisionId,
            fieldValueId,
            valueSetId,
            assignmentId,
            schemaFieldBindingId,
            fieldContractId,
            runtime.contributorUserId,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id, requirement_revision_id, occurrence_path,
                 disposition, field_value_set_id, response_revision, recorded_by_principal_kind, recorded_by_principal_id,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, 'root', 'PROVIDED', ?, 2, 'USER', ?, ?, ?)
            """.trimIndent(),
            responseId,
            runtime.requestId,
            requirementId,
            revisionId,
            valueSetId,
            runtime.contributorUserId,
            now,
            now,
        )
    }

    fun insertFieldItem(runtime: SubmissionRuntimeSqlFixture, itemId: UUID, packageId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_item
                (id, package_id, information_request_id, information_request_requirement_id, requirement_revision_id,
                 template_binding_id, requirement_key, requirement_type, occurrence_path, completeness_state, disposition,
                 response_id, response_revision, responded_by_principal_kind, responded_by_principal_id, field_value_set_id,
                 field_value_revision_id, item_hash_sha256)
            VALUES (?, ?, ?, ?, ?, ?, 'recorded-note', 'FIELD', 'root', 'COMPLETE', 'PROVIDED', ?, 2, 'USER', ?, ?, ?, ?)
            """.trimIndent(),
            itemId,
            packageId,
            runtime.requestId,
            requirementId,
            revisionId,
            bindingId,
            responseId,
            runtime.contributorUserId,
            valueSetId,
            fieldValueRevisionId,
            "f".repeat(64),
        )
    }
}
