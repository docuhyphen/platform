package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.InvalidOrganizationRegistrationException
import com.dochyphen.app.api.exception.InvalidPersonRegistrationException
import com.dochyphen.app.api.exception.OrganizationAlreadyExistsException
import com.dochyphen.app.api.exception.PersonAlreadyExistsException
import com.dochyphen.app.api.model.BasicModelConverter
import com.dochyphen.app.api.model.entity.DetailedModelConverter
import com.dochyphen.app.api.resource.model.OrganizationRegistrationRequest
import com.dochyphen.app.api.resource.model.PersonRegistrationRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.EntityRegistrationService
import jakarta.inject.Inject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
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
        ResourceEndpointDelayHelper.delayEndpoint(3500, 6000)

        return try
        {
            personRegistrationRequest?.let {

                val person = with(it) {
                    entityRegistrationService.registerPerson(firstName, lastName, idNumber, idType)
                }

                Response.ok(DetailedModelConverter.toDto(person)).build()
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
                    val responseError = ResponseError("An error occurred while registering organization.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    @Path("/organization")
    @Produces(APPLICATION_JSON)
    fun registerOrganization(organizationRegistrationRequest: OrganizationRegistrationRequest?): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(3500, 6000)

        return try
        {
            organizationRegistrationRequest?.let {

                val organization = with(it) {
                    entityRegistrationService.registerOrganization(name, registrationNumber, phoneNumber, email)
                }

                Response.ok(BasicModelConverter.toDto(organization)).build()

            } ?: Response.status(BAD_REQUEST).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InvalidOrganizationRegistrationException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                is OrganizationAlreadyExistsException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(CONFLICT).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while registering organization.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}