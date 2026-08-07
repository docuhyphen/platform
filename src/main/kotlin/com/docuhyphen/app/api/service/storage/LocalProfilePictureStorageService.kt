package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Local
import jakarta.enterprise.context.ApplicationScoped
import java.io.File

@Local
@ApplicationScoped
class LocalProfilePictureStorageService : ProfilePictureStorageService
{
    private companion object
    {
        const val PROFILE_PICTURE_DIRECTORY = "local-development-resources/profile-pictures"
    }

    override fun store(file: File, key: String)
    {
        val target = File(PROFILE_PICTURE_DIRECTORY, key)
        target.parentFile.mkdirs()
        file.copyTo(target, overwrite = true)
    }

    override fun load(key: String): ByteArray?
    {
        val target = File(PROFILE_PICTURE_DIRECTORY, key)
        return target.takeIf { it.isFile }?.readBytes()
    }

    override fun delete(key: String)
    {
        val target = File(PROFILE_PICTURE_DIRECTORY, key)
        if (target.exists()) target.delete()
    }
}

