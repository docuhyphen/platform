package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/audit-events")
@Produces(APPLICATION_JSON)
class InformationRequestAuditEventResource @Inject constructor(
    private val audit: InformationRequestAuditService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    @Suppress("LongParameterList")
    fun list(
        @PathParam("id") id: String,
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
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val search = InformationRequestAuditQuery.searchOf(null, eventClass, eventType, actorId, occurredAfter, occurredBefore, limit, offset)
            Response.ok(InformationRequestAuditDtoMapper.toDto(audit.events(requestId, accessContextFactory.currentAuthenticated(), search))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit history failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditEventResource::class.java)
    }
}
