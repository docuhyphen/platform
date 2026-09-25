package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionContentIdentityMapper
import com.docuhyphen.app.api.model.document.DocumentVersionObjectKeys
import com.docuhyphen.app.api.model.document.DocumentVersionStorageLocatorMapper
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.model.document.StoredDocumentVersionContent
import com.docuhyphen.app.api.model.entity.DocumentVersion
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.File
import java.util.UUID

@ApplicationScoped
class DocumentVersionContentService @Inject constructor(
    private val documentVersionStorageService: DocumentVersionStorageService,
)
{
    fun store(
        documentId: UUID,
        versionId: UUID,
        fileName: String,
        file: File,
        expected: DocumentVersionContentDigest = DocumentVersionContentDigests.of(file),
    ): StoredDocumentVersionContent
    {
        val allocatedKey = DocumentVersionObjectKeys.allocate(documentId, versionId, fileName)
        val locator = documentVersionStorageService.writeNewVersion(allocatedKey.value, file, expected)

        return StoredDocumentVersionContent(locator, expected)
    }

    fun open(version: DocumentVersion): DocumentVersionContent
    {
        val file = when (val locator = DocumentVersionStorageLocatorMapper.read(version))
        {
            is ObjectStoreDocumentVersionLocator -> documentVersionStorageService.openVersion(locator)
        }

        if (DocumentVersionContentDigests.of(file) != DocumentVersionContentIdentityMapper.digestOf(version))
        {
            throw DocumentVersionContentIntegrityException(version.storageLocator)
        }

        return DocumentVersionContent(file, version.fileName)
    }
}
