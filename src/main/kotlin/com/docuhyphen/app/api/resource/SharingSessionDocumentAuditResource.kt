package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.sharingsession.SharingSessionDocumentAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("sharing-sessions/{sessionId}/documents/{documentId}/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingSessionDocumentAuditResource @Inject constructor(
    private val auditService: SharingSessionDocumentAuditService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentAuditResource::class.java)
    }

    @GET
    fun getAudits(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {
        return try
        {
            val logs = auditService.getDocumentAuditLogs(sessionId, documentId)
                .map { DetailedEntityToDtoTransformer.toDto(it) }
                .toTypedArray()

            Response.ok(logs).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException,
                is SharingSessionDocumentNotFoundException ->
                {
                    logger.error("Error getting sharing session document audit logs", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()

                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting sharing session document audit logs", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error downloading sharing session document audit logs", exception)

                    val responseError =
                        ResponseError("An error occurred while getting sharing session document audit logs")

                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}