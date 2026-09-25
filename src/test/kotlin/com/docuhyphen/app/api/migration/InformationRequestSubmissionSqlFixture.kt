package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

internal class SubmissionPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<SubmissionPostgreSQLContainer>(imageName)

internal fun withSubmissionPostgres(block: (SubmissionPostgreSQLContainer) -> Unit)
{
    val postgres = SubmissionPostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("docuhyphen_submission_test")
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

internal fun submissionFlyway(postgres: SubmissionPostgreSQLContainer, target: String? = null): Flyway
{
    val configuration = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
    target?.let(configuration::target)
    return configuration.load()
}

internal fun refusedBy(connection: Connection, expected: String, block: () -> Unit)
{
    connection.autoCommit = false
    val refusal = try
    {
        assertThrows<SQLException>(block)
    }
    finally
    {
        connection.rollback()
        connection.autoCommit = true
    }
    assertTrue(
        refusal.message.orEmpty().contains(expected),
        "Expected $expected to refuse this statement: ${refusal.message}",
    )
}

internal fun execute(connection: Connection, sql: String, vararg values: Any?): Int =
    connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

internal fun queryString(connection: Connection, sql: String, vararg values: Any?): String? =
    connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }

internal fun queryInt(connection: Connection, sql: String, vararg values: Any?): Int =
    connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { rows ->
            check(rows.next())
            rows.getInt(1)
        }
    }

internal fun queryStrings(connection: Connection, sql: String, vararg values: Any?): Set<String> =
    connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { rows ->
            val collected = mutableSetOf<String>()
            while (rows.next()) collected += rows.getString(1)
            collected
        }
    }

internal class SubmissionTemplateSqlFixture(private val connection: Connection)
{
    val now: Timestamp = Timestamp.from(Instant.now())
    val organizationId: UUID = UUID.randomUUID()
    val userId: UUID = UUID.randomUUID()
    val definitionId: UUID = UUID.randomUUID()
    val versionId: UUID = UUID.randomUUID()
    val sectionId: UUID = UUID.randomUUID()

    init
    {
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
            "submission-owner-${userId.toString().take(8)}@process.test",
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
            definitionId,
            organizationId,
            "collection-${definitionId.toString().take(8)}",
            now,
            now,
        )
        insertVersion(versionId, 1)
        insertSectionBeforeStages(sectionId, versionId, "records", 1)
    }

    fun insertVersion(id: UUID, number: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_version (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, ?, 'DRAFT', ?)
            """.trimIndent(),
            id,
            definitionId,
            number,
            now,
        )
    }

    fun insertSection(id: UUID, version: UUID, key: String, order: Int, stageKey: String? = null)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_section
                (id, template_version_id, section_key, display_order, title, submission_stage_key)
            VALUES (?, ?, ?, ?, 'Records', ?)
            """.trimIndent(),
            id,
            version,
            key,
            order,
            stageKey,
        )
    }

    fun insertSectionBeforeStages(id: UUID, version: UUID, key: String, order: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_section (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, ?, ?, 'Records')
            """.trimIndent(),
            id,
            version,
            key,
            order,
        )
    }

    fun insertRequirement(id: UUID, key: String, type: String)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            definitionId,
            key,
            type,
            now,
        )
    }

    @Suppress("LongParameterList")
    fun insertBinding(
        id: UUID,
        version: UUID,
        requirementId: UUID,
        section: UUID,
        order: Int,
        contributorRole: String = "CONTRIBUTOR",
        responseMode: String = "PROVIDE",
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy)
            VALUES (?, ?, ?, ?, ?, ?, 'Provide the recorded item', ?, 'REQUIRED', ?, 'NOT_REQUIRED')
            """.trimIndent(),
            id,
            version,
            definitionId,
            requirementId,
            section,
            order,
            responseMode,
            contributorRole,
        )
    }

    fun insertEvidencePolicy(
        id: UUID,
        bindingId: UUID,
        version: UUID,
        waiverPolicy: String = "NOT_PERMITTED",
        conformancePolicy: String = "CONFORMANCE_REQUIRED",
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_evidence_policy
                (id, template_binding_id, template_version_id, minimum_file_count, maximum_file_count,
                 issuer_requirement, jurisdiction_requirement, language_requirement, issue_date_requirement,
                 expiry_date_requirement, coverage_period_requirement, certification_requirement,
                 signature_requirement, coverage_continuity_required, waiver_policy, conformance_policy)
            VALUES (?, ?, ?, 1, 3, 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED',
                    'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', FALSE, ?, ?)
            """.trimIndent(),
            id,
            bindingId,
            version,
            waiverPolicy,
            conformancePolicy,
        )
    }

    fun insertDisposition(bindingId: UUID, version: UUID, disposition: String)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_binding_disposition
                (id, template_binding_id, template_version_id, disposition)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            bindingId,
            version,
            disposition,
        )
    }

    @Suppress("LongParameterList")
    fun insertAttestationPolicy(
        id: UUID,
        bindingId: UUID,
        version: UUID,
        ordering: String = "ANY_ORDER",
        minimumAssents: Int = 1,
        strength: String = "VERIFIED_CONTACT",
        validityHours: Int? = null,
        signatureReference: String = "NOT_ACCEPTED",
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_attestation_policy
                (id, template_version_id, template_binding_id, ordering, minimum_assent_count,
                 minimum_authentication_strength, validity_hours, external_signature_reference)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            version,
            bindingId,
            ordering,
            minimumAssents,
            strength,
            validityHours,
            signatureReference,
        )
    }

    fun insertAttestationRole(policyId: UUID, version: UUID, role: String, position: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_attestation_role
                (id, template_version_id, attestation_policy_id, role_key, position)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            version,
            policyId,
            role,
            position,
        )
    }

    fun recordDerivedCapabilities(version: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), ?, required.capability_key, 1
            FROM request_template_required_capabilities(?) required
            """.trimIndent(),
            version,
            version,
        )
    }

    fun publish(version: UUID)
    {
        execute(
            connection,
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
            now,
            userId,
            version,
        )
    }

    fun derivedCapabilities(version: UUID): Set<String> =
        queryStrings(connection, "SELECT capability_key FROM request_template_required_capabilities(?)", version)

    fun recordedCapabilities(version: UUID): Set<String> =
        queryStrings(
            connection,
            "SELECT capability_key FROM information_request_template_version_capability WHERE template_version_id = ?",
            version,
        )
}
