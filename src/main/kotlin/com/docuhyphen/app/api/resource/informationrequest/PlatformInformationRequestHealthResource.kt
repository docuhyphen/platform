package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestHealthDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHealthService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/information-request-health")
@Produces(APPLICATION_JSON)
class PlatformInformationRequestHealthResource @Inject constructor(
    private val healthService: InformationRequestHealthService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun get(@HeaderParam("X-Request-Id") requestId: String?): Response
    {
        return try
        {
            val report = healthService.reportFor(accessContextFactory.currentAuthenticated().principal, requestId)
            Response.ok(InformationRequestHealthDtoMapper.toDto(report)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request health report failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(PlatformInformationRequestHealthResource::class.java)
    }
}
