package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-audit-events")
@Produces(APPLICATION_JSON)
class InformationRequestAuditSearchResource @Inject constructor(
    private val audit: InformationRequestAuditService,
)
{
    @GET
    @Suppress("LongParameterList")
    fun search(
        @QueryParam("requestId") requestId: String?,
        @QueryParam("eventClass") eventClass: String?,
        @QueryParam("eventType") eventType: String?,
        @QueryParam("actorId") actorId: String?,
        @QueryParam("occurredAfter") occurredAfter: String?,
        @QueryParam("occurredBefore") occurredBefore: String?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
    {
        return try
        {
            val search = InformationRequestAuditQuery.searchOf(
                requestId?.takeIf { it.isNotBlank() }?.let { InformationRequestCommandHttp.uuid(it, "information request id") },
                eventClass, eventType, actorId, occurredAfter, occurredBefore, limit, offset,
            )
            Response.ok(InformationRequestAuditDtoMapper.toDto(audit.search(search))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit search failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditSearchResource::class.java)
    }
}
