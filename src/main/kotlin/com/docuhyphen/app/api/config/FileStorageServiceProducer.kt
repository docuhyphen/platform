package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.storage.AwsS3FileStorageService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.storage.LocalFileStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class FileStorageServiceProducer @Inject constructor(
    @ConfigProperty(name = "file.storage.service") private val fileStorageServiceType: String,
    @Local private val localFileStorageService: LocalFileStorageService,
    @Aws private val awsS3FileStorageService: AwsS3FileStorageService,
) {
    @Produces
    fun produceFileStorageService(): FileStorageService {
        return when (fileStorageServiceType) {
            "aws" -> awsS3FileStorageService
            "local" -> localFileStorageService
            else -> throw IllegalArgumentException("Invalid file storage service type")
        }
    }
}
