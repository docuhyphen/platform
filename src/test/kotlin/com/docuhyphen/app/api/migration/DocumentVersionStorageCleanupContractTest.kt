package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class DocumentVersionStorageCleanupContractTest {
    @Test
    fun `a stored object has at most one pending cleanup, named by a non-blank object key, with a non-negative attempt count`() {
        withCleanupSchema { connection ->
            insertCleanup(connection, "document-versions/a/b/record.pdf")

            refusedBy(connection, "uq_document_version_storage_cleanup_locator") {
                insertCleanup(connection, "document-versions/a/b/record.pdf")
            }
            refusedBy(connection, "ck_document_version_storage_cleanup_locator_kind") {
                insertCleanup(connection, "document-versions/a/c/record.pdf", locatorKind = "LEGACY_LOCAL_PATH")
            }
            refusedBy(connection, "ck_document_version_storage_cleanup_locator") {
                insertCleanup(connection, "   ")
            }
            refusedBy(connection, "ck_document_version_storage_cleanup_attempt_count") {
                insertCleanup(connection, "document-versions/a/d/record.pdf", attempts = -1)
            }
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM document_version_storage_cleanup"),
            )
        }
    }

    @Test
    fun `a pending cleanup can be retried and removed`() {
        withCleanupSchema { connection ->
            val id = insertCleanup(connection, "document-versions/e/f/record.pdf")

            execute(
                connection,
                "UPDATE document_version_storage_cleanup SET attempt_count = attempt_count + 1, next_attempt_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(900)),
                id,
            )
            assertEquals(
                1,
                queryInt(connection, "SELECT attempt_count FROM document_version_storage_cleanup WHERE id = ?", id)
            )
            execute(connection, "DELETE FROM document_version_storage_cleanup WHERE id = ?", id)
            assertEquals(
                0,
                queryInt(connection, "SELECT count(*) FROM document_version_storage_cleanup WHERE id = ?", id)
            )
        }
    }

    private fun withCleanupSchema(block: (Connection) -> Unit) {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use(block)
        }
    }

    private fun insertCleanup(
        connection: Connection,
        locator: String,
        locatorKind: String = "OBJECT_KEY",
        attempts: Int = 0,
    ): UUID {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        execute(
            connection,
            """
            INSERT INTO document_version_storage_cleanup (id, locator_kind, locator, created_at, attempt_count, next_attempt_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            locatorKind,
            locator,
            now,
            attempts,
            now,
        )
        return id
    }
}
