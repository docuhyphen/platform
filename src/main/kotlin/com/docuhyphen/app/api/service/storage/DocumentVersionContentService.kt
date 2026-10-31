package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.DocumentVersion
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.File

@ApplicationScoped
class DocumentVersionContentService @Inject constructor(
    private val documentVersionStorageService: DocumentVersionStorageService,
)
{
    fun store(
        locator: ObjectStoreDocumentVersionLocator,
        file: File,
        expected: DocumentVersionContentDigest = DocumentVersionContentDigests.of(file),
    ): StoredDocumentVersionContent =
        StoredDocumentVersionContent(
            documentVersionStorageService.writeNewVersion(locator.value, file, expected),
            expected
        )

    fun open(version: DocumentVersion): DocumentVersionContent
    {
        val handle = when (val locator = DocumentVersionStorageLocatorMapper.read(version))
        {
            is ObjectStoreDocumentVersionLocator -> documentVersionStorageService.openVersion(locator)
        }

        try {
            if (DocumentVersionContentDigests.of(handle.file) != DocumentVersionContentIdentityMapper.digestOf(version)) {
                throw DocumentVersionContentIntegrityException(version.storageLocator)
            }
        } catch (failure: Exception) {
            handle.close()
            throw failure
        }

        return DocumentVersionContent(handle, version.fileName)
    }

    fun delete(locatorKind: String?, locator: String?): DocumentVersionDeletionOutcome
    {
        if (locatorKind == null || locator == null) return DocumentVersionDeletionOutcome.ABSENT
        return when (val stored =
            DocumentVersionStorageLocators.resolve(DocumentVersionLocatorKind.valueOf(locatorKind), locator))
        {
            is ObjectStoreDocumentVersionLocator -> documentVersionStorageService.deleteVersion(stored)
        }
    }
}
