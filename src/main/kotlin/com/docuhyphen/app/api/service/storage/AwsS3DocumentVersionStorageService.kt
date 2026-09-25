package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.exception.DocumentVersionObjectKeyInUseException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.qualifier.Aws
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.nio.file.Files

@Aws
@ApplicationScoped
class AwsS3DocumentVersionStorageService @Inject constructor(
    @ConfigProperty(name = "document.version.storage.aws.bucket") private val bucketName: String,
    @ConfigProperty(name = "document.version.storage.aws.region") private val awsRegion: String,
) : DocumentVersionStorageService
{
    private companion object
    {
        const val NO_EXISTING_OBJECT = "*"
        const val KEY_ALREADY_WRITTEN_STATUS = 412
        const val OBJECT_MISSING_STATUS = 404
        const val CHECKSUM_REFUSED_ERROR = "BadDigest"
    }

    private val s3Client: S3Client = S3Client.builder().region(Region.of(awsRegion)).build()

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

    override fun openVersion(locator: ObjectStoreDocumentVersionLocator): File
    {
        val request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(locator.value)
            .build()

        val target = Files.createTempDirectory("document-version")
            .resolve(locator.value.substringAfterLast('/'))
            .toFile()

        try
        {
            target.outputStream().use { output ->
                s3Client.getObject(request, ResponseTransformer.toOutputStream(output))
            }
        }
        catch (_: NoSuchKeyException)
        {
            target.delete()
            throw DocumentVersionContentNotFoundException(locator.value)
        }
        catch (exception: S3Exception)
        {
            target.delete()
            if (exception.statusCode() == OBJECT_MISSING_STATUS)
            {
                throw DocumentVersionContentNotFoundException(locator.value)
            }
            throw exception
        }

        return target
    }
}
