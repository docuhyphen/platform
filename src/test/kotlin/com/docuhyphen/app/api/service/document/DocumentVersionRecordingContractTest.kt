package com.docuhyphen.app.api.service.document

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionCreatorMapper
import com.docuhyphen.app.api.model.document.DocumentVersionUpload
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.repository.document.DocumentRepository
import com.docuhyphen.app.api.repository.document.DocumentVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class DocumentVersionRecordingContractTest
{
    @Inject
    lateinit var recordingService: DocumentVersionRecordingService

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var documentVersionRepository: DocumentVersionRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `a standalone document is recorded with its first version and belongs to no exchange`()
    {
        val creator = PrincipalRef.participant(UUID.randomUUID())
        val file = upload("abc")

        val version = QuarkusTransaction.requiringNew().call {
            recordingService.recordStandaloneDocument(
                DocumentVersionUpload(
                    fileName = "collected-record.pdf",
                    file = file,
                    creator = creator,
                    encryptionMode = DocumentEncryptionMode.INTERNAL,
                    expectedDigest = DocumentVersionContentDigests.of(file),
                ),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(version.id))
            val document = requireNotNull(documentRepository.findById(requireNotNull(stored.document).id))

            assertEquals("collected-record.pdf", document.title)
            assertEquals(DocumentType.PDF, document.type)
            assertEquals("1", stored.version)
            assertEquals("collected-record.pdf", stored.fileName)
            assertEquals(creator, DocumentVersionCreatorMapper.read(stored))
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", stored.contentHash)
            assertEquals(DocumentVersionContentVerification.VERIFIED, stored.contentVerification)
            assertFalse(belongsToAnyExchange(document.id))
            assertEquals("abc", recordingService.open(stored).file.readText())
        }
    }

    @Test
    fun `a later version of a standalone document is numbered after its latest version`()
    {
        val creator = PrincipalRef.user(UUID.randomUUID())
        val first = QuarkusTransaction.requiringNew().call {
            recordingService.recordStandaloneDocument(standalone("first", creator))
        }

        val second = QuarkusTransaction.requiringNew().call {
            val document = requireNotNull(documentRepository.findById(requireNotNull(first.document).id))
            val file = upload("second")
            recordingService.recordNextVersion(
                document,
                DocumentVersionUpload(
                    fileName = "collected-record-revised.pdf",
                    file = file,
                    creator = creator,
                    encryptionMode = DocumentEncryptionMode.END_TO_END,
                    expectedDigest = DocumentVersionContentDigests.of(file),
                ),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(second.id))

            assertEquals(requireNotNull(first.document).id, requireNotNull(stored.document).id)
            assertEquals("2", stored.version)
            assertEquals("collected-record-revised.pdf", stored.fileName)
            assertEquals(DocumentVersionContentVerification.UNVERIFIED, stored.contentVerification)
            assertEquals(
                "first",
                recordingService.open(requireNotNull(documentVersionRepository.findById(first.id))).file.readText()
            )
        }
    }

    @Test
    fun `two versions recorded at the same time for one document receive different numbers`() {
        val creator = PrincipalRef.user(UUID.randomUUID())
        val first = QuarkusTransaction.requiringNew().call {
            recordingService.recordStandaloneDocument(standalone("first", creator))
        }
        val documentId = requireNotNull(first.document).id
        val heldVersionRecorded = CountDownLatch(1)
        val holder = Executors.newSingleThreadExecutor()
        try {
            val held = holder.submit<String> {
                QuarkusTransaction.requiringNew().call {
                    val version = recordingService.recordNextVersion(
                        requireNotNull(documentRepository.findById(documentId)),
                        standalone("held", creator),
                    )
                    heldVersionRecorded.countDown()
                    Thread.sleep(HOLD_OPEN_MILLIS)
                    version.version
                }
            }
            assertTrue(heldVersionRecorded.await(30, TimeUnit.SECONDS), "The held version was never recorded")

            val competing = QuarkusTransaction.requiringNew().call {
                recordingService.recordNextVersion(
                    requireNotNull(documentRepository.findById(documentId)),
                    standalone("competing", creator),
                ).version
            }

            assertEquals(setOf("2", "3"), setOf(held.get(30, TimeUnit.SECONDS), competing))
        } finally {
            holder.shutdownNow()
        }
    }

    @Test
    fun `content that does not match the digest the caller expected is refused and nothing is recorded`()
    {
        val file = upload("received")

        assertThrows(DocumentVersionContentDigestMismatchException::class.java)
        {
            QuarkusTransaction.requiringNew().run {
                recordingService.recordStandaloneDocument(
                    DocumentVersionUpload(
                        fileName = "collected-record.pdf",
                        file = file,
                        creator = PrincipalRef.user(UUID.randomUUID()),
                        encryptionMode = DocumentEncryptionMode.INTERNAL,
                        expectedDigest = DocumentVersionContentDigests.of(upload("expected")),
                    ),
                )
            }
        }
    }

    private fun standalone(content: String, creator: PrincipalRef): DocumentVersionUpload
    {
        val file = upload(content)
        return DocumentVersionUpload(
            fileName = "collected-record.pdf",
            file = file,
            creator = creator,
            encryptionMode = DocumentEncryptionMode.INTERNAL,
            expectedDigest = DocumentVersionContentDigests.of(file),
        )
    }

    private fun belongsToAnyExchange(documentId: UUID): Boolean =
        (entityManager.createNativeQuery("SELECT COUNT(*) FROM exchange_document WHERE documents_id = ?1")
            .setParameter(1, documentId)
            .singleResult as Number).toLong() > 0

    private fun upload(content: String): File =
        File.createTempFile("evidence-upload", ".pdf").apply {
            deleteOnExit()
            writeText(content)
        }

    private companion object {
        const val HOLD_OPEN_MILLIS = 1500L
    }
}
