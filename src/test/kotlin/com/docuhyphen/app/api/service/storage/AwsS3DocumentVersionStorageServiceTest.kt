package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.exception.DocumentVersionObjectKeyInUseException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionDeletionOutcome
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class AwsS3DocumentVersionStorageServiceTest {
    @TempDir
    lateinit var uploads: Path

    private val key = "document-versions/a/b/record_v1.pdf"
    private val s3Client: S3Client = mock()
    private val storage = AwsS3DocumentVersionStorageService("version-bucket", s3Client)

    @Test
    fun `a new version is written only where no object exists and carries the checksum of the expected bytes`() {
        val source = upload("first content")
        val expected = DocumentVersionContentDigests.of(source)

        val locator = storage.writeNewVersion(key, source, expected)

        val request = argumentCaptor<PutObjectRequest>()
            .also { verify(s3Client).putObject(it.capture(), eq(source.toPath())) }
            .firstValue
        assertEquals(ObjectStoreDocumentVersionLocator(key), locator)
        assertEquals("version-bucket", request.bucket())
        assertEquals(key, request.key())
        assertEquals("*", request.ifNoneMatch())
        assertEquals(ChecksumAlgorithm.SHA256, request.checksumAlgorithm())
        assertEquals(expected.base64Value(), request.checksumSHA256())
    }

    @Test
    fun `a key that already holds an object is refused as in use`() {
        val source = upload("second content")
        whenever(s3Client.putObject(any<PutObjectRequest>(), any<Path>())).thenThrow(
            s3Failure(
                412,
                "PreconditionFailed"
            )
        )

        assertThrows(DocumentVersionObjectKeyInUseException::class.java)
        {
            storage.writeNewVersion(key, source, DocumentVersionContentDigests.of(source))
        }
    }

    @Test
    fun `bytes the object store refuses for their checksum are refused as a digest mismatch`() {
        val source = upload("received content")
        whenever(s3Client.putObject(any<PutObjectRequest>(), any<Path>())).thenThrow(s3Failure(400, "BadDigest"))

        assertThrows(DocumentVersionContentDigestMismatchException::class.java)
        {
            storage.writeNewVersion(key, source, DocumentVersionContentDigests.of(upload("expected content")))
        }
    }

    @Test
    fun `any other object store failure on write is passed on unchanged`() {
        val source = upload("first content")
        val failure = s3Failure(500, "InternalError")
        whenever(s3Client.putObject(any<PutObjectRequest>(), any<Path>())).thenThrow(failure)

        val thrown = assertThrows(S3Exception::class.java)
        {
            storage.writeNewVersion(key, source, DocumentVersionContentDigests.of(source))
        }
        assertSame(failure, thrown)
    }

    @Test
    fun `an opened version is a local copy named by the key's final segment that is removed when it is closed`() {
        val before = temporaryCopies()
        whenever(s3Client.getObject(any<GetObjectRequest>(), any<ResponseTransformer<GetObjectResponse, Any>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<ResponseTransformer<GetObjectResponse, Any>>(1).transform(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(ByteArrayInputStream("stored content".toByteArray())),
                )
            }

        val opened = storage.openVersion(ObjectStoreDocumentVersionLocator(key))

        val request = argumentCaptor<GetObjectRequest>()
            .also { verify(s3Client).getObject(it.capture(), any<ResponseTransformer<GetObjectResponse, Any>>()) }
            .firstValue
        assertEquals("version-bucket", request.bucket())
        assertEquals(key, request.key())
        assertEquals("record_v1.pdf", opened.file.name)
        assertArrayEquals("stored content".toByteArray(), opened.file.readBytes())
        val copyDirectory = opened.file.parentFile
        assertTrue(copyDirectory.isDirectory)

        opened.close()

        assertFalse(opened.file.exists())
        assertFalse(copyDirectory.exists())
        assertEquals(before, temporaryCopies())
    }

    @Test
    fun `a missing object is refused as not found and leaves no local copy behind`() {
        val before = temporaryCopies()
        whenever(s3Client.getObject(any<GetObjectRequest>(), any<ResponseTransformer<GetObjectResponse, Any>>()))
            .thenThrow(NoSuchKeyException.builder().statusCode(404).message("missing").build())
            .thenThrow(s3Failure(404, "NotFound"))

        assertThrows(DocumentVersionContentNotFoundException::class.java)
        {
            storage.openVersion(ObjectStoreDocumentVersionLocator(key))
        }
        assertThrows(DocumentVersionContentNotFoundException::class.java)
        {
            storage.openVersion(ObjectStoreDocumentVersionLocator(key))
        }
        assertEquals(before, temporaryCopies())
    }

    @Test
    fun `any other read failure is passed on and leaves no local copy behind`() {
        val before = temporaryCopies()
        val failure = s3Failure(500, "InternalError")
        whenever(s3Client.getObject(any<GetObjectRequest>(), any<ResponseTransformer<GetObjectResponse, Any>>()))
            .thenThrow(failure)

        val thrown = assertThrows(S3Exception::class.java)
        {
            storage.openVersion(ObjectStoreDocumentVersionLocator(key))
        }
        assertSame(failure, thrown)
        assertEquals(before, temporaryCopies())
    }

    @Test
    fun `a deletion removes every version and delete marker of exactly the key across every listed page`() {
        whenever(s3Client.listObjectVersions(any<ListObjectVersionsRequest>())).thenReturn(
            ListObjectVersionsResponse.builder()
                .versions(
                    ObjectVersion.builder().key(key).versionId("version-1").build(),
                    ObjectVersion.builder().key("$key.preview").versionId("other-key-version").build(),
                )
                .deleteMarkers(DeleteMarkerEntry.builder().key(key).versionId("marker-1").build())
                .isTruncated(true)
                .nextKeyMarker(key)
                .nextVersionIdMarker("version-1")
                .build(),
            ListObjectVersionsResponse.builder()
                .versions(ObjectVersion.builder().key(key).versionId("version-2").build())
                .isTruncated(false)
                .build(),
        )

        val outcome = storage.deleteVersion(ObjectStoreDocumentVersionLocator(key))

        assertEquals(DocumentVersionDeletionOutcome.DELETED, outcome)
        val listings = argumentCaptor<ListObjectVersionsRequest>()
            .also { verify(s3Client, times(2)).listObjectVersions(it.capture()) }
            .allValues
        assertEquals(listOf(key, key), listings.map { it.prefix() })
        assertEquals(listOf(null, key), listings.map { it.keyMarker() })
        assertEquals(listOf(null, "version-1"), listings.map { it.versionIdMarker() })
        val deletions = argumentCaptor<DeleteObjectRequest>()
            .also { verify(s3Client, times(3)).deleteObject(it.capture()) }
            .allValues
        assertEquals(setOf("version-1", "marker-1", "version-2"), deletions.map { it.versionId() }.toSet())
        assertEquals(setOf(key), deletions.map { it.key() }.toSet())
        assertEquals(setOf("version-bucket"), deletions.map { it.bucket() }.toSet())
    }

    @Test
    fun `a deletion of a key that holds nothing is reported absent`() {
        whenever(s3Client.listObjectVersions(any<ListObjectVersionsRequest>()))
            .thenReturn(ListObjectVersionsResponse.builder().isTruncated(false).build())

        assertEquals(
            DocumentVersionDeletionOutcome.ABSENT,
            storage.deleteVersion(ObjectStoreDocumentVersionLocator(key))
        )
        verify(s3Client, never()).deleteObject(any<DeleteObjectRequest>())
    }

    private fun s3Failure(status: Int, code: String): S3Exception =
        S3Exception.builder()
            .statusCode(status)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(code).build())
            .message(code)
            .build() as S3Exception

    private fun upload(content: String): File =
        Files.createTempFile(uploads, "uploaded-version", ".pdf").toFile().apply { writeText(content) }

    private fun temporaryCopies(): Set<Path> =
        Files.list(Path.of(System.getProperty("java.io.tmpdir"))).use { entries ->
            entries.filter { it.fileName.toString().startsWith("document-version-read-") }.toList().toSet()
        }
}
