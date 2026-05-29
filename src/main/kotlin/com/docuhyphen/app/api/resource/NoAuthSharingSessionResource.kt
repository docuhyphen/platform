package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateNoAuthSharingSession
import com.docuhyphen.app.api.service.sharingsession.SharingSessionDocumentService
import com.docuhyphen.app.api.service.sharingsession.SharingSessionRetrievalService
import com.docuhyphen.app.api.service.sharingsession.SharingSessionUpdateService
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

            Response.ok(BasicEntityToDtoTransformer.toNoAuthDto(sharingSession)).build()
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

    @POST
    @Path("/{sessionId}/otp")
    fun issueNoAuthSharingSessionOtp(
        @PathParam("sessionId") sessionId: String
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 2500)

        return try
        {
            sharingSessionUpdateService.issueRecipientOtp(sessionId)
            Response.status(Response.Status.NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.warn("OTP request denied", exception)
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                is SharingSessionNotFoundException ->
                {
                    logger.warn("OTP request for missing session", exception)
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                is IllegalArgumentException ->
                {
                    logger.warn("OTP request rejected", exception)
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }
                else ->
                {
                    logger.error("Error issuing sharing session OTP", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while issuing the verification code"))
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

            Response.ok(BasicEntityToDtoTransformer.toNoAuthDto(updatedSession)).build()
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

            Response.ok(DetailedEntityToDtoTransformer.toDto(document)).build()
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