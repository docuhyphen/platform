package com.dochyphen.app.api.service.storage

import com.dochyphen.app.api.qualifier.Local
import jakarta.enterprise.context.ApplicationScoped
import java.io.File

@Local
@ApplicationScoped
class LocalFileStorageService : FileStorageService
{
    override fun uploadDocument(file: File, key: String): String
    {
        val targetDirectory = File("document-uploads")

        if (!targetDirectory.exists())
        {
            targetDirectory.mkdirs()
        }

        val targetFile = File(targetDirectory, key)
        file.copyTo(targetFile, overwrite = true)

        return targetFile.absolutePath
    }

    override fun downloadDocument(key: String): File
    {
        val targetFile = File("document-uploads", key)

        if (!targetFile.exists())
        {
            throw IllegalArgumentException("File not found")
        }

        return targetFile
    }


}