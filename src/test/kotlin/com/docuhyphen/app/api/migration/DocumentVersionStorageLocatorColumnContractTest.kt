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

private class DocumentVersionStoragePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DocumentVersionStoragePostgreSQLContainer>(imageName)

/**
 * A stored document version names its content with exactly one typed locator: the provider that
 * holds it, the kind of identifier it is, and the identifier itself. There is no second column a
 * reader could fall back to, a version cannot be stored without its locator, and a row that could
 * only be located through the retired local path does not survive the migration.
 */
class DocumentVersionStorageLocatorColumnContractTest
{
    @Test
    fun `the canonical locator is required and the local path column is gone`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertNull(nullabilityOf(connection, "storage_path"))

                assertEquals("NO", nullabilityOf(connection, "storage_provider"))
                assertEquals("NO", nullabilityOf(connection, "storage_locator_kind"))
                assertEquals("NO", nullabilityOf(connection, "storage_locator"))

                assertEquals("character varying", typeOf(connection, "storage_provider"))
                assertEquals("character varying", typeOf(connection, "storage_locator_kind"))
                assertEquals("character varying", typeOf(connection, "storage_locator"))
            }
        }
    }

    @Test
    fun `a version stating its object-store locator is stored`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertDocument(connection, documentId)

                insertVersion(
                    connection,
                    versionId,
                    documentId,
                    provider = "OBJECT_STORE",
                    locatorKind = "OBJECT_KEY",
                    locator = "document-versions/$documentId/$versionId/record_v1.pdf",
                )

                assertEquals("OBJECT_STORE", valueOf(connection, versionId, "storage_provider"))
                assertEquals("OBJECT_KEY", valueOf(connection, versionId, "storage_locator_kind"))
                assertEquals(
                    "document-versions/$documentId/$versionId/record_v1.pdf",
                    valueOf(connection, versionId, "storage_locator"),
                )
            }
        }
    }

    @Test
    fun `a version missing any part of its locator is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "\"storage_provider\"") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        locatorKind = "OBJECT_KEY",
                        locator = "document-versions/9f1/record-1",
                    )
                }
                refused(connection, "\"storage_locator_kind\"") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "OBJECT_STORE",
                        locator = "document-versions/9f1/record-1",
                    )
                }
                refused(connection, "\"storage_locator\"") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "OBJECT_STORE",
                        locatorKind = "OBJECT_KEY",
                    )
                }
            }
        }
    }

    @Test
    fun `a local filesystem path is no longer an admitted locator`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_storage_locator_provider_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "LOCAL_FILESYSTEM",
                        locatorKind = "LEGACY_LOCAL_PATH",
                        locator = "storage/versions/$documentId/record_v1.pdf",
                    )
                }
                refused(connection, "ck_document_version_storage_locator_provider_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "OBJECT_STORE",
                        locatorKind = "LEGACY_LOCAL_PATH",
                        locator = "storage/versions/$documentId/record_v1.pdf",
                    )
                }
            }
        }
    }

    @Test
    fun `an unrecognised provider or locator kind is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_storage_locator_provider_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "REMOTE_ARCHIVE",
                        locatorKind = "OBJECT_KEY",
                        locator = "document-versions/9f1/record-1",
                    )
                }
                refused(connection, "ck_document_version_storage_locator_provider_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "OBJECT_STORE",
                        locatorKind = "CONTENT_ADDRESS",
                        locator = "document-versions/9f1/record-1",
                    )
                }
            }
        }
    }

    @Test
    fun `a blank locator is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val documentId = UUID.randomUUID()
                insertDocument(connection, documentId)

                refused(connection, "ck_document_version_storage_locator_value") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        documentId,
                        provider = "OBJECT_STORE",
                        locatorKind = "OBJECT_KEY",
                        locator = "   ",
                    )
                }
            }
        }
    }

    @Test
    fun `a version that can only be located through the retired local path is removed`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = "122").migrate()

            val documentId = UUID.randomUUID()
            val pathOnlyVersionId = UUID.randomUUID()
            val statedLocalVersionId = UUID.randomUUID()
            val objectVersionId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertDocument(connection, documentId)
                insertPreviousShapeVersion(
                    connection,
                    pathOnlyVersionId,
                    documentId,
                    storagePath = "storage/versions/$documentId/record_v1.pdf",
                )
                insertPreviousShapeVersion(
                    connection,
                    statedLocalVersionId,
                    documentId,
                    storagePath = "storage/versions/$documentId/record_v2.pdf",
                    provider = "LOCAL_FILESYSTEM",
                    locatorKind = "LEGACY_LOCAL_PATH",
                    locator = "storage/versions/$documentId/record_v2.pdf",
                )
                insertPreviousShapeVersion(
                    connection,
                    objectVersionId,
                    documentId,
                    storagePath = "document-versions/$documentId/$objectVersionId/record_v3.pdf",
                    provider = "OBJECT_STORE",
                    locatorKind = "OBJECT_KEY",
                    locator = "document-versions/$documentId/$objectVersionId/record_v3.pdf",
                )
            }

            flyway(postgres, target = "123").migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(versionExists(connection, pathOnlyVersionId))
                assertFalse(versionExists(connection, statedLocalVersionId))
                assertTrue(versionExists(connection, objectVersionId))
                assertEquals(
                    "document-versions/$documentId/$objectVersionId/record_v3.pdf",
                    valueOf(connection, objectVersionId, "storage_locator"),
                )
            }
        }
    }

    // Support

    private fun withPostgres(block: (DocumentVersionStoragePostgreSQLContainer) -> Unit)
    {
        val postgres = DocumentVersionStoragePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_document_version_storage_test")
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

    private fun flyway(postgres: DocumentVersionStoragePostgreSQLContainer, target: String? = null): Flyway =
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
        provider: String? = null,
        locatorKind: String? = null,
        locator: String? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by_principal_kind, created_by_principal_id,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, ?, 'record.pdf', '1', ?, ?, ?, 'USER', gen_random_uuid(),
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'VERIFIED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, provider)
            statement.setString(5, locatorKind)
            statement.setString(6, locator)
            statement.executeUpdate()
        }
    }

    private fun insertPreviousShapeVersion(
        connection: Connection,
        id: UUID,
        documentId: UUID,
        storagePath: String,
        provider: String? = null,
        locatorKind: String? = null,
        locator: String? = null,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_date, file_name, storage_path, version,
                 storage_provider, storage_locator_kind, storage_locator,
                 created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, 'record.pdf', ?, '1', ?, ?, ?, 'USER', gen_random_uuid())
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.setString(4, storagePath)
            statement.setString(5, provider)
            statement.setString(6, locatorKind)
            statement.setString(7, locator)
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
