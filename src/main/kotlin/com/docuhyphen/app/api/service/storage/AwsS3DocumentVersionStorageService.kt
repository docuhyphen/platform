package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.exception.DocumentVersionObjectKeyInUseException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.DocumentVersionDeletionOutcome
import com.docuhyphen.app.api.model.document.DocumentVersionReadHandle
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.DocumentVersionObjectStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.File
import java.nio.file.Files

@Aws
@ApplicationScoped
class AwsS3DocumentVersionStorageService @Inject constructor(
    @ConfigProperty(name = "document.version.storage.aws.bucket") private val bucketName: String,
    @DocumentVersionObjectStore private val s3Client: S3Client,
) : DocumentVersionStorageService
{
    private companion object
    {
        const val NO_EXISTING_OBJECT = "*"
        const val KEY_ALREADY_WRITTEN_STATUS = 412
        const val OBJECT_MISSING_STATUS = 404
        const val CHECKSUM_REFUSED_ERROR = "BadDigest"
        const val TEMPORARY_COPY_PREFIX = "document-version-read-"
    }

    override fun writeNewVersion(
        key: String,
        file: File,
        expected: DocumentVersionContentDigest,
    ): ObjectStoreDocumentVersionLocator
    {
        val locator = ObjectStoreDocumentVersionLocator(key)

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(locator.value)
            .ifNoneMatch(NO_EXISTING_OBJECT)
            .contentType(Files.probeContentType(file.toPath()))
            .checksumAlgorithm(ChecksumAlgorithm.SHA256)
            .checksumSHA256(expected.base64Value())
            .build()

        try
        {
            s3Client.putObject(request, file.toPath())
        }
        catch (exception: S3Exception)
        {
            if (exception.statusCode() == KEY_ALREADY_WRITTEN_STATUS)
            {
                throw DocumentVersionObjectKeyInUseException(locator.value)
            }
            if (exception.awsErrorDetails()?.errorCode() == CHECKSUM_REFUSED_ERROR)
            {
                throw DocumentVersionContentDigestMismatchException(locator.value)
            }
            throw exception
        }

        return locator
    }

    override fun openVersion(locator: ObjectStoreDocumentVersionLocator): DocumentVersionReadHandle
    {
        val request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(locator.value)
            .build()

        val directory = Files.createTempDirectory(TEMPORARY_COPY_PREFIX)
        val target = directory
            .resolve(locator.value.substringAfterLast('/'))
            .toFile()
        val opened = DocumentVersionReadHandle.temporaryCopy(target, directory)

        try
        {
            target.outputStream().use { output ->
                s3Client.getObject(request, ResponseTransformer.toOutputStream(output))
            }
        } catch (failure: Exception) {
            opened.close()
            throw readFailure(locator, failure)
        }

        return opened
    }

    private fun readFailure(locator: ObjectStoreDocumentVersionLocator, failure: Exception): Exception =
        when {
            failure is NoSuchKeyException -> DocumentVersionContentNotFoundException(locator.value)
            failure is S3Exception && failure.statusCode() == OBJECT_MISSING_STATUS ->
                DocumentVersionContentNotFoundException(locator.value)

            else -> failure
        }

    override fun deleteVersion(locator: ObjectStoreDocumentVersionLocator): DocumentVersionDeletionOutcome
    {
        val versionIds = mutableListOf<String>()
        var keyMarker: String? = null
        var versionMarker: String? = null
        do
        {
            val page = s3Client.listObjectVersions(
                ListObjectVersionsRequest.builder()
                    .bucket(bucketName)
                    .prefix(locator.value)
                    .keyMarker(keyMarker)
                    .versionIdMarker(versionMarker)
                    .build(),
            )
            versionIds += page.versions().filter { it.key() == locator.value }.map { it.versionId() }
            versionIds += page.deleteMarkers().filter { it.key() == locator.value }.map { it.versionId() }
            keyMarker = page.nextKeyMarker()
            versionMarker = page.nextVersionIdMarker()
        }
        while (page.isTruncated == true)
        if (versionIds.isEmpty()) return DocumentVersionDeletionOutcome.ABSENT
        versionIds.forEach { versionId ->
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(locator.value)
                    .versionId(versionId)
                    .build(),
            )
        }
        return DocumentVersionDeletionOutcome.DELETED
    }
}
