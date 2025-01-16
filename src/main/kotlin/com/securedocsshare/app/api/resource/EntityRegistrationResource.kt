package com.securedocsshare.app.api.resource

import com.securedocsshare.app.api.model.CompanyAlreadyExistsException
import com.securedocsshare.app.api.model.CompanyRegistrationRequest
import com.securedocsshare.app.api.model.CompanyRegistrationResponse
import com.securedocsshare.app.api.model.InvalidCompanyRegistrationException
import com.securedocsshare.app.api.model.InvalidPersonRegistrationException
import com.securedocsshare.app.api.model.PersonAlreadyExistsException
import com.securedocsshare.app.api.model.PersonRegistrationRequest
import com.securedocsshare.app.api.model.PersonRegistrationResponse
import com.securedocsshare.app.api.model.ResponseError
import com.securedocsshare.app.api.service.EntityRegistrationService
import jakarta.inject.Inject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("/entity-registration")
class EntityRegistrationResource @Inject constructor(
    private val entityRegistrationService: EntityRegistrationService,
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

                val person = with(it) {
                    entityRegistrationService.registerPerson(firstName, lastName, idNumber, idType)
                }

                Response.ok(PersonRegistrationResponse(person)).build()
            } ?: Response.status(BAD_REQUEST).build()

        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is PersonAlreadyExistsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                is InvalidPersonRegistrationException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(CONFLICT).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while registering company.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
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

                val company = with(it) {
                    entityRegistrationService.registerCompany(name, registrationNumber)
                }

                val companyRegistrationResponse = CompanyRegistrationResponse(company)
                Response.ok(companyRegistrationResponse).build()

            } ?: Response.status(BAD_REQUEST).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InvalidCompanyRegistrationException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                is CompanyAlreadyExistsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(CONFLICT).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while registering company.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}