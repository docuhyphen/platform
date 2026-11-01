package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.exchange.handler.ExchangeDocumentRequestHandler
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeDocumentsResourceOperations
import com.docuhyphen.app.api.resource.model.AddExchangeDocumentRequest
import com.docuhyphen.app.api.resource.model.DownloadDocumentsZipRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import com.docuhyphen.app.api.service.exchange.DocumentPreviewConversionException
import com.docuhyphen.app.api.service.exchange.DocumentThumbnailUnavailableException
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentDetailsViewService
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File

class ExchangeDocumentsResource @Inject constructor(
    private val exchangeDocumentService: ExchangeDocumentService,
    exchangeDocumentDetailsViewService: ExchangeDocumentDetailsViewService,
) : ExchangeDocumentsResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentsResource::class.java)
    }

    private val handler = ExchangeDocumentRequestHandler(exchangeDocumentService, exchangeDocumentDetailsViewService)

    override fun addSessionDocument(
        request: AddExchangeDocumentRequest,
        exchangeId: String
    ): Response
    {
        return try
        {
            val document = exchangeDocumentService.addDocument(
                exchangeId,
                request.title,
                request.documentType,
                request.restrictedType,
            )
            Response.ok(BasicEntityToDtoTransformer.toDto(document)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while adding Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun deleteSessionDocument(
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            exchangeDocumentService.deleteDocument(exchangeId, documentId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deleting Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun updateSessionDocument(
        request: UpdateShareSessionDocumentRequest,
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            handler.update(exchangeId, documentId, request)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while update Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun uploadSessionDocument(
        file: File?,
        extension: String?,
        encryptionMode: DocumentEncryptionMode?,
        exchangeId: String?,
        documentId: String?
    ): Response
    {
        return try
        {
            handler.upload(file, extension, exchangeId, documentId, encryptionMode)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                {
                    logger.warn("Document upload access denied: {}", exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error uploading Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while uploading Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun downloadDocumentsAsZip(
        request: DownloadDocumentsZipRequest,
        exchangeId: String
    ): Response
    {
        return try
        {
            val zipFile = exchangeDocumentService.downloadDocumentsAsZip(exchangeId, request.documentIds)
            Response.ok(zipFile.inputStream())
                .header("Content-Disposition", "attachment; filename=\"documents.zip\"")
                .header("Content-Length", zipFile.length())
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error downloading Exchange documents as zip", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while downloading documents as zip"))
                        .build()
                }
            }
        }
    }

    override fun downloadDocument(
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            handler.download(exchangeId, documentId)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                {
                    logger.warn("Download format blocked: {}", exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error downloading Exchange document", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while downloading Exchange document"))
                        .build()
                }
            }
        }
    }

    override fun getDocumentPreviewAsPdf(
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            val document = exchangeDocumentService.getDocumentFilePreviewAsPdf(exchangeId, documentId)
            Response.ok(document.inputStream())
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"${document.name}\"")
                .header("Content-Length", document.length())
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is DocumentPreviewConversionException ->
                {
                    logger.warn("Preview conversion unavailable: {}", exception.message)
                    Response.status(SERVICE_UNAVAILABLE)
                        .header("X-Preview-Reason", "CONVERSION_FAILED")
                        .entity(ResponseError(exception.message ?: "Preview conversion failed"))
                        .build()
                }

                else ->
                {
                    logger.error("Error generating Exchange document preview", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while generating document preview"))
                        .build()
                }
            }
        }
    }

    override fun getDocumentThumbnail(
        exchangeId: String,
        documentId: String,
        ifNoneMatch: String?,
    ): Response
    {
        return try
        {
            handler.thumbnail(exchangeId, documentId, ifNoneMatch)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                {
                    logger.warn("Document thumbnail access denied: {}", exception.message)
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                }

                is DocumentThumbnailUnavailableException ->
                {
                    logger.warn("Document thumbnail is unavailable: {}", exception.message)
                    Response.status(SERVICE_UNAVAILABLE).entity(ResponseError(exception.message)).build()
                }

                else ->
                {
                    logger.error("Error loading Exchange document thumbnail", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while loading the document thumbnail"))
                        .build()
                }
            }
        }
    }
}
