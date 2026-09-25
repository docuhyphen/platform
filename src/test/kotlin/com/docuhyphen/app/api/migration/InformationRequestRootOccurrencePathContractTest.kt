package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class RootOccurrencePathPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<RootOccurrencePathPostgreSQLContainer>(imageName)

/**
 * The root occurrence of an Information Request has one spelling, `root`. A Requirement, its
 * revision, and its response stored under the earlier `$` spelling are carried to `root`, and the
 * earlier spelling cannot be stored again.
 */
class InformationRequestRootOccurrencePathContractTest
{
    private val previousVersion = "126"

    @Test
    fun `a Requirement, revision, and response stored under the earlier spelling are carried to root`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = previousVersion).migrate()

            val ids = postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.occurrence(connection, "\$")
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("root", pathOf(connection, "information_request_requirement", ids.requirementId))
                assertEquals("root", pathOf(connection, "information_request_requirement_revision", ids.revisionId))
                assertEquals("root", pathOf(connection, "information_request_response", ids.responseId))
            }
        }
    }

    @Test
    fun `the earlier root spelling cannot be stored again`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                refused(connection, "ck_information_request_requirement_root_path") {
                    fixture.requirement(connection, UUID.randomUUID(), "\$")
                }

                val root = fixture.occurrence(connection, "root")
                assertEquals("root", pathOf(connection, "information_request_response", root.responseId))
            }
        }
    }

    // Support

    private data class Occurrence(val requirementId: UUID, val revisionId: UUID, val responseId: UUID)

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val templateVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val templateRequirementId: UUID = UUID.randomUUID()
        val templateBindingId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertTemplateVersion(connection, templateVersionId, definitionId)
            insertTemplateRequirement(connection, templateRequirementId, definitionId)
            insertTemplateSection(connection, sectionId, templateVersionId)
            insertTemplateBinding(connection, templateBindingId, templateVersionId, definitionId, templateRequirementId, sectionId)
            publishVersion(connection, templateVersionId, userId)
            insertRequest(connection, requestId, exchangeId, templateVersionId, organizationId)
        }

        fun requirement(connection: Connection, id: UUID, occurrencePath: String)
        {
            execute(
                connection,
                """
                INSERT INTO information_request_requirement
                    (id, information_request_id, source_template_version_id,
                     source_template_requirement_id, source_template_binding_id, occurrence_path, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id, requestId, templateVersionId, templateRequirementId, templateBindingId, occurrencePath,
                Timestamp.from(Instant.now()),
            )
        }

        fun occurrence(connection: Connection, occurrencePath: String): Occurrence
        {
            val requirementId = UUID.randomUUID()
            val revisionId = UUID.randomUUID()
            val responseId = UUID.randomUUID()

            requirement(connection, requirementId, occurrencePath)
            execute(
                connection,
                """
                INSERT INTO information_request_requirement_revision
                    (id, information_request_requirement_id, information_request_id, source_template_version_id,
                     source_template_requirement_id, source_template_binding_id, revision_number,
                     occurrence_path, configuration_hash_sha256)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                """.trimIndent(),
                revisionId, requirementId, requestId, templateVersionId, templateRequirementId, templateBindingId,
                occurrencePath, "0".repeat(64),
            )
            execute(
                connection,
                """
                INSERT INTO information_request_response
                    (id, information_request_id, information_request_requirement_id, requirement_revision_id,
                     occurrence_path, recorded_by_principal_kind, recorded_by_principal_id)
                VALUES (?, ?, ?, ?, ?, 'USER', ?)
                """.trimIndent(),
                responseId, requestId, requirementId, revisionId, occurrencePath, userId,
            )
            return Occurrence(requirementId, revisionId, responseId)
        }
    }

    private fun insertOrganization(connection: Connection, id: UUID) = execute(
        connection,
        """
        INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
        VALUES (?, 'Collection Process Owner', ?, TRUE, TRUE, ?)
        """.trimIndent(),
        id, "REG-${id.toString().take(8)}", Timestamp.from(Instant.now()),
    )

    private fun insertUser(connection: Connection, id: UUID) = execute(
        connection,
        """
        INSERT INTO app_user
            (id, is_active, created_date, email, email_verification_completed, is_temporary,
             sign_in_attempts, exchange_version, multifactor_authentication_type,
             is_password_temporary, email_mfa_fallback_enabled)
        VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
        """.trimIndent(),
        id, Timestamp.from(Instant.now()), "collection-owner-${id.toString().take(8)}@process.test",
    )

    private fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID) = execute(
        connection,
        """
        INSERT INTO exchange
            (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
             created_date, last_activity, description, initial_share_message, name, status)
        VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                'Process collection', 'ACCEPTED_STARTED')
        """.trimIndent(),
        id, organizationId, userId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
    )

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID) = execute(
        connection,
        """
        INSERT INTO information_request_template_definition
            (id, scope_kind, scope_org_id, namespace, template_key, display_name, status, created_at, updated_at)
        VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern', 'PUBLISHED', ?, ?)
        """.trimIndent(),
        id, organizationId, "collection-${id.toString().take(8)}", Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
    )

    private fun insertTemplateVersion(connection: Connection, id: UUID, definitionId: UUID) = execute(
        connection,
        """
        INSERT INTO information_request_template_version (id, template_definition_id, version_number, status, created_at)
        VALUES (?, ?, 1, 'DRAFT', ?)
        """.trimIndent(),
        id, definitionId, Timestamp.from(Instant.now()),
    )

    private fun insertTemplateSection(connection: Connection, id: UUID, versionId: UUID) = execute(
        connection,
        """
        INSERT INTO information_request_template_section (id, template_version_id, section_key, display_order, title)
        VALUES (?, ?, 'records', 1, 'Records')
        """.trimIndent(),
        id, versionId,
    )

    private fun insertTemplateRequirement(connection: Connection, id: UUID, definitionId: UUID) = execute(
        connection,
        """
        INSERT INTO information_request_template_requirement
            (id, template_definition_id, requirement_key, requirement_type, created_at)
        VALUES (?, ?, 'recorded-assertion', 'RESPONSE_ATTESTATION', ?)
        """.trimIndent(),
        id, definitionId, Timestamp.from(Instant.now()),
    )

    private fun insertTemplateBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        definitionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
    ) = execute(
        connection,
        """
        INSERT INTO information_request_template_requirement_binding
            (id, template_version_id, template_definition_id, template_requirement_id,
             template_section_id, display_order, prompt, response_mode, requiredness,
             contributor_role, review_policy)
        VALUES (?, ?, ?, ?, ?, 1, 'State the response', 'PROVIDE', 'REQUIRED', 'CONTRIBUTOR', 'NOT_REQUIRED')
        """.trimIndent(),
        id, versionId, definitionId, requirementId, sectionId,
    )

    private fun publishVersion(connection: Connection, versionId: UUID, actorId: UUID)
    {
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
            versionId,
        )
        execute(
            connection,
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
            Timestamp.from(Instant.now()), actorId, versionId,
        )
    }

    private fun insertRequest(connection: Connection, id: UUID, exchangeId: UUID, versionId: UUID, ownerId: UUID) = execute(
        connection,
        """
        INSERT INTO information_request
            (id, exchange_id, template_version_id, owner_type, owner_organization_id,
             state, gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
        VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
        """.trimIndent(),
        id, exchangeId, versionId, ownerId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
    )

    private fun execute(connection: Connection, sql: String, vararg values: Any?)
    {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun pathOf(connection: Connection, table: String, id: UUID): String
    {
        connection.prepareStatement("SELECT occurrence_path FROM $table WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "No $table row stored for $id" }
                return rows.getString(1)
            }
        }
    }

    private fun refused(connection: Connection, expected: String, block: () -> Unit)
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

    private fun withPostgres(block: (RootOccurrencePathPostgreSQLContainer) -> Unit)
    {
        val postgres = RootOccurrencePathPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_root_occurrence_path_test")
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

    private fun flyway(postgres: RootOccurrencePathPostgreSQLContainer, target: String? = null): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .apply { target?.let { target(MigrationVersion.fromVersion(it)) } }
            .load()
}
