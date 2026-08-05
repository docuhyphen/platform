package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Aws
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File

@Aws
@ApplicationScoped
class AwsS3DocumentThumbnailStorageService @Inject constructor(
    @ConfigProperty(name = "document.thumbnail.storage.aws.bucket") private val bucketName: String,
    @ConfigProperty(name = "document.thumbnail.storage.aws.region") private val awsRegion: String,
) : DocumentThumbnailStorageService
{
    private val s3Client = S3Client.builder().region(Region.of(awsRegion)).build()

    override fun store(file: File, key: String)
    {
        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("image/png")
            .build()
        s3Client.putObject(request, RequestBody.fromFile(file))
    }

    override fun load(key: String): ByteArray?
    {
        val request = GetObjectRequest.builder().bucket(bucketName).key(key).build()
        return try
        {
            s3Client.getObject(request, ResponseTransformer.toBytes()).asByteArray()
        }
        catch (_: NoSuchKeyException)
        {
            null
        }
        catch (exception: S3Exception)
        {
            if (exception.statusCode() == 404) null else throw exception
        }
    }

    override fun exists(key: String): Boolean
    {
        val request = HeadObjectRequest.builder().bucket(bucketName).key(key).build()
        return try
        {
            s3Client.headObject(request)
            true
        }
        catch (_: NoSuchKeyException)
        {
            false
        }
        catch (exception: S3Exception)
        {
            if (exception.statusCode() == 404) false else throw exception
        }
    }

    override fun deleteDocumentThumbnails(documentId: String)
    {
        val prefix = "$documentId/"
        var continuationToken: String? = null
        do
        {
            val response = s3Client.listObjectsV2 { builder ->
                builder.bucket(bucketName).prefix(prefix).continuationToken(continuationToken)
            }
            response.contents().forEach { item ->
                val request = DeleteObjectRequest.builder().bucket(bucketName).key(item.key()).build()
                s3Client.deleteObject(request)
            }
            continuationToken = response.nextContinuationToken()
        }
        while (continuationToken != null)
    }
}
