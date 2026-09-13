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
import java.util.UUID

private class CollectedFieldPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<CollectedFieldPostgreSQLContainer>(imageName)

/**
 * A requirement that asks for typed data says which field the answer is recorded against.
 *
 * Three separate questions are settled here, and each is asked at the point where it can be
 * answered. Whether a requirement of this kind names a field at all is a fact about one row, so the
 * write settles it. Whether one version names the same field twice is a fact about the version, so
 * the index settles it. Whether the version's own typed-data contract carries that field is a fact
 * about two independently written halves of one authored document, so publication settles it, which
 * is also the last point at which the author can still change either half.
 */
class InformationRequestTemplateCollectedFieldContractTest
{
    private val baselineVersion = "90"

    @Test
    fun `a typed requirement names a field and every other kind names none`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)

                refused(connection, "must name the field it collects") {
                    fixture.bindFieldRequirement(connection, collectedField = null)
                }
                refused(connection, "only a requirement that asks for typed data") {
                    fixture.bindAttestationRequirement(connection, collectedField = fixture.fieldDefinitionId)
                }

                fixture.bindFieldRequirement(connection, collectedField = fixture.fieldDefinitionId)
                assertEquals(
                    fixture.fieldDefinitionId,
                    collectedField(connection, fixture.fieldBindingId),
                )

                // The reference is to a stable field identity, so it cannot name something that is
                // not one.
                refused(connection, "request_template_binding_collected_field_fkey") {
                    fixture.bindSecondFieldRequirement(connection, collectedField = UUID.randomUUID())
                }
            }
        }
    }

    @Test
    fun `one version collects one field once`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.bindFieldRequirement(connection, collectedField = fixture.fieldDefinitionId)

                refused(connection, "ux_request_template_binding_collected_field") {
                    fixture.bindSecondFieldRequirement(connection, collectedField = fixture.fieldDefinitionId)
                }

                // Another version of the same definition asks its own question, so the same field
                // being collected there is not the same answer being recorded twice.
                fixture.bindFieldRequirementInOtherVersion(connection, fixture.fieldDefinitionId)
                assertEquals(
                    fixture.fieldDefinitionId,
                    collectedField(connection, fixture.otherVersionBindingId),
                )
            }
        }
    }

    @Test
    fun `a version cannot freeze while collecting a field its contract does not carry`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.bindFieldRequirement(connection, collectedField = fixture.fieldDefinitionId)
                fixture.nameSchemaVersion(connection)

                refused(connection, "does not bind") {
                    publish(connection, fixture.versionId, fixture.userId)
                }

                fixture.bindFieldIntoSchemaVersion(connection)
                publish(connection, fixture.versionId, fixture.userId)
                assertEquals("PUBLISHED", versionStatus(connection, fixture.versionId))
            }
        }
    }

    @Test
    fun `a baseline released before the rule keeps its bindings and names no field for them`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = baselineVersion).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                fixture.bindAttestationRequirement(connection, collectedField = null)

                flyway(postgres).migrate()

                assertNull(collectedField(connection, fixture.attestationBindingId))
            }
        }
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    /**
     * One organization-owned template with two versions and one section, plus the typed-data
     * contract a version may resolve against and one stable field it may carry.
     */
    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val namespace: String = "process-${UUID.randomUUID().toString().take(8)}"
        val definitionId: UUID = UUID.randomUUID()
        val versionId: UUID = UUID.randomUUID()
        val otherVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val otherSectionId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val fieldContractId: UUID = UUID.randomUUID()
        val fieldRequirementId: UUID = UUID.randomUUID()
        val secondFieldRequirementId: UUID = UUID.randomUUID()
        val attestationRequirementId: UUID = UUID.randomUUID()
        val fieldBindingId: UUID = UUID.randomUUID()
        val secondFieldBindingId: UUID = UUID.randomUUID()
        val attestationBindingId: UUID = UUID.randomUUID()
        val otherVersionBindingId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId, "Primary Process Owner")
            insertUser(connection, userId)
            insertDefinition(connection, definitionId, organizationId, namespace)
            insertVersion(connection, versionId, definitionId, 1)
            insertVersion(connection, otherVersionId, definitionId, 2)
            insertSection(connection, sectionId, versionId, "collected-data")
            insertSection(connection, otherSectionId, otherVersionId, "collected-data")
            insertSchemaVersion(connection, schemaDefinitionId, schemaVersionId, organizationId, namespace)
            insertFieldDefinition(connection, fieldDefinitionId, fieldContractId, namespace)
        }

        fun bindFieldRequirement(connection: Connection, collectedField: UUID?)
        {
            insertRequirement(connection, fieldRequirementId, definitionId, "recorded-note", "FIELD")
            insertBinding(connection, fieldBindingId, versionId, fieldRequirementId, sectionId, 1, collectedField)
        }

        fun bindSecondFieldRequirement(connection: Connection, collectedField: UUID?)
        {
            insertRequirement(connection, secondFieldRequirementId, definitionId, "restated-note", "FIELD")
            insertBinding(
                connection, secondFieldBindingId, versionId, secondFieldRequirementId, sectionId, 2, collectedField,
            )
        }

        fun bindFieldRequirementInOtherVersion(connection: Connection, collectedField: UUID)
        {
            insertRequirement(connection, fieldRequirementId, definitionId, "recorded-note", "FIELD")
            insertBinding(
                connection, otherVersionBindingId, otherVersionId, fieldRequirementId, otherSectionId, 1,
                collectedField,
            )
        }

        fun bindAttestationRequirement(connection: Connection, collectedField: UUID?)
        {
            insertRequirement(
                connection, attestationRequirementId, definitionId, "recorded-assertion", "RESPONSE_ATTESTATION",
            )
            insertBinding(
                connection, attestationBindingId, versionId, attestationRequirementId, sectionId, 3, collectedField,
            )
        }

        fun nameSchemaVersion(connection: Connection)
        {
            connection.prepareStatement(
                "UPDATE information_request_template_version SET schema_version_id = ? WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, schemaVersionId)
                statement.setObject(2, versionId)
                statement.executeUpdate()
            }
        }

        fun bindFieldIntoSchemaVersion(connection: Connection)
        {
            connection.prepareStatement(
                """
                INSERT INTO schema_field_binding
                    (id, schema_version_id, field_contract_id, field_definition_id, display_order,
                     is_required, is_read_only, visibility)
                VALUES (?, ?, ?, ?, 1, FALSE, FALSE, 'INTERNAL')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, schemaVersionId)
                statement.setObject(3, fieldContractId)
                statement.setObject(4, fieldDefinitionId)
                statement.executeUpdate()
            }
        }
    }

    // ── Storage helpers ───────────────────────────────────────────────────────

    private fun insertOrganization(connection: Connection, id: UUID, name: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, name)
            statement.setString(3, "REG-${id.toString().take(8)}")
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, "collected-field-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID, namespace: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name, status,
                 created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, ?, 'collection-pattern', 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, namespace)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(connection: Connection, id: UUID, definitionId: UUID, versionNumber: Int)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, ?, 'DRAFT', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, versionNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertSection(connection: Connection, id: UUID, versionId: UUID, sectionKey: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_section
                (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, ?, 1, 'Collected data')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setString(3, sectionKey)
            statement.executeUpdate()
        }
    }

    private fun insertSchemaVersion(
        connection: Connection,
        definitionId: UUID,
        versionId: UUID,
        organizationId: UUID,
        namespace: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO schema_definition
                (id, scope_kind, scope_org_id, namespace, schema_key, display_name,
                 target_resource_type, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, ?, 'collected-data', 'Collected data', 'INFORMATION_REQUEST',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, definitionId)
            statement.setObject(2, organizationId)
            statement.setString(3, namespace)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_version
                (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }

    private fun insertFieldDefinition(
        connection: Connection,
        definitionId: UUID,
        contractId: UUID,
        namespace: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_definition
                (id, scope_kind, namespace, field_key, status, created_at, updated_at)
            VALUES (?, 'PLATFORM', ?, 'recorded-note', 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, definitionId)
            statement.setString(2, namespace)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO field_contract
                (id, field_definition_id, contract_version, value_type, label, created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Recorded note', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, contractId)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }
    }

    private fun insertRequirement(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        requirementKey: String,
        requirementType: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (template_definition_id, requirement_key) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, requirementKey)
            statement.setString(4, requirementType)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    @Suppress("LongParameterList")
    private fun insertBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int,
        collectedField: UUID?,
    )
    {
        // The collected field is the last column so a baseline that predates it can insert the
        // same row without it.
        val columns = if (collectedFieldColumnExists(connection)) ", collected_field_definition_id" else ""
        val value = if (collectedFieldColumnExists(connection)) ", ?" else ""
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy$columns)
            SELECT ?, version.id, version.template_definition_id, ?, ?, ?, 'State the recorded note',
                   'PROVIDE', 'OPTIONAL', 'CONTRIBUTOR', 'NOT_REQUIRED'$value
            FROM information_request_template_version version
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requirementId)
            statement.setObject(3, sectionId)
            statement.setInt(4, displayOrder)
            if (columns.isEmpty())
            {
                statement.setObject(5, versionId)
            }
            else
            {
                statement.setObject(5, collectedField)
                statement.setObject(6, versionId)
            }
            statement.executeUpdate()
        }
    }

    /**
     * Publication requires the version to have recorded the runtime capabilities its configuration
     * needs, which is not what these tests are about, so the set is derived and recorded here.
     */
    private fun publish(connection: Connection, versionId: UUID, actorId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), version.id, required.capability_key, 1
            FROM information_request_template_version version
                     CROSS JOIN request_template_required_capabilities(version.id) required
            WHERE version.id = ?
              AND version.status = 'DRAFT'
              AND NOT EXISTS (SELECT 1
                              FROM information_request_template_version_capability existing
                              WHERE existing.template_version_id = version.id
                                AND existing.capability_key = required.capability_key)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, actorId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun collectedFieldColumnExists(connection: Connection): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (SELECT 1
                           FROM information_schema.columns
                           WHERE table_name = 'information_request_template_requirement_binding'
                             AND column_name = 'collected_field_definition_id')
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getBoolean(1)
            }
        }

    private fun collectedField(connection: Connection, bindingId: UUID): UUID? =
        connection.prepareStatement(
            """
            SELECT collected_field_definition_id
            FROM information_request_template_requirement_binding
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, bindingId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getObject(1) as UUID?
            }
        }

    private fun versionStatus(connection: Connection, versionId: UUID): String =
        connection.prepareStatement("SELECT status FROM information_request_template_version WHERE id = ?")
            .use { statement ->
                statement.setObject(1, versionId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
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

    private fun withPostgres(block: (CollectedFieldPostgreSQLContainer) -> Unit)
    {
        val postgres = CollectedFieldPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_collected_field_test")
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

    private fun flyway(postgres: CollectedFieldPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }
}

