package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("exchanges/{exchangeId}/documents/{documentId}/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeDocumentAuditResource @Inject constructor(
    private val auditService: ExchangeDocumentAuditService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentAuditResource::class.java)
    }

    @GET
    fun getAudits(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val logs = auditService.getDocumentAuditLogs(exchangeId, documentId)
                .map { DetailedEntityToDtoTransformer.toDto(it) }
                .toTypedArray()

            Response.ok(logs).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ExchangeNotFoundException,
                is ExchangeDocumentNotFoundException ->
                {
                    logger.error("Error getting exchange document audit logs", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting exchange document audit logs", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error downloading exchange document audit logs", exception)

                    val responseError =
                        ResponseError("An error occurred while getting exchange document audit logs")

                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}