package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.DataIntegrityException
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.model.DetailedEntityToDtoTransformer
import com.dochyphen.app.api.model.dto.OrganizationSettingsDto
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateOrganizationRequest
import com.dochyphen.app.api.service.SettingsService
import com.dochyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationResource @Inject constructor(
    private val organizationService: OrganizationService,
    private val settingsService: SettingsService
)
{

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationResource::class.java)
    }

    @Path("/{organizationId}")
    @PUT
    @Transactional
    fun updateOrganization(
        @PathParam("organizationId") organizationId: String?,
        updateOrganizationRequest: UpdateOrganizationRequest
    ): Response
    {
        return try
        {
            with(updateOrganizationRequest)
            {
                organizationService.updateOrganization(
                    organizationId,
                    name,
                    registrationNumber
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization group", exception)

            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while updating organization")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }


    @PUT
    @Path("/{organizationId}/settings")
    @Transactional
    fun updateOrganizationSettings(
        @PathParam("organizationId") organizationId: String,
        settingsDto: OrganizationSettingsDto
    ): Response
    {
        return try
        {
            val updatedSettings = settingsService.updateOrganizationSettings(organizationId, settingsDto)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization settings", exception)

            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message ?: "Not authorized to update settings")
                    Response.status(FORBIDDEN).entity(responseError).build()
                }

                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message ?: "Organization not found")
                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is DataIntegrityException ->
                {
                    val responseError = ResponseError(exception.message ?: "Invalid data format")
                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("Server error while updating organization settings")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}