package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.BasicModelConverter
import com.dochyphen.app.api.model.entity.EntityToDtoTransformer
import com.dochyphen.app.api.model.entity.DocumentEncryptionMode
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateNoAuthSharingSession
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentService
import com.dochyphen.app.api.service.sharingsession.SharingSessionRetrievalService
import com.dochyphen.app.api.service.sharingsession.SharingSessionUpdateService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File

@Path("no-auth/sharing-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NoAuthSharingSessionResource @Inject constructor(
    private val sharingSessionRetrievalService: SharingSessionRetrievalService,
    private val sharingSessionUpdateService: SharingSessionUpdateService,
    private val sharingSessionDocumentService: SharingSessionDocumentService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NoAuthSharingSessionResource::class.java)
    }

    @GET
    @Path("/{sessionId}")
    fun getNoAuthSharingSession(@PathParam("sessionId") sessionId: String): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(2000, 4000)

        return try
        {
            val sharingSession = sharingSessionRetrievalService.getNoAuthSharingSession(sessionId)

            Response.ok(BasicModelConverter.toNoAuthDto(sharingSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError("An error occurred while getting sharing session")
                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @PUT
    @Path("/{sessionId}")
    fun updateNoAuthSharingSession(
        @PathParam("sessionId") sessionId: String,
        request: UpdateNoAuthSharingSession
    ): Response
    {
        return try
        {
            val updatedSession = with(request) {
                sharingSessionUpdateService.updateNoAuthSharingSession(sessionId, status, otp, rejectReason)
            }

            Response.ok(BasicModelConverter.toNoAuthDto(updatedSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.FORBIDDEN)
                        .entity(responseError)
                        .build()
                }

                is SharingSessionNotFoundException ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError("An error occurred while updating sharing session")
                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @POST
    @Path("{sessionId}/documents/{documentId}/file")
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
            val document = sharingSessionDocumentService.uploadNoAuthDocument(
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
                    Response.status(NOT_FOUND).entity(responseError).build()
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
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("{sessionId}/documents/{documentId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocument(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val file = sharingSessionDocumentService.downloadNoAuthSessionDocument(sessionId, documentId)
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
                    Response.status(NOT_FOUND).entity(responseError).build()
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
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}