package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayOutputStream

/**
 * S3-backed [AuditArchiveStorage]: one bucket, reused from the existing S3
 * service already declared in `infra/cloudformation.yml` (versioning + Object Lock Governance
 * mode; no new AWS service type). The application task's IAM role may
 * `PutObject`/`GetObject` only; it has no `DeleteObject`, retention-bypass, or legal-hold-admin
 * permission, so this class deliberately never calls those APIs.
 */
@ApplicationScoped
@Aws
class S3AuditArchiveStorage @Inject constructor(
    private val configService: AuditArchiveConfigService,
)
    : AuditArchiveStorage
{
    override fun putObject(key: String, bytes: ByteArray)
    {
        if (objectExists(key))
        {
            throw AuditArchiveObjectAlreadyExistsException(key)
        }

        withClient { client ->
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(configService.getBucket())
                    .key(key)
                    .contentType("application/octet-stream")
                    .build(),
                RequestBody.fromBytes(bytes),
            )
        }
    }

    override fun getObject(key: String): ByteArray
    {
        return withClient { client ->
            val output = ByteArrayOutputStream()
            try
            {
                client.getObject(
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(configService.getBucket())
                        .key(key)
                        .build(),
                    ResponseTransformer.toOutputStream(output),
                )
            }
            catch (e: NoSuchKeyException)
            {
                throw AuditArchiveObjectNotFoundException(key)
            }
            output.toByteArray()
        }
    }

    override fun objectExists(key: String): Boolean
    {
        return withClient { client ->
            try
            {
                client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(configService.getBucket())
                        .key(key)
                        .build(),
                )
                true
            }
            catch (e: NoSuchKeyException)
            {
                false
            }
            catch (e: software.amazon.awssdk.services.s3.model.S3Exception)
            {
                if (e.statusCode() == 404) false else throw e
            }
        }
    }

    override fun listKeysWithPrefix(prefix: String): List<String>
    {
        return withClient { client ->
            val keys = mutableListOf<String>()
            var continuationToken: String? = null
            do
            {
                val response = client.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(configService.getBucket())
                        .prefix(prefix)
                        .continuationToken(continuationToken)
                        .build(),
                )
                keys += response.contents().map { it.key() }
                continuationToken = if (response.isTruncated) response.nextContinuationToken() else null
            }
            while (continuationToken != null)
            keys
        }
    }

    private fun <T> withClient(block: (S3Client) -> T): T
    {
        return S3Client.builder()
            .region(Region.of(configService.getRegion()))
            .build()
            .use(block)
    }
}
