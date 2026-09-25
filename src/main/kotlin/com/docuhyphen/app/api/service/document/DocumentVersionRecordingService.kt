package com.docuhyphen.app.api.service.document

import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionContentIdentityMapper
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionCreatorMapper
import com.docuhyphen.app.api.model.document.DocumentVersionUpload
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.repository.document.DocumentRepository
import com.docuhyphen.app.api.repository.exchange.DocumentVersionRepository
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class DocumentVersionRecordingService @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val documentVersionRepository: DocumentVersionRepository,
    private val documentVersionContentService: DocumentVersionContentService,
)
{
    fun recordStandaloneDocument(upload: DocumentVersionUpload): DocumentVersion
    {
        val now = Timestamp.from(Instant.now())
        val document = Document().apply {
            title = upload.fileName
            type = DocumentType.fromFileExtension(".${upload.fileName.substringAfterLast('.', "")}")
            encryptionMode = upload.encryptionMode
            hash = upload.expectedDigest.value
            createdDate = now
            updateDate = now
            uploadDate = now
        }
        documentRepository.save(document)

        return recordVersion(document, FIRST_VERSION, upload)
    }

    fun recordNextVersion(document: Document, upload: DocumentVersionUpload): DocumentVersion
    {
        document.hash = upload.expectedDigest.value
        document.encryptionMode = upload.encryptionMode
        document.updateDate = Timestamp.from(Instant.now())

        return recordVersion(document, nextVersionNumber(document), upload)
    }

    fun nextVersionNumber(document: Document): Int =
        documentVersionRepository.findByDocumentId(document.id).size + 1

    fun recordVersion(document: Document, versionNumber: Int, upload: DocumentVersionUpload): DocumentVersion
    {
        val version = DocumentVersion().apply {
            this.document = document
            fileName = upload.fileName
            this.version = "$versionNumber"
            createdDate = Timestamp.from(Instant.now())
        }

        DocumentVersionCreatorMapper.recordOn(version, upload.creator)
        DocumentVersionContentIdentityMapper.recordOn(
            version,
            documentVersionContentService.store(document.id, version.id, upload.fileName, upload.file, upload.expectedDigest),
            DocumentVersionContentVerification.forEncryptionMode(upload.encryptionMode),
        )

        documentVersionRepository.save(version)
        return version
    }

    fun findVersion(versionId: UUID): DocumentVersion? = documentVersionRepository.findById(versionId)

    fun open(version: DocumentVersion): DocumentVersionContent = documentVersionContentService.open(version)

    private companion object
    {
        const val FIRST_VERSION = 1
    }
}
