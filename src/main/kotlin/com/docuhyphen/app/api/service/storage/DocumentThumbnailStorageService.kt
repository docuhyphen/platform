package com.docuhyphen.app.api.service.storage

import java.io.File

interface DocumentThumbnailStorageService
{
    fun store(file: File, key: String)
    fun load(key: String): ByteArray?
    fun exists(key: String): Boolean
    fun deleteDocumentThumbnails(documentId: String)
}
