package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer.Companion.toDto
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.DocumentDetailedDto
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.resource.model.AddSharingSessionDocumentRequest
import com.docuhyphen.app.api.resource.model.DownloadDocumentsZipRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import com.docuhyphen.app.api.service.sharingsession.DocumentPreviewConversionException
import com.docuhyphen.app.api.service.sharingsession.SharingSessionDocumentService
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File

@Path("sharing-sessions/{sessionId}/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionDocumentsResource @Inject constructor(
    private val sharingSessionDocumentService: SharingSessionDocumentService,
    private val fileStorageService: FileStorageService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentsResource::class.java)
    }

    @POST
    fun addSessionDocument(
        request: AddSharingSessionDocumentRequest,
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        return try
        {
            val document = with(request) {
                sharingSessionDocumentService.addDocument(
                    sessionId,
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
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError("An error occurred while adding sharing session document")
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
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            sharingSessionDocumentService.deleteDocument(sessionId, documentId)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error deleting sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error deleting sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error deleting sharing session document", exception)

                    val responseError = ResponseError("An error occurred while deleting sharing session document")
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
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {

        return try
        {
            val document = with(request) {
                sharingSessionDocumentService.updateDocument(
                    sessionId,
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
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error updating sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating sharing session document", exception)

                    val responseError = ResponseError("An error occurred while update sharing session document")
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
        @PathParam("sessionId") sessionId: String?,
        @PathParam("documentId") documentId: String?
    ): Response
    {

        return try
        {
            val document = sharingSessionDocumentService.uploadDocument(
                file,
                extension,
                sessionId,
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
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error uploading sharing session document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error uploading sharing session document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error uploading sharing session document", exception)
                    val responseError = ResponseError("An error occurred while uploading sharing session document")
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
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        return try
        {
            val zipFile = sharingSessionDocumentService.downloadDocumentsAsZip(sessionId, request.documentIds)
            Response.ok(zipFile)
                .header("Content-Disposition", "attachment; filename=\"documents.zip\"")
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
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
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val file = sharingSessionDocumentService.downloadDocument(sessionId, documentId)
            Response.ok(file)
                .header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error downloading sharing session document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error downloading sharing session document", exception)
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error downloading sharing session document", exception)
                    val responseError = ResponseError("An error occurred while downloading sharing session document")
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
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val document = sharingSessionDocumentService.getDocumentFilePreviewAsPdf(sessionId, documentId)
            Response.ok(document)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"${document.name}\"")
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
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