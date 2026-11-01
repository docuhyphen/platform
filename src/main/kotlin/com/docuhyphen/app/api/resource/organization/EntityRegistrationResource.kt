package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.InvalidOrganizationRegistrationException
import com.docuhyphen.app.api.exception.InvalidPersonRegistrationException
import com.docuhyphen.app.api.exception.OrganizationAlreadyExistsException
import com.docuhyphen.app.api.exception.PersonAlreadyExistsException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.OrganizationRegistrationRequest
import com.docuhyphen.app.api.resource.model.PersonRegistrationRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.EntityRegistrationResourceOperations
import com.docuhyphen.app.api.service.organization.EntityRegistrationService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class EntityRegistrationResource @Inject constructor(
    private val entityRegistrationService: EntityRegistrationService,
) : EntityRegistrationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(EntityRegistrationResource::class.java)
    }

    override fun registerPerson(personRegistrationRequest: PersonRegistrationRequest?): Response
    {
        return try
        {
            personRegistrationRequest?.let {
                val person = with(it) {
                    entityRegistrationService.registerPerson(firstName, lastName, idNumber, idType)
                }
                Response.ok(DetailedEntityToDtoTransformer.toDto(person)).build()
            } ?: Response.status(BAD_REQUEST).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is PersonAlreadyExistsException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is InvalidPersonRegistrationException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error registering person", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while registering organization."))
                        .build()
                }
            }
        }
    }

    override fun registerOrganization(organizationRegistrationRequest: OrganizationRegistrationRequest?): Response
    {
        return try
        {
            organizationRegistrationRequest?.let {
                val organization = with(it) {
                    entityRegistrationService.registerOrganization(name, registrationNumber, phoneNumber, email)
                }
                Response.ok(BasicEntityToDtoTransformer.toDto(organization)).build()
            } ?: Response.status(BAD_REQUEST).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is InvalidOrganizationRegistrationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is OrganizationAlreadyExistsException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error registering organization", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while registering organization."))
                        .build()
                }
            }
        }
    }
}
