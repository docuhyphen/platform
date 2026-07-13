package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer.Companion.toDto
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.resource.model.AddExchangeDocumentRequest
import com.docuhyphen.app.api.resource.model.DownloadDocumentsZipRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import com.docuhyphen.app.api.service.exchange.DocumentPreviewConversionException
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import com.docuhyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File

@Path("exchanges/{exchangeId}/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeDocumentsResource @Inject constructor(
    private val exchangeDocumentService: ExchangeDocumentService,
    private val fileStorageService: FileStorageService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentsResource::class.java)
    }

    @POST
    fun addSessionDocument(
        request: AddExchangeDocumentRequest,
        @PathParam("exchangeId") exchangeId: String
    ): Response
    {
        return try
        {
            val document = with(request) {
                exchangeDocumentService.addDocument(
                    exchangeId,
                    title,
                    documentType,
                    restrictedType
                )
            }

            Response.ok(toDto(document)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException ->
                {
                    logger.error("Error adding exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error adding exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error adding exchange document", exception)

                    val responseError = ResponseError("An error occurred while adding exchange document")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{documentId}")
    fun deleteSessionDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
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
                {
                    logger.error("Error deleting exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error deleting exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error deleting exchange document", exception)

                    val responseError = ResponseError("An error occurred while deleting exchange document")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @PUT
    @Path("/{documentId}")
    fun updateSessionDocument(
        request: UpdateShareSessionDocumentRequest,
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {

        return try
        {
            val document = with(request) {
                exchangeDocumentService.updateDocument(
                    exchangeId,
                    documentId,
                    title,
                    type,
                    restrictedType
                )
            }

            val dto = DetailedEntityToDtoTransformer.toDto(document)
            Response.ok(enrichDocumentWithFileSize(dto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error updating exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating exchange document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating exchange document", exception)

                    val responseError = ResponseError("An error occurred while update exchange document")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("/{documentId}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadSessionDocument(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @RestForm("encryptionMode") encryptionMode: DocumentEncryptionMode?,
        @PathParam("exchangeId") exchangeId: String?,
        @PathParam("documentId") documentId: String?
    ): Response
    {

        return try
        {
            val document = exchangeDocumentService.uploadDocument(
                file,
                extension,
                exchangeId,
                documentId,
                encryptionMode
            )

            val dto = DetailedEntityToDtoTransformer.toDto(document)
            Response.ok(enrichDocumentWithFileSize(dto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is ForbiddenException ->
                {
                    logger.warn("Document preview access denied: {}", exception.message)
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error uploading exchange document", exception)
                    val responseError = ResponseError("An error occurred while uploading exchange document")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/zip-file")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocumentsAsZip(
        request: DownloadDocumentsZipRequest,
        @PathParam("exchangeId") exchangeId: String
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
                {
                    logger.error(
                        "Error downloading documents as zip",
                        exception
                    )
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error(
                        "Error downloading documents as zip",
                        exception
                    )
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error(
                        "Error downloading documents as zip",
                        exception
                    )
                    val responseError = ResponseError("An error occurred while downloading documents as zip")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("{documentId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val file = exchangeDocumentService.downloadDocument(exchangeId, documentId)
            Response.ok(file.inputStream())
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .header("Content-Length", file.length())
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error downloading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is ForbiddenException ->
                {
                    logger.warn("Download format blocked: {}", exception.message)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error downloading exchange document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error downloading exchange document", exception)
                    val responseError = ResponseError("An error occurred while downloading exchange document")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @Path("{documentId}/preview")
    @GET
//    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Produces("application/pdf")
    fun getDocumentPreviewAsPdf(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val document = exchangeDocumentService.getDocumentFilePreviewAsPdf(exchangeId, documentId)
//            Response.ok(document)
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
                {
                    logger.error("Error generating document preview", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error generating document preview", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                is DocumentPreviewConversionException ->
                {
                    logger.warn("Preview conversion unavailable: {}", exception.message)
                    val responseError = ResponseError(exception.message ?: "Preview conversion failed")
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .header("X-Preview-Reason", "CONVERSION_FAILED")
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error generating document preview", exception)
                    val responseError = ResponseError("An error occurred while generating document preview")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    private fun enrichDocumentWithFileSize(document: DocumentDetailedDto?): DocumentDetailedDto?
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
