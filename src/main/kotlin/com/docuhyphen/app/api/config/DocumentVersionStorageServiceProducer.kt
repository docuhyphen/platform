package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.storage.AwsS3DocumentVersionStorageService
import com.docuhyphen.app.api.service.storage.DocumentVersionStorageService
import com.docuhyphen.app.api.service.storage.LocalDocumentVersionStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class DocumentVersionStorageServiceProducer @Inject constructor(
    @ConfigProperty(name = "document.version.storage.service")
    private val storageType: String,

    @Local
    private val localStorage: LocalDocumentVersionStorageService,

    @Aws
    private val awsStorage: AwsS3DocumentVersionStorageService,
)
{
    @Produces
    fun produce(): DocumentVersionStorageService = when (storageType)
    {
        "local" -> localStorage
        "aws" -> awsStorage
        else -> throw IllegalArgumentException("Invalid document version storage service type")
    }
}
