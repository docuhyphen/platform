package com.dochyphen.app.api.config

import com.dochyphen.app.api.qualifier.Aws
import com.dochyphen.app.api.qualifier.Local
import com.dochyphen.app.api.service.AwsS3FileStorageService
import com.dochyphen.app.api.service.FileStorageService
import com.dochyphen.app.api.service.LocalFileStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class FileStorageServiceProducer @Inject constructor(
    @ConfigProperty(name = "file.storage.service") private val fileStorageServiceType: String
) {
    @Produces
    @Local
    fun produceLocalFileStorageService(): FileStorageService {
        return LocalFileStorageService()
    }

    @Produces
    @Aws
    fun produceAwsS3FileStorageService(): FileStorageService {
        return AwsS3FileStorageService()
    }

    @Produces
    fun produceFileStorageService(
        @Local localFileStorageService: LocalFileStorageService,
        @Aws awsS3FileStorageService: AwsS3FileStorageService
    ): FileStorageService {
        return when (fileStorageServiceType) {
            "aws" -> awsS3FileStorageService
            "local" -> localFileStorageService
            else -> throw IllegalArgumentException("Invalid file storage service type")
        }
    }
}