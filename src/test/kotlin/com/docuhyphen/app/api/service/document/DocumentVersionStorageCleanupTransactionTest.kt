package com.docuhyphen.app.api.service.document

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.repository.document.DocumentVersionStorageCleanupRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.storage.DocumentVersionStorageService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.TransactionSynchronizationRegistry
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class DocumentVersionStorageCleanupTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var cleanups: DocumentVersionStorageCleanupRepository

    @Inject
    lateinit var storage: DocumentVersionStorageService

    @Inject
    lateinit var synchronizations: TransactionSynchronizationRegistry

    @Inject
    lateinit var recordingService: DocumentVersionRecordingService

    @Inject
    @ConfigProperty(name = "document.version.storage.local.root-directory")
    lateinit var storageRoot: String

    private val start: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    @Test
    fun `bytes whose prompt removal failed after a rollback are removed once their cleanup falls due, with backoff between failures`() {
        val flaky = FlakyStorage(storage).apply { refuseDeletes = true }
        val locator = locator()

        assertThrows(IllegalStateException::class.java)
        {
            QuarkusTransaction.requiringNew().run {
                cleanupAt(start, flaky).guard(locator)
                write(flaky, locator, "left behind")
                throw IllegalStateException("recording failed")
            }
        }

        assertTrue(stored(locator))
        assertEquals(0, attemptsOf(locator))
        cleanupAt(start.plus(Duration.ofMinutes(30)), flaky).sweep(SWEEP_LIMIT)
        assertTrue(stored(locator))
        assertEquals(0, attemptsOf(locator))

        val firstAttempt = start.plus(Duration.ofMinutes(61))
        cleanupAt(firstAttempt, flaky).sweep(SWEEP_LIMIT)
        assertTrue(stored(locator))
        assertEquals(1, attemptsOf(locator))
        assertEquals(firstAttempt.plus(Duration.ofMinutes(15)), nextAttemptOf(locator))

        cleanupAt(firstAttempt.plus(Duration.ofMinutes(14)), flaky).sweep(SWEEP_LIMIT)
        assertEquals(1, attemptsOf(locator))
        val secondAttempt = firstAttempt.plus(Duration.ofMinutes(15))
        cleanupAt(secondAttempt, flaky).sweep(SWEEP_LIMIT)
        assertEquals(2, attemptsOf(locator))
        assertEquals(secondAttempt.plus(Duration.ofMinutes(30)), nextAttemptOf(locator))

        flaky.refuseDeletes = false
        cleanupAt(secondAttempt.plus(Duration.ofMinutes(30)), flaky).sweep(SWEEP_LIMIT)
        assertFalse(stored(locator))
        assertFalse(hasCleanup(locator))
    }

    @Test
    fun `a cleanup never removes the bytes of a committed version that refers to them`() {
        val version = QuarkusTransaction.requiringNew().call {
            recordingService.recordStandaloneDocument(upload("committed version"))
        }
        val locator = ObjectStoreDocumentVersionLocator(version.storageLocator)
        assertFalse(hasCleanup(locator))
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO document_version_storage_cleanup (id, locator_kind, locator, created_at, attempt_count, next_attempt_at)
                VALUES (?, 'OBJECT_KEY', ?, ?, 0, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                locator.value,
                Timestamp.from(start.minus(Duration.ofHours(2))),
                Timestamp.from(start.minus(Duration.ofHours(1))),
            )
        }

        cleanupAt(start, storage).sweep(SWEEP_LIMIT)

        assertTrue(stored(locator))
        assertFalse(hasCleanup(locator))
    }

    @Test
    fun `a cleanup held by a recording transaction that is still running is left alone`() {
        val locator = locator()

        assertThrows(IllegalStateException::class.java)
        {
            QuarkusTransaction.requiringNew().run {
                cleanupAt(start, storage).guard(locator)
                write(storage, locator, "still recording")
                cleanupAt(start.plus(Duration.ofHours(2)), storage).sweep(SWEEP_LIMIT)
                assertTrue(stored(locator))
                throw IllegalStateException("recording failed")
            }
        }

        assertFalse(stored(locator))
        assertTrue(hasCleanup(locator))
        cleanupAt(start.plus(Duration.ofHours(2)), storage).sweep(SWEEP_LIMIT)
        assertFalse(hasCleanup(locator))
    }

    @Test
    fun `a committed recording leaves its bytes and no cleanup behind`() {
        val version = QuarkusTransaction.requiringNew().call {
            recordingService.recordStandaloneDocument(upload("recorded once"))
        }
        val locator = ObjectStoreDocumentVersionLocator(version.storageLocator)

        cleanupAt(start.plus(Duration.ofHours(2)), storage).sweep(SWEEP_LIMIT)

        assertTrue(stored(locator))
        assertFalse(hasCleanup(locator))
    }

    @Test
    fun `bytes are never stored outside the transaction that records them`() {
        val locator = locator()

        assertThrows(IllegalStateException::class.java) { cleanupAt(start, storage).guard(locator) }

        assertFalse(hasCleanup(locator))
    }

    private fun cleanupAt(now: Instant, storage: DocumentVersionStorageService) =
        DocumentVersionStorageCleanupService(
            cleanups,
            storage,
            synchronizations,
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofHours(1),
            Duration.ofMinutes(15),
        )

    private fun locator(): ObjectStoreDocumentVersionLocator =
        DocumentVersionObjectKeys.allocate(UUID.randomUUID(), UUID.randomUUID(), "cleanup-record.pdf")

    private fun write(
        storage: DocumentVersionStorageService,
        locator: ObjectStoreDocumentVersionLocator,
        content: String
    ) {
        val file = temporaryFile(content)
        storage.writeNewVersion(locator.value, file, DocumentVersionContentDigests.of(file))
    }

    private fun upload(content: String): DocumentVersionUpload {
        val file = temporaryFile(content)
        return DocumentVersionUpload(
            fileName = "cleanup-record.pdf",
            file = file,
            creator = PrincipalRef.user(UUID.randomUUID()),
            encryptionMode = DocumentEncryptionMode.INTERNAL,
            expectedDigest = DocumentVersionContentDigests.of(file),
        )
    }

    private fun temporaryFile(content: String): File =
        File.createTempFile("cleanup-upload", ".pdf").apply {
            deleteOnExit()
            writeText(content)
        }

    private fun stored(locator: ObjectStoreDocumentVersionLocator): Boolean = File(storageRoot, locator.value).isFile

    private fun hasCleanup(locator: ObjectStoreDocumentVersionLocator): Boolean =
        dataSource.connection.use {
            queryInt(it, "SELECT count(*) FROM document_version_storage_cleanup WHERE locator = ?", locator.value) > 0
        }

    private fun attemptsOf(locator: ObjectStoreDocumentVersionLocator): Int =
        dataSource.connection.use {
            queryInt(it, "SELECT attempt_count FROM document_version_storage_cleanup WHERE locator = ?", locator.value)
        }

    private fun nextAttemptOf(locator: ObjectStoreDocumentVersionLocator): Instant =
        dataSource.connection.use {
            Instant.ofEpochSecond(
                requireNotNull(
                    queryString(
                        it,
                        "SELECT extract(epoch FROM next_attempt_at)::bigint FROM document_version_storage_cleanup WHERE locator = ?",
                        locator.value,
                    ),
                ).toLong(),
            )
        }

    private class FlakyStorage(private val stored: DocumentVersionStorageService) :
        DocumentVersionStorageService by stored {
        var refuseDeletes = false

        override fun deleteVersion(locator: ObjectStoreDocumentVersionLocator): DocumentVersionDeletionOutcome {
            check(!refuseDeletes) { "storage unavailable" }
            return stored.deleteVersion(locator)
        }
    }

    private companion object {
        const val SWEEP_LIMIT = 1000
    }
}
