package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Local
import jakarta.enterprise.context.ApplicationScoped
import java.io.File

@Local
@ApplicationScoped
class LocalDocumentThumbnailStorageService : DocumentThumbnailStorageService
{
    private companion object
    {
        const val THUMBNAIL_DIRECTORY = "local-development-resources/document-thumbnails"
    }

    override fun store(file: File, key: String)
    {
        val target = File(THUMBNAIL_DIRECTORY, key)
        target.parentFile.mkdirs()
        file.copyTo(target, overwrite = true)
    }

    override fun load(key: String): ByteArray?
    {
        val target = File(THUMBNAIL_DIRECTORY, key)
        return target.takeIf { it.isFile }?.readBytes()
    }

    override fun exists(key: String): Boolean = File(THUMBNAIL_DIRECTORY, key).isFile

    override fun deleteDocumentThumbnails(documentId: String)
    {
        val target = File(THUMBNAIL_DIRECTORY, documentId)
        if (target.exists()) target.deleteRecursively()
    }
}
