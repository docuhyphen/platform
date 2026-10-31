package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.qualifier.DocumentVersionObjectStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@ApplicationScoped
class DocumentVersionS3ClientProducer @Inject constructor(
    @ConfigProperty(name = "document.version.storage.aws.region")
    private val awsRegion: String,
) {
    @Produces
    @Singleton
    @DocumentVersionObjectStore
    fun s3Client(): S3Client = S3Client.builder().region(Region.of(awsRegion)).build()

    fun close(@Disposes @DocumentVersionObjectStore s3Client: S3Client) {
        s3Client.close()
    }
}
