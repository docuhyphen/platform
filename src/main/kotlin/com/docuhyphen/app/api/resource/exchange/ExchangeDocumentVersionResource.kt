package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentVersionService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.status
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File

@Path("exchanges/{exchangeId}/documents/{documentId}/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeDocumentVersionResource @Inject constructor(
    private val exchangeDocumentVersionService: ExchangeDocumentVersionService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentVersionResource::class.java)
    }

    @GET
    fun getDocumentVersions(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val versions = exchangeDocumentVersionService.getDocumentVersions(exchangeId, documentId)

            val versionDtos = versions.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()

            Response.ok(versionDtos).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
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
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @RestForm("userEmail") userEmail: String?
    ): Response
    {
        return try
        {
            val version = exchangeDocumentVersionService.createVersion(
                exchangeId,
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
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
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
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @PathParam("versionId") versionId: String
    ): Response
    {
        return try
        {
            val file = exchangeDocumentVersionService.getVersionFile(exchangeId, documentId, versionId)
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
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {

        return try
        {
            val version = exchangeDocumentVersionService.getLatestVersion(exchangeId, documentId)
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
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
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
