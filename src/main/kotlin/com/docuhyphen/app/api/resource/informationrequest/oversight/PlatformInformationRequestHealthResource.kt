package com.docuhyphen.app.api.resource.informationrequest.oversight

import com.docuhyphen.app.api.model.InformationRequestHealthDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.oversight.operations.PlatformInformationRequestHealthResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestHealthService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class PlatformInformationRequestHealthResource @Inject constructor(
    private val healthService: InformationRequestHealthService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : PlatformInformationRequestHealthResourceOperations
{
    override fun get(requestId: String?): Response
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
