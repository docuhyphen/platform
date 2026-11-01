package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.organization.OrganizationIdpSecretRotationRunbookDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationIdentityProviderRotationRunbookResourceOperations
import com.docuhyphen.app.api.service.identity.OrganizationIdpSecretRotationRunbookService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationIdentityProviderRotationRunbookResource @Inject constructor(
    private val rotationRunbookService: OrganizationIdpSecretRotationRunbookService,
) : OrganizationIdentityProviderRotationRunbookResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderRotationRunbookResource::class.java)
    }

    override fun getRotationStatus(organizationId: String): Response
    {
        return try
        {
            val status = rotationRunbookService.getRotationStatus(organizationId)
            Response.ok(OrganizationIdpSecretRotationRunbookDtoMapper.toStatusResponse(status)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching emergency organization IdP secret rotation status", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun previewEmergencyRotation(organizationId: String): Response
    {
        return try
        {
            val preview = rotationRunbookService.previewEmergencyRotationCandidates(organizationId)
            Response.ok(OrganizationIdpSecretRotationRunbookDtoMapper.toPreviewResponse(preview)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error previewing emergency organization IdP secret rotation", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun runEmergencyRotation(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            val result = rotationRunbookService.runEmergencyRotation(
                organizationId = organizationId,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(OrganizationIdpSecretRotationRunbookDtoMapper.toRunResponse(result)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error running emergency organization IdP secret rotation", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}









