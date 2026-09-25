package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import java.io.File

interface DocumentVersionStorageService
{
    fun writeNewVersion(key: String, file: File, expected: DocumentVersionContentDigest): ObjectStoreDocumentVersionLocator
    fun openVersion(locator: ObjectStoreDocumentVersionLocator): File
}
