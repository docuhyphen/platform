package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeDocumentVersionResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentVersionService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.status
import jakarta.ws.rs.core.StreamingOutput
import org.slf4j.LoggerFactory
import java.io.File

class ExchangeDocumentVersionResource @Inject constructor(
    private val exchangeDocumentVersionService: ExchangeDocumentVersionService
) : ExchangeDocumentVersionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentVersionResource::class.java)
    }

    override fun getDocumentVersions(
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            val versions = exchangeDocumentVersionService.getDocumentVersions(exchangeId, documentId)

            val versionDtos = versions.map(DetailedEntityToDtoTransformer::toDto).toTypedArray()

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

                is ForbiddenException -> {
                    logger.warn("Refused retrieving document versions: {}", exception.message)
                    status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()
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

    override fun createVersion(
        file: File?,
        encryptionMode: DocumentEncryptionMode?,
        exchangeId: String,
        documentId: String,
    ): Response
    {
        return try
        {
            val version = exchangeDocumentVersionService.createVersion(exchangeId, documentId, file, encryptionMode)

            status(CREATED).entity(DetailedEntityToDtoTransformer.toDto(version)).build()
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.warn(
                "Creating a document version was refused by the subscription plan check: plan={}",
                exception.denial.planCode,
            )
            throw exception
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

                is ForbiddenException -> {
                    logger.warn("Refused creating document version: {}", exception.message)
                    status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()
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

    override fun downloadVersion(
        exchangeId: String,
        documentId: String,
        versionId: String
    ): Response
    {
        return try
        {
            val content = exchangeDocumentVersionService.getVersionContent(exchangeId, documentId, versionId)
            Response.ok(StreamingOutput(content::transferTo))
                .header("Content-Disposition", "attachment; filename=\"${content.fileName}\"")
                .header("Content-Length", content.file.length())
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

                is ForbiddenException -> {
                    logger.warn("Refused downloading document version: {}", exception.message)
                    status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()
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

    override fun getLatestVersion(
        exchangeId: String,
        documentId: String
    ): Response
    {

        return try
        {
            val version = exchangeDocumentVersionService.getLatestVersion(exchangeId, documentId)
            if (version != null)
            {
                Response.ok(DetailedEntityToDtoTransformer.toDto(version)).build()
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

                is ForbiddenException -> {
                    logger.warn("Refused retrieving latest document version: {}", exception.message)
                    status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()
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
