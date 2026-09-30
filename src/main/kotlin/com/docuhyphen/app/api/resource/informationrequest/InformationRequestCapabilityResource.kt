package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestCapabilityDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapabilityService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-capabilities")
@Produces(APPLICATION_JSON)
class InformationRequestCapabilityResource @Inject constructor(
    private val capabilityService: InformationRequestCapabilityService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun get(): Response
    {
        return try
        {
            val capabilities = capabilityService.forCaller(accessContextFactory.currentAuthenticated())
            Response.ok(InformationRequestCapabilityDtoMapper.toDto(capabilities)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request capability discovery failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestCapabilityResource::class.java)
    }
}
