package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.DetailedEntityToDtoTransformer
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentVersionService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.status
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File

@Path("sharing-sessions/{sessionId}/documents/{documentId}/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionDocumentVersionResource @Inject constructor(
    private val sharingSessionDocumentVersionService: SharingSessionDocumentVersionService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentVersionResource::class.java)
    }

    @GET
    fun getDocumentVersions(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1000, 2000)

        return try
        {
            val versions = sharingSessionDocumentVersionService.getDocumentVersions(sessionId, documentId)

            val versionDtos = versions.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()

            Response.ok(versionDtos).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error retrieving document versions", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error retrieving document versions", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error retrieving document versions", exception)
                    val responseError = ResponseError("An error occurred while retrieving document versions")
                    status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun createVersion(
        @RestForm("file") file: File?,
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String,
        @RestForm("userEmail") userEmail: String?
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1000, 2000)

        return try
        {
            val version = sharingSessionDocumentVersionService.createVersion(
                sessionId,
                documentId,
                file,
                userEmail
            )

            status(CREATED).entity(DetailedEntityToDtoTransformer.toDto(version)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error creating document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error creating document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error creating document version", exception)
                    val responseError = ResponseError("An error occurred while creating document version")
                    status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("/{versionId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadVersion(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String,
        @PathParam("versionId") versionId: String
    ): Response
    {
        return try
        {
            val file = sharingSessionDocumentVersionService.getVersionFile(sessionId, documentId, versionId)
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
                    logger.error("Error downloading document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error downloading document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error downloading document version", exception)
                    val responseError = ResponseError("An error occurred while downloading document version")
                    status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @GET
    @Path("/latest")
    fun getLatestVersion(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1000, 2000)

        return try
        {
            val version = sharingSessionDocumentVersionService.getLatestVersion(sessionId, documentId)
            if (version != null)
            {
                Response.ok(version).build()
            }
            else
            {
                status(Response.Status.NOT_FOUND)
                    .entity(ResponseError("No versions found for this document"))
                    .build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error retrieving latest document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error retrieving latest document version", exception)
                    val responseError = ResponseError(exception.message)
                    status(Response.Status.BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error retrieving latest document version", exception)
                    val responseError = ResponseError("An error occurred while retrieving latest document version")
                    status(Response.Status.INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}