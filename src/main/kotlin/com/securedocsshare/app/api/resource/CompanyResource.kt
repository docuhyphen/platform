package com.securedocsshare.app.api.resource

import com.securedocsshare.app.api.service.CompanyService
import jakarta.inject.Inject
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.slf4j.LoggerFactory

@Path("company")
@Produces(MediaType.APPLICATION_JSON)
class CompanyResource @Inject constructor(
    private val companyService: CompanyService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(CompanyResource::class.java)
    }
}