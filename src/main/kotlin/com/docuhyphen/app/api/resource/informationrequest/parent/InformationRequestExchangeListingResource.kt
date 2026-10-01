package com.docuhyphen.app.api.resource.informationrequest.parent

import com.docuhyphen.app.api.model.InformationRequestSummaryDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.parent.operations.InformationRequestExchangeListingResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeSummaryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestExchangeListingResource @Inject constructor(
    private val summaryService: InformationRequestExchangeSummaryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestExchangeListingResourceOperations
{
    override fun list(exchangeId: String): Response
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
