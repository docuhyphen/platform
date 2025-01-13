package com.securedocsshare.app.resource

import com.securedocsshare.app.api.model.CompanyRegistrationRequest
import com.securedocsshare.app.api.model.CompanyRegistrationResponse
import com.securedocsshare.app.api.model.PersonRegistrationRequest
import com.securedocsshare.app.api.model.PersonRegistrationResponse
import com.securedocsshare.app.interceptor.AuthTokenContext
import com.securedocsshare.app.service.EntityRegistrationService
import jakarta.inject.Inject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/entity-registration")
class EntityRegistrationResource @Inject constructor(
    private val entityRegistrationService: EntityRegistrationService,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        val logger = LoggerFactory.getLogger(EntityRegistrationResource::class.java)
    }

    @POST
    @Path("/person")
    @Produces(APPLICATION_JSON)
    fun registerPerson(personRegistrationRequest: PersonRegistrationRequest?): Response
    {
        return try
        {
            personRegistrationRequest?.let {

                with(it) {
                    entityRegistrationService.registerPerson(firstName, lastName, idNumber)
                }

                val personRegistrationResponse = PersonRegistrationResponse()

                Response.ok().build()
            } ?: Response.status(Response.Status.BAD_REQUEST).build()

        }
        catch (exception: Exception)
        {
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
    }

    @POST
    @Path("/company")
    @Produces(APPLICATION_JSON)
    fun registerCompany(companyRegistrationRequest: CompanyRegistrationRequest?): Response
    {
        return try
        {

            companyRegistrationRequest?.let {

                with(it) {
                    entityRegistrationService.registerCompany(name, registrationNumber)
                }

                val companyRegistrationResponse = CompanyRegistrationResponse()
                Response.ok(companyRegistrationResponse).build()

            } ?: Response.status(Response.Status.BAD_REQUEST).build()
        }
        catch (exception: Exception)
        {
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
    }
}