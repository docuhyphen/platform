package com.dochyphen.app.api.resource

import com.dochyphen.app.api.service.OrganizationService
import jakarta.inject.Inject
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.slf4j.LoggerFactory

@Path("organization")
@Produces(MediaType.APPLICATION_JSON)
class OrganizationResource @Inject constructor(
    private val organizationService: OrganizationService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationResource::class.java)
    }
}