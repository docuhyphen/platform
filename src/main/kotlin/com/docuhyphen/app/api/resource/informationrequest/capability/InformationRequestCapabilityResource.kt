package com.docuhyphen.app.api.resource.informationrequest.capability

import com.docuhyphen.app.api.model.InformationRequestCapabilityDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.capability.operations.InformationRequestCapabilityResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestCapabilityResource @Inject constructor(
    private val capabilityService: InformationRequestCapabilityService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestCapabilityResourceOperations
{
    override fun get(): Response
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
