package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestSummaryDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestExchangeSummaryService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/exchanges/{exchangeId}/information-requests")
@Produces(APPLICATION_JSON)
class InformationRequestExchangeListingResource @Inject constructor(
    private val summaryService: InformationRequestExchangeSummaryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("exchangeId") exchangeId: String): Response
    {
        return try
        {
            val id = InformationRequestCommandHttp.uuid(exchangeId, "exchange id")
            val listing = summaryService.listForExchange(id, accessContextFactory.currentAuthenticated())
            Response.ok(InformationRequestSummaryDtoMapper.toDto(listing)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Exchange Information Request listing failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestExchangeListingResource::class.java)
    }
}
