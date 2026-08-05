package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.storage.AwsS3DocumentThumbnailStorageService
import com.docuhyphen.app.api.service.storage.DocumentThumbnailStorageService
import com.docuhyphen.app.api.service.storage.LocalDocumentThumbnailStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class DocumentThumbnailStorageServiceProducer @Inject constructor(
    @ConfigProperty(name = "document.thumbnail.storage.service")
    private val storageType: String,

    @Local
    private val localStorage: LocalDocumentThumbnailStorageService,

    @Aws
    private val awsStorage: AwsS3DocumentThumbnailStorageService,
)
{
    @Produces
    fun produce(): DocumentThumbnailStorageService = when (storageType)
    {
        "local" -> localStorage
        "aws" -> awsStorage
        else -> throw IllegalArgumentException("Invalid document thumbnail storage service type")
    }
}
