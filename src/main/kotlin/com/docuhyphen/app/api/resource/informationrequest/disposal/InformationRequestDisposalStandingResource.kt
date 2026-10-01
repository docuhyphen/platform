package com.docuhyphen.app.api.resource.informationrequest.disposal

import com.docuhyphen.app.api.model.RecordPreservationDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.disposal.operations.InformationRequestDisposalStandingResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestDisposalStandingResource @Inject constructor(
    private val disposals: InformationRequestDisposalQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestDisposalStandingResourceOperations
{
    override fun get(id: String): Response
    {
        return try
        {
            val standing = disposals.standing(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated()
            )
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
