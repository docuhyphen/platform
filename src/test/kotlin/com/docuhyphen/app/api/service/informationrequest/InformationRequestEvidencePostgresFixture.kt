package com.docuhyphen.app.api.service.informationrequest

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class InformationRequestEvidencePostgresFixture(
    private val requestState: String = "ISSUED",
    private val exchangeStatus: String = "ACCEPTED_STARTED",
)
{
    val organizationId: UUID = UUID.randomUUID()
    val userId: UUID = UUID.randomUUID()
    val exchangeId: UUID = UUID.randomUUID()
    val definitionId: UUID = UUID.randomUUID()
    val templateVersionId: UUID = UUID.randomUUID()
    val sectionId: UUID = UUID.randomUUID()
    val templateRequirementId: UUID = UUID.randomUUID()
    val templateBindingId: UUID = UUID.randomUUID()
    val evidencePolicyId: UUID = UUID.randomUUID()
    val requestId: UUID = UUID.randomUUID()
    val otherRequestId: UUID = UUID.randomUUID()
    val requirementId: UUID = UUID.randomUUID()
    val otherRequestRequirementId: UUID = UUID.randomUUID()

    fun insertInto(dataSource: DataSource): InformationRequestEvidencePostgresFixture
    {
        dataSource.connection.use { connection -> insertInto(connection) }
        return this
    }

    private fun insertInto(connection: Connection)
    {
        val now = Timestamp.from(Instant.now())
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
        execute(
            connection,
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
            userId,
            now,
            "evidence-owner-${userId.toString().take(8)}@process.test",
        )
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Process collection', ?)
            """.trimIndent(),
            exchangeId,
            organizationId,
            userId,
            now,
            now,
            exchangeStatus,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern', 'PUBLISHED', ?, ?)
            """.trimIndent(),
            definitionId,
            organizationId,
            "collection-${definitionId.toString().take(8)}",
            now,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_version (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, 1, 'DRAFT', ?)
            """.trimIndent(),
            templateVersionId,
            definitionId,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_section (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, 'records', 1, 'Records')
            """.trimIndent(),
            sectionId,
            templateVersionId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, 'supporting-record', 'DOCUMENT', ?)
            """.trimIndent(),
            templateRequirementId,
            definitionId,
            now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy)
            VALUES (?, ?, ?, ?, ?, 1, 'Provide the supporting record', 'PROVIDE', 'REQUIRED',
                    'CONTRIBUTOR', 'NOT_REQUIRED')
            """.trimIndent(),
            templateBindingId,
            templateVersionId,
            definitionId,
            templateRequirementId,
            sectionId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_evidence_policy
                (id, template_binding_id, template_version_id, minimum_file_count, maximum_file_count,
                 issuer_requirement, jurisdiction_requirement, language_requirement, issue_date_requirement,
                 expiry_date_requirement, coverage_period_requirement, certification_requirement,
                 signature_requirement, coverage_continuity_required, waiver_policy, conformance_policy)
            VALUES (?, ?, ?, 1, 3, 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED',
                    'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', FALSE,
                    'NOT_PERMITTED', 'CONFORMANCE_REQUIRED')
            """.trimIndent(),
            evidencePolicyId,
            templateBindingId,
            templateVersionId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), version.id, required.capability_key, 1
            FROM information_request_template_version version
                     CROSS JOIN request_template_required_capabilities(version.id) required
            WHERE version.id = ?
            """.trimIndent(),
            templateVersionId,
        )
        execute(
            connection,
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
            now,
            userId,
            templateVersionId,
        )
        insertRequest(connection, requestId, now)
        insertRequest(connection, otherRequestId, now)
        insertRuntimeRequirement(connection, requirementId, requestId, now)
        insertRuntimeRequirement(connection, otherRequestRequirementId, otherRequestId, now)
    }

    private fun insertRequest(connection: Connection, id: UUID, now: Timestamp)
    {
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                 state, gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, ?, TRUE, 1, 1, ?, ?, CASE WHEN ? = 'DRAFT' THEN NULL ELSE ?::timestamptz END)
            """.trimIndent(),
            id,
            exchangeId,
            templateVersionId,
            organizationId,
            requestState,
            now,
            now,
            requestState,
            now,
        )
    }

    private fun insertRuntimeRequirement(connection: Connection, id: UUID, request: UUID, now: Timestamp)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path, created_at)
            VALUES (?, ?, ?, ?, ?, 'root', ?)
            """.trimIndent(),
            id,
            request,
            templateVersionId,
            templateRequirementId,
            templateBindingId,
            now,
        )
    }

    private fun execute(connection: Connection, sql: String, vararg values: Any)
    {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }
}
