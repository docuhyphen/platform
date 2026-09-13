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

private class InformationRequestBlueprintDocumentPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestBlueprintDocumentPostgreSQLContainer>(imageName)

class InformationRequestBlueprintDocumentPlaceholderContractTest
{
    @Test
    fun `blueprint document placeholders snapshot library metadata for a runtime request`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val placeholderId = UUID.randomUUID()

                insertPlaceholder(connection, placeholderId, fixture)

                assertEquals(1, placeholderCount(connection, fixture.requestId))
                assertEquals("Reference packet", libraryTitle(connection, placeholderId))

                deleteLibraryDocument(connection, fixture.libraryDocumentId)

                assertNull(libraryDocumentId(connection, placeholderId))
                assertEquals("Reference packet", libraryTitle(connection, placeholderId))

                refused(connection, "ck_information_request_document_placeholder_title") {
                    insertPlaceholder(connection, UUID.randomUUID(), fixture, title = " ")
                }
                refused(connection, "ck_information_request_document_placeholder_size") {
                    insertPlaceholder(connection, UUID.randomUUID(), fixture, fileSizeBytes = -1)
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val templateDefinitionId: UUID = UUID.randomUUID()
        val templateVersionId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()
        val blueprintId: UUID = UUID.randomUUID()
        val libraryDocumentId: UUID = UUID.randomUUID()
        val blueprintDocumentDefaultId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertTemplateDefinition(connection, templateDefinitionId, organizationId)
            insertTemplateVersion(connection, templateVersionId, templateDefinitionId)
            publishVersion(connection, templateVersionId, userId)
            insertRequest(connection, requestId, exchangeId, templateVersionId, organizationId, userId)
            insertBlueprint(connection, blueprintId, organizationId, userId, templateVersionId)
            insertLibraryDocument(connection, libraryDocumentId, organizationId, userId)
            insertBlueprintDocumentDefault(connection, blueprintDocumentDefaultId, blueprintId, libraryDocumentId)
        }
    }

    private fun withPostgres(block: (InformationRequestBlueprintDocumentPostgreSQLContainer) -> Unit)
    {
        val postgres = InformationRequestBlueprintDocumentPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_information_request_blueprint_document_test")
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

    private fun flyway(postgres: InformationRequestBlueprintDocumentPostgreSQLContainer): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()

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

    private fun insertOrganization(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Primary Process Owner', ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "REG-${id.toString().take(8)}")
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
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
            statement.setString(3, "blueprint-document-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateDefinition(connection: Connection, id: UUID, organizationId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                 status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, "collection-${id.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateVersion(connection: Connection, id: UUID, definitionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, 1, 'DRAFT', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun publishVersion(connection: Connection, versionId: UUID, actorId: UUID)
    {
        recordRequiredCapabilities(connection, versionId)
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

    private fun recordRequiredCapabilities(connection: Connection, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), version.id, required.capability_key, 1
            FROM information_request_template_version version
                     CROSS JOIN request_template_required_capabilities(version.id) required
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertRequest(
        connection: Connection,
        id: UUID,
        exchangeId: UUID,
        versionId: UUID,
        ownerId: UUID,
        userId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                 state, gates_exchange_closure, aggregate_revision, party_revision,
                 created_by_app_user_id, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, exchangeId)
            statement.setObject(3, versionId)
            statement.setObject(4, ownerId)
            statement.setObject(5, userId)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, now)
            statement.executeUpdate()
        }
    }

    private fun insertBlueprint(connection: Connection, id: UUID, organizationId: UUID, userId: UUID, versionId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO blueprint_definition
                (id, name, scope, organization_id, created_by_app_user_id, config_json,
                 information_request_template_version_id, created_at, updated_at)
            VALUES (?, 'Collection blueprint', 'ORG', ?, ?, '{}', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setObject(4, versionId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertLibraryDocument(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO document_library
                (id, title, description, scope, organization_id, created_by_app_user_id,
                 document_type, file_name, file_size_bytes, storage_path, content_hash,
                 created_at, updated_at)
            VALUES (?, 'Reference packet', 'Seeded from a Library item', 'ORG', ?, ?,
                    'PDF', 'reference-packet.pdf', 42, 'library/reference-packet.pdf',
                    'hash-123', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertBlueprintDocumentDefault(connection: Connection, id: UUID, blueprintId: UUID, libraryDocumentId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO blueprint_document_default
                (id, blueprint_definition_id, title, restricted_type, restrict_type, required,
                 library_document_id, display_order)
            VALUES (?, ?, 'Reference document', 'PDF', TRUE, TRUE, ?, 1)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, blueprintId)
            statement.setObject(3, libraryDocumentId)
            statement.executeUpdate()
        }
    }

    private fun insertPlaceholder(
        connection: Connection,
        id: UUID,
        fixture: Fixture,
        title: String = "Reference document",
        fileSizeBytes: Long? = 42,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_document_placeholder
                (id, information_request_id, source_blueprint_document_default_id, title,
                 restricted_type, restrict_type, required, library_document_id, library_title,
                 library_description, library_document_type, library_file_name,
                 library_file_size_bytes, library_content_hash, display_order)
            VALUES (?, ?, ?, ?, 'PDF', TRUE, TRUE, ?, 'Reference packet',
                    'Seeded from a Library item', 'PDF', 'reference-packet.pdf', ?,
                    'hash-123', 1)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, fixture.requestId)
            statement.setObject(3, fixture.blueprintDocumentDefaultId)
            statement.setString(4, title)
            statement.setObject(5, fixture.libraryDocumentId)
            statement.setObject(6, fileSizeBytes)
            statement.executeUpdate()
        }
    }

    private fun deleteLibraryDocument(connection: Connection, id: UUID)
    {
        connection.prepareStatement("DELETE FROM document_library WHERE id = ?")
            .use { statement ->
                statement.setObject(1, id)
                statement.executeUpdate()
            }
    }

    private fun placeholderCount(connection: Connection, requestId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM information_request_document_placeholder WHERE information_request_id = ?",
        ).use { statement ->
            statement.setObject(1, requestId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun libraryTitle(connection: Connection, placeholderId: UUID): String =
        connection.prepareStatement(
            "SELECT library_title FROM information_request_document_placeholder WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, placeholderId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }

    private fun libraryDocumentId(connection: Connection, placeholderId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT library_document_id FROM information_request_document_placeholder WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, placeholderId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getObject(1) as UUID?
            }
        }
}
