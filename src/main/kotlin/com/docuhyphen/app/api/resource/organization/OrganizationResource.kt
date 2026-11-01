package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.OrganizationSettingsDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationRequest
import com.docuhyphen.app.api.resource.organization.operations.OrganizationResourceOperations
import com.docuhyphen.app.api.service.application.SettingsService
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationResource @Inject constructor(
    private val organizationService: OrganizationService,
    private val settingsService: SettingsService,
) : OrganizationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationResource::class.java)
    }

    override fun updateOrganization(
        organizationId: String?,
        requestId: String?,
        updateOrganizationRequest: UpdateOrganizationRequest,
    ): Response
    {
        return try
        {
            with(updateOrganizationRequest)
            {
                organizationService.updateOrganization(
                    organizationId,
                    name,
                    registrationNumber,
                    AdminApprovalContext(requestId = requestId),
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Updating organization {} was refused by the subscription plan check: plan={}",
                        organizationId,
                        exception.denial.planCode
                    )
                    throw exception
                }

                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating organization", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while updating organization"))
                        .build()
                }
            }
        }
    }

    override fun updateOrganizationSettings(
        organizationId: String,
        requestId: String?,
        settingsDto: OrganizationSettingsDto,
    ): Response
    {
        return try
        {
            settingsService.updateOrganizationSettings(
                organizationId,
                settingsDto,
                AdminApprovalContext(requestId = requestId),
            )
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Updating settings for organization {} was refused by the subscription plan check: plan={}",
                        organizationId,
                        exception.denial.planCode
                    )
                    throw exception
                }

                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(exception.message ?: "Not authorized to update settings"))
                        .build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message ?: "Organization not found"))
                        .build()

                is DataIntegrityException ->
                    Response.status(BAD_REQUEST)
                        .entity(ResponseError(exception.message ?: "Invalid data format"))
                        .build()

                else ->
                {
                    logger.error("Error updating organization settings", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Server error while updating organization settings"))
                        .build()
                }
            }
        }
    }
}
