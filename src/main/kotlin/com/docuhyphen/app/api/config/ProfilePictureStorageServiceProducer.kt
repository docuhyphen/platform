package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.storage.AwsS3ProfilePictureStorageService
import com.docuhyphen.app.api.service.storage.LocalProfilePictureStorageService
import com.docuhyphen.app.api.service.storage.ProfilePictureStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class ProfilePictureStorageServiceProducer @Inject constructor(
    @ConfigProperty(name = "profile.picture.storage.service")
    private val storageType: String,

    @Local
    private val localStorage: LocalProfilePictureStorageService,

    @Aws
    private val awsStorage: AwsS3ProfilePictureStorageService,
)
{
    @Produces
    fun produce(): ProfilePictureStorageService = when (storageType)
    {
        "local" -> localStorage
        "aws" -> awsStorage
        else -> throw IllegalArgumentException("Invalid profile picture storage service type")
    }
}

