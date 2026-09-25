package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLineageQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/carry-forwards")
@Produces(APPLICATION_JSON)
class InformationRequestCarryForwardResource @Inject constructor(
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val offers = lineageQueryService.carryForwards(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated(),
            )
            Response.ok(offers.map(InformationRequestLineageDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request carry-forward lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestCarryForwardResource::class.java)
    }
}
