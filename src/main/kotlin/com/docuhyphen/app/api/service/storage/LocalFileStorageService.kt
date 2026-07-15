package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Local
import jakarta.enterprise.context.ApplicationScoped
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Local
@ApplicationScoped
class LocalFileStorageService : FileStorageService
{
    private companion object
    {
        const val DOCUMENT_UPLOADS_DIRECTORY = "local-development-resources/document-uploads"
    }

    override fun uploadDocument(file: File, key: String): String
    {
        val targetDirectory = File(DOCUMENT_UPLOADS_DIRECTORY)

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
        val targetFile = File(DOCUMENT_UPLOADS_DIRECTORY, key)

        if (!targetFile.exists())
        {
            throw IllegalArgumentException("File not found")
        }

        return targetFile
    }

    override fun downloadDocumentsAsZip(keys: List<String>): File
    {
        val tempZipFile = File.createTempFile("documents", ".zip")
        val uniqueKeys = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zipOut ->
            keys.forEach { key ->
                if (uniqueKeys.add(key))
                {
                    val targetFile = File(DOCUMENT_UPLOADS_DIRECTORY, key)
                    if (!targetFile.exists())
                    {
                        throw IllegalArgumentException("File not found: $key")
                    }
                    zipOut.putNextEntry(ZipEntry(key))
                    Files.copy(targetFile.toPath(), zipOut)
                    zipOut.closeEntry()
                }
                else
                {
                    // Handle duplicate entry, e.g., by renaming or skipping
                    val newKey = generateUniqueKey(key, uniqueKeys)
                    uniqueKeys.add(newKey)
                    val targetFile = File(DOCUMENT_UPLOADS_DIRECTORY, key)
                    if (!targetFile.exists())
                    {
                        throw IllegalArgumentException("File not found: $key")
                    }
                    zipOut.putNextEntry(ZipEntry(newKey))
                    Files.copy(targetFile.toPath(), zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        return tempZipFile
    }

    override fun getDocumentSizeBytes(key: String): Long
    {
        val targetFile = File(DOCUMENT_UPLOADS_DIRECTORY, key)

        if (!targetFile.exists())
        {
            throw IllegalArgumentException("File not found")
        }

        return targetFile.length()
    }

    private fun generateUniqueKey(key: String, existingKeys: Set<String>): String
    {
        var newKey = key
        var counter = 1
        while (existingKeys.contains(newKey))
        {
            newKey = "${key}_$counter"
            counter++
        }
        return newKey
    }
}
