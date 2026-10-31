package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.dto.ExchangeDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeDetailsViewService @Inject constructor(
    private val shareService: ShareService,
    private val appUserService: AppUserService,
    private val principalGroupService: PrincipalGroupService,
    private val fileStorageService: FileStorageService,
)
{
    fun detailedView(exchangeDto: ExchangeDetailedDto?): ExchangeDetailedDto? =
        withRecipient(withPermissions(withFileSizes(exchangeDto)))

    private fun withFileSizes(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null

        return sessionDto.copy(
            documents = sessionDto.documents.map { document -> withDocumentFileSize(document) }
        )
    }

    private fun withRecipient(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null
        if (sessionDto.recipient != null) return sessionDto
        val recipientUserId = shareService.primaryRecipientUserIdForDisplay(sessionDto.id)
        if (recipientUserId != null)
        {
            val recipient = appUserService.getById(recipientUserId) ?: return sessionDto
            return sessionDto.copy(
                recipient = DetailedEntityToDtoTransformer.toPublicDto(
                    recipient
                )
            )
        }
        val recipientGroupId = shareService.primaryRecipientGroupIdForDisplay(sessionDto.id) ?: return sessionDto
        val groupName = principalGroupService.findById(recipientGroupId)?.name ?: return sessionDto
        return sessionDto.copy(recipientGroupName = groupName)
    }

    private fun withPermissions(sessionDto: ExchangeDetailedDto?): ExchangeDetailedDto?
    {
        if (sessionDto == null) return null
        val constraintsJson = shareService.recipientConstraintsJson(sessionDto.id) ?: return sessionDto
        val c = ShareConstraints.parse(constraintsJson) ?: return sessionDto
        val downloadAllowed = c.canDownload != false &&
                (constraintsJson.contains("\"allow_document_download\":true") ||
                        c.canDownload == true)
        return sessionDto.copy(
            allowDocumentAddition = c.allowDocumentAddition == true,
            allowDocumentDeletion = c.allowDocumentDeletion == true,
            allowDocumentDownload = downloadAllowed,
            allowDocumentUpdate = c.allowDocumentUpdate == true,
            allowDocumentUpload = c.allowDocumentUpload == true,
            watermark = c.watermark,
            requireMfa = c.requireMfa,
            allowedDownloadFormats = c.allowedDownloadFormats,
        )
    }

    private fun withDocumentFileSize(document: DocumentDetailedDto?): DocumentDetailedDto?
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
