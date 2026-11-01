package com.docuhyphen.app.api.resource.exchange.handler

import com.docuhyphen.app.api.model.dto.DocumentThumbnailResult
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentDetailsViewService
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import jakarta.ws.rs.core.Response
import java.io.File

class ExchangeDocumentRequestHandler(
    private val exchangeDocumentService: ExchangeDocumentService,
    private val documentDetailsViewService: ExchangeDocumentDetailsViewService,
)
{
    fun update(exchangeId: String, documentId: String, request: UpdateShareSessionDocumentRequest): Response
    {
        val document = exchangeDocumentService.updateDocument(
            exchangeId,
            documentId,
            request.title,
            request.type,
            request.restrictedType,
        )
        return Response.ok(documentDetailsViewService.detailedView(document)).build()
    }

    fun upload(
        file: File?,
        extension: String?,
        exchangeId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?,
    ): Response
    {
        val document = exchangeDocumentService.uploadDocument(file, extension, exchangeId, documentId, encryptionMode)
        return Response.ok(documentDetailsViewService.detailedView(document)).build()
    }

    fun uploadNoAuth(
        file: File?,
        extension: String?,
        exchangeId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?,
        noAuthAccessToken: String?,
    ): Response
    {
        val document = exchangeDocumentService.uploadNoAuthDocument(
            file,
            extension,
            exchangeId,
            documentId,
            encryptionMode,
            noAuthAccessToken,
        )
        return Response.ok(documentDetailsViewService.detailedView(document)).build()
    }

    fun download(exchangeId: String, documentId: String): Response =
        fileResponse(exchangeDocumentService.downloadDocument(exchangeId, documentId))

    fun downloadNoAuth(exchangeId: String, documentId: String, noAuthAccessToken: String?): Response =
        fileResponse(exchangeDocumentService.downloadNoAuthSessionDocument(exchangeId, documentId, noAuthAccessToken))

    fun thumbnail(exchangeId: String, documentId: String, ifNoneMatch: String?): Response =
        thumbnailResponse(exchangeDocumentService.getDocumentThumbnail(exchangeId, documentId), ifNoneMatch)

    fun thumbnailNoAuth(
        exchangeId: String,
        documentId: String,
        noAuthAccessToken: String?,
        ifNoneMatch: String?,
    ): Response =
        thumbnailResponse(
            exchangeDocumentService.getNoAuthDocumentThumbnail(exchangeId, documentId, noAuthAccessToken),
            ifNoneMatch,
        )

    private fun fileResponse(file: File): Response =
        Response.ok(file.inputStream())
            .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
            .header("Content-Length", file.length())
            .build()

    private fun thumbnailResponse(thumbnail: DocumentThumbnailResult, ifNoneMatch: String?): Response
    {
        val etag = "\"${thumbnail.etag}\""
        if (ifNoneMatch == etag)
        {
            return Response.notModified()
                .header("ETag", etag)
                .header("Cache-Control", THUMBNAIL_CACHE_CONTROL)
                .build()
        }
        return Response.ok(thumbnail.content)
            .header("Content-Type", "image/png")
            .header("Content-Length", thumbnail.content.size)
            .header("ETag", etag)
            .header("Cache-Control", THUMBNAIL_CACHE_CONTROL)
            .build()
    }

    private companion object
    {
        const val THUMBNAIL_CACHE_CONTROL = "private, max-age=300, must-revalidate"
    }
}
