package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeDocumentDetailsViewService @Inject constructor(
    private val fileStorageService: FileStorageService,
)
{
    fun detailedView(document: Document): DocumentDetailedDto? =
        withFileSize(DetailedEntityToDtoTransformer.toDto(document))

    private fun withFileSize(document: DocumentDetailedDto?): DocumentDetailedDto?
    {
        if (document == null || document.uploadDate == null) return document

        val documentId = document.id ?: return document
        val documentType = document.type
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.let { runCatching { DocumentType.valueOf(it) }.getOrNull() }
            ?: return document

        val storageKey = "$documentId${DocumentType.toFileExtension(documentType)}"
        val fileSize = runCatching { fileStorageService.getDocumentSizeBytes(storageKey) }.getOrNull()

        return document.copy(fileSize = fileSize)
    }
}
