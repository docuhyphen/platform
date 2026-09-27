package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestDisposalQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/disposal-standing")
@Produces(APPLICATION_JSON)
class InformationRequestDisposalStandingResource @Inject constructor(
    private val disposals: InformationRequestDisposalQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun get(@PathParam("id") id: String): Response
    {
        return try
        {
            val standing = disposals.standing(InformationRequestCommandHttp.uuid(id, "information request id"), accessContextFactory.currentAuthenticated())
            Response.ok(RecordPreservationDtoMapper.toDto(standing)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request disposal standing failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestDisposalStandingResource::class.java)
    }
}
