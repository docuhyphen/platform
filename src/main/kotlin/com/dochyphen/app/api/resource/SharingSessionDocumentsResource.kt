package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.BasicModelConverter.Companion.toDto
import com.dochyphen.app.api.model.entity.EntityToDtoTransformer
import com.dochyphen.app.api.model.entity.DocumentEncryptionMode
import com.dochyphen.app.api.resource.model.AddSharingSessionDocumentRequest
import com.dochyphen.app.api.resource.model.DownloadDocumentsZipRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentService
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
    private val sharingSessionDocumentService: SharingSessionDocumentService
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

        ResourceEndpointDelayHelper.delayEndpoint(4000, 6000)
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
        ResourceEndpointDelayHelper.delayEndpoint(4000, 6000)

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
        ResourceEndpointDelayHelper.delayEndpoint(2000, 3500)

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

            Response.ok(EntityToDtoTransformer.toDto(document)).build()
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

            Response.ok(EntityToDtoTransformer.toDto(document)).build()
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

                else ->
                {
                    logger.error("Error generating document preview", exception)
                    val responseError = ResponseError("An error occurred while generating document preview")
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}