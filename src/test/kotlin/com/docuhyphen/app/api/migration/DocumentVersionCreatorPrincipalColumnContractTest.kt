package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

private class DocumentVersionCreatorPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DocumentVersionCreatorPostgreSQLContainer>(imageName)

/**
 * A stored document version names its creator with exactly one canonical principal, so a
 * participant, a group, a registered application, and a service principal are named the same way a
 * registered user is. There is no registered-user key or email beside it, a version cannot be
 * stored without it, and a row whose creator could only be stated by an email does not survive the
 * migration.
 */
class DocumentVersionCreatorPrincipalColumnContractTest
{
    @Test
    fun `the canonical creator is required and the legacy creator columns are gone`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertNull(nullabilityOf(connection, "created_by"))
                assertNull(nullabilityOf(connection, "createdbyemail"))

                assertEquals("NO", nullabilityOf(connection, "created_by_principal_kind"))
                assertEquals("NO", nullabilityOf(connection, "created_by_principal_id"))

                assertEquals("character varying", typeOf(connection, "created_by_principal_kind"))
                assertEquals("uuid", typeOf(connection, "created_by_principal_id"))
            }
        }
    }

    @Test
    fun `a version states the canonical principal that created it`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                listOf("USER", "PARTICIPANT", "PRINCIPAL_GROUP", "APPLICATION").forEach { kind ->
                    val versionId = UUID.randomUUID()
                    val principalId = UUID.randomUUID()

                    insertVersion(connection, versionId, documentId, principalKind = kind, principalId = principalId)

                    assertEquals(kind, valueOf(connection, versionId, "created_by_principal_kind"))
                    assertEquals(principalId.toString(), valueOf(connection, versionId, "created_by_principal_id"))
                }
            }
        }
    }

    @Test
    fun `a version without a creator is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "\"created_by_principal_kind\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, principalId = UUID.randomUUID())
                }
                refused(connection, "\"created_by_principal_id\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, principalKind = "USER")
                }
            }
        }
    }

    @Test
    fun `an unrecognised creator principal kind is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_creator_principal_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        principalKind = "DEVICE",
                        principalId = UUID.randomUUID(),
                    )
                }
            }
        }
    }

    @Test
    fun `a version a registered user created before the canonical pair keeps that user`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = "121").migrate()

            val userId = UUID.randomUUID()
            val versionId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)
                insertUser(connection, userId, "author@process.test")
                insertPreviousShapeVersion(
                    connection,
                    versionId,
                    documentId,
                    createdBy = userId,
                    createdByEmail = "author@process.test",
                )
            }

            flyway(postgres, target = "124").migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("USER", valueOf(connection, versionId, "created_by_principal_kind"))
                assertEquals(userId.toString(), valueOf(connection, versionId, "created_by_principal_id"))
            }
        }
    }

    @Test
    fun `a version recorded with only the registered-user key after the canonical pair keeps that user`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = "122").migrate()

            val userId = UUID.randomUUID()
            val versionId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)
                insertUser(connection, userId, "author@process.test")
                insertPreviousShapeVersion(connection, versionId, documentId, createdBy = userId)
            }

            flyway(postgres, target = "124").migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("USER", valueOf(connection, versionId, "created_by_principal_kind"))
                assertEquals(userId.toString(), valueOf(connection, versionId, "created_by_principal_id"))
            }
        }
    }

    @Test
    fun `a version whose creator could only be stated by an email is removed`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = "121").migrate()

            val emailOnlyVersionId = UUID.randomUUID()
            val anonymousVersionId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)
                insertPreviousShapeVersion(
                    connection,
                    emailOnlyVersionId,
                    documentId,
                    createdByEmail = "responder@process.test",
                )
                insertPreviousShapeVersion(connection, anonymousVersionId, documentId)
            }

            flyway(postgres, target = "124").migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(versionExists(connection, emailOnlyVersionId))
                assertFalse(versionExists(connection, anonymousVersionId))
            }
        }
    }

    // Support

    private fun withPostgres(block: (DocumentVersionCreatorPostgreSQLContainer) -> Unit)
    {
        val postgres = DocumentVersionCreatorPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_document_version_creator_test")
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

    private fun flyway(postgres: DocumentVersionCreatorPostgreSQLContainer, target: String? = null): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .apply { target?.let { target(MigrationVersion.fromVersion(it)) } }
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

    private fun insertDocument(connection: Connection, id: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO document
                (id, encryption_mode, is_deleted, created_date, update_date, title, type)
            VALUES (?, 0, FALSE, ?, ?, 'Process record', 'PDF')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, id: UUID, email: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, email, created_date, is_active, is_temporary, sign_in_attempts,
                 exchange_version, multifactor_authentication_type)
            VALUES (?, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, email)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertPreviousShapeVersion(
        connection: Connection,
        id: UUID,
        documentId: UUID,
        createdBy: UUID? = null,
        createdByEmail: String? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, storage_path, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by, createdbyemail)
            VALUES (?, ?, ?, 'record.pdf', ?, '1', 'OBJECT_STORE', 'OBJECT_KEY', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val key = "document-versions/$documentId/$id/record.pdf"
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, key)
            statement.setString(5, key)
            statement.setObject(6, createdBy)
            statement.setString(7, createdByEmail)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(
        connection: Connection,
        id: UUID,
        documentId: UUID,
        principalKind: String? = null,
        principalId: UUID? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by_principal_kind, created_by_principal_id,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, ?, 'record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?, ?, ?,
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'VERIFIED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, "document-versions/$documentId/$id/record.pdf")
            statement.setString(5, principalKind)
            statement.setObject(6, principalId)
            statement.executeUpdate()
        }
    }

    private fun versionExists(connection: Connection, versionId: UUID): Boolean
    {
        connection.prepareStatement("SELECT 1 FROM document_version WHERE id = ?").use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows -> return rows.next() }
        }
    }

    private fun valueOf(connection: Connection, versionId: UUID, column: String): String?
    {
        connection.prepareStatement("SELECT $column FROM document_version WHERE id = ?").use { statement ->
            statement.setObject(1, versionId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "No document version stored for $versionId" }
                return rows.getString(1)
            }
        }
    }

    private fun nullabilityOf(connection: Connection, column: String): String? =
        columnAttribute(connection, column, "is_nullable")

    private fun typeOf(connection: Connection, column: String): String? =
        columnAttribute(connection, column, "data_type")

    private fun columnAttribute(connection: Connection, column: String, attribute: String): String?
    {
        connection.prepareStatement(
            "SELECT $attribute FROM information_schema.columns " +
                "WHERE table_name = 'document_version' AND column_name = ?",
        ).use { statement ->
            statement.setString(1, column)
            statement.executeQuery().use { rows ->
                return if (rows.next()) rows.getString(1) else null
            }
        }
    }
}
