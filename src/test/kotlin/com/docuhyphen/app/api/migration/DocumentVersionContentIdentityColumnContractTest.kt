package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class DocumentVersionContentIdentityPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DocumentVersionContentIdentityPostgreSQLContainer>(imageName)

class DocumentVersionContentIdentityColumnContractTest
{
    private val recordedDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `every part of a version's content identity is required`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("NO", nullabilityOf(connection, "content_length"))
                assertEquals("NO", nullabilityOf(connection, "content_hash_algorithm"))
                assertEquals("NO", nullabilityOf(connection, "content_hash"))
                assertEquals("NO", nullabilityOf(connection, "content_verification"))

                assertEquals("bigint", typeOf(connection, "content_length"))
                assertEquals("character varying", typeOf(connection, "content_hash_algorithm"))
                assertEquals("character varying", typeOf(connection, "content_hash"))
                assertEquals("character varying", typeOf(connection, "content_verification"))
            }
        }
    }

    @Test
    fun `a version stating its content identity is stored`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertDocument(connection, documentId)

                insertVersion(connection, versionId, documentId)

                assertEquals("3", valueOf(connection, versionId, "content_length"))
                assertEquals("SHA_256", valueOf(connection, versionId, "content_hash_algorithm"))
                assertEquals(recordedDigest, valueOf(connection, versionId, "content_hash"))
                assertEquals("VERIFIED", valueOf(connection, versionId, "content_verification"))
            }
        }
    }

    @Test
    fun `a version of opaque content is stored as unverified`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertDocument(connection, documentId)

                insertVersion(connection, versionId, documentId, verification = "UNVERIFIED")

                assertEquals("UNVERIFIED", valueOf(connection, versionId, "content_verification"))
            }
        }
    }

    @Test
    fun `a version missing any part of its content identity is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "\"content_length\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, length = null)
                }
                refused(connection, "\"content_hash_algorithm\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, algorithm = null)
                }
                refused(connection, "\"content_hash\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, hash = null)
                }
                refused(connection, "\"content_verification\"") {
                    insertVersion(connection, UUID.randomUUID(), documentId, verification = null)
                }
            }
        }
    }

    @Test
    fun `only a SHA-256 digest is admitted`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_content_hash_algorithm") {
                    insertVersion(connection, UUID.randomUUID(), documentId, algorithm = "MD5")
                }
            }
        }
    }

    @Test
    fun `a digest that is not a lowercase hexadecimal SHA-256 value is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_content_hash_value") {
                    insertVersion(connection, UUID.randomUUID(), documentId, hash = recordedDigest.uppercase())
                }
                refused(connection, "ck_document_version_content_hash_value") {
                    insertVersion(connection, UUID.randomUUID(), documentId, hash = recordedDigest.drop(1))
                }
                refused(connection, "ck_document_version_content_hash_value") {
                    insertVersion(connection, UUID.randomUUID(), documentId, hash = "g" + recordedDigest.drop(1))
                }
            }
        }
    }

    @Test
    fun `a negative content length is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_content_length") {
                    insertVersion(connection, UUID.randomUUID(), documentId, length = -1)
                }
            }
        }
    }

    @Test
    fun `only verified and unverified content states are admitted`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_content_verification") {
                    insertVersion(connection, UUID.randomUUID(), documentId, verification = "PENDING")
                }
                refused(connection, "ck_document_version_content_verification") {
                    insertVersion(connection, UUID.randomUUID(), documentId, verification = "BACKFILL_FAILED")
                }
            }
        }
    }

    @Test
    fun `a stored version's storage and content identity cannot be rewritten`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertDocument(connection, documentId)
                insertVersion(connection, versionId, documentId)

                listOf(
                    "storage_locator = 'document-versions/other/record.pdf'",
                    "content_length = 4",
                    "content_hash = '${"0".repeat(64)}'",
                    "content_verification = 'UNVERIFIED'",
                ).forEach { assignment ->
                    refused(connection, "document version content identity is immutable") {
                        connection.prepareStatement("UPDATE document_version SET $assignment WHERE id = ?")
                            .use { statement ->
                                statement.setObject(1, versionId)
                                statement.executeUpdate()
                            }
                    }
                }

                assertEquals(recordedDigest, valueOf(connection, versionId, "content_hash"))
            }
        }
    }

    @Test
    fun `a version's descriptive fields stay writable`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertDocument(connection, documentId)
                insertVersion(connection, versionId, documentId)

                connection.prepareStatement("UPDATE document_version SET file_name = 'renamed.pdf' WHERE id = ?")
                    .use { statement ->
                        statement.setObject(1, versionId)
                        statement.executeUpdate()
                    }

                assertEquals("renamed.pdf", valueOf(connection, versionId, "file_name"))
            }
        }
    }

    @Test
    fun `a version that never stated its content identity does not survive the migration`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = "127").migrate()

            val documentId = UUID.randomUUID()
            val versionId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertDocument(connection, documentId)
                insertPreviousShapeVersion(connection, versionId, documentId)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(versionExists(connection, versionId))
            }
        }
    }

    private fun withPostgres(block: (DocumentVersionContentIdentityPostgreSQLContainer) -> Unit)
    {
        val postgres = DocumentVersionContentIdentityPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_document_version_content_identity_test")
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

    private fun flyway(postgres: DocumentVersionContentIdentityPostgreSQLContainer, target: String? = null): Flyway =
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

    private fun insertVersion(
        connection: Connection,
        id: UUID,
        documentId: UUID,
        length: Long? = 3,
        algorithm: String? = "SHA_256",
        hash: String? = recordedDigest,
        verification: String? = "VERIFIED",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by_principal_kind, created_by_principal_id,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, ?, 'record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?, 'USER', gen_random_uuid(),
                    ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, "document-versions/$documentId/$id/record.pdf")
            if (length == null) statement.setNull(5, java.sql.Types.BIGINT) else statement.setLong(5, length)
            statement.setString(6, algorithm)
            statement.setString(7, hash)
            statement.setString(8, verification)
            statement.executeUpdate()
        }
    }

    private fun insertPreviousShapeVersion(connection: Connection, id: UUID, documentId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, 'record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?, 'USER', gen_random_uuid())
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, "document-versions/$documentId/$id/record.pdf")
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
