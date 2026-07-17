package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.dto.OrganizationSettingsDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.SettingsService
import com.docuhyphen.app.api.service.organization.OrganizationService
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
    private val settingsService: SettingsService,
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
        @HeaderParam("X-Request-Id") requestId: String?,
        updateOrganizationRequest: UpdateOrganizationRequest
    ): Response
    {
        return try
        {
            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )

            with(updateOrganizationRequest)
            {
                organizationService.updateOrganization(
                    organizationId,
                    name,
                    registrationNumber,
                    adminApprovalContext,
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
        @HeaderParam("X-Request-Id") requestId: String?,
        settingsDto: OrganizationSettingsDto
    ): Response
    {
        return try
        {
            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )

            settingsService.updateOrganizationSettings(organizationId, settingsDto, adminApprovalContext)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
