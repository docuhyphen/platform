package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.organization.OrganizationIdentityProviderConfigDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationAuthSessionPolicyResourceOperations
import com.docuhyphen.app.api.service.identity.OrganizationIdentityProviderConfigService
import com.docuhyphen.app.api.service.organization.OrganizationAuthSessionPolicyService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationAuthSessionPolicyResource @Inject constructor(
    private val organizationAuthSessionPolicyService: OrganizationAuthSessionPolicyService,
    private val organizationIdentityProviderConfigService: OrganizationIdentityProviderConfigService,
) : OrganizationAuthSessionPolicyResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAuthSessionPolicyResource::class.java)
    }

    override fun getEffectivePolicy(
        organizationId: String,
        appUserId: String?,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(
                organizationAuthSessionPolicyService.getEffectivePolicy(organizationId, appUserId, requestId),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching effective auth session policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun getSettings(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(organizationAuthSessionPolicyService.getSettings(organizationId, requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching auth session policy settings", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun updateIdpSessionPolicy(
        organizationId: String,
        configId: String,
        requestId: String?,
        payload: OrganizationAuthSessionPolicyUpdateRequest,
    ): Response
    {
        return try
        {
            val updated = organizationIdentityProviderConfigService.updateSessionPolicyFields(
                organizationId = organizationId,
                configId = configId,
                request = payload,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(OrganizationIdentityProviderConfigDtoMapper.toResponse(updated)).build()
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
                    logger.error("Error updating IdP auth session policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}

