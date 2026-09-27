package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/audit-reconciliation")
@Produces(APPLICATION_JSON)
class InformationRequestAuditReconciliationResource @Inject constructor(
    private val audit: InformationRequestAuditService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun get(@PathParam("id") id: String): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            Response.ok(InformationRequestAuditDtoMapper.toDto(audit.reconciliation(requestId, accessContextFactory.currentAuthenticated()))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit reconciliation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditReconciliationResource::class.java)
    }
}
