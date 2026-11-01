package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.organization.OrganizationIdentityProviderConfigDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationIdentityProviderConfigResourceOperations
import com.docuhyphen.app.api.service.identity.OrganizationIdentityProviderConfigService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationIdentityProviderConfigResource @Inject constructor(
    private val organizationIdentityProviderConfigService: OrganizationIdentityProviderConfigService,
) : OrganizationIdentityProviderConfigResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderConfigResource::class.java)
    }

    override fun list(organizationId: String): Response
    {
        return try
        {
            val response = organizationIdentityProviderConfigService.list(organizationId)
                .map(OrganizationIdentityProviderConfigDtoMapper::toResponse)
            Response.ok(response).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider listing refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing org IdP configs", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun create(
        organizationId: String,
        requestId: String?,
        payload: OrganizationIdpConfigRequest,
    ): Response
    {
        return try
        {
            val created = organizationIdentityProviderConfigService.create(
                organizationId = organizationId,
                request = payload,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(OrganizationIdentityProviderConfigDtoMapper.toResponse(created)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider creation refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating org IdP config", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun update(
        organizationId: String,
        configId: String,
        requestId: String?,
        payload: OrganizationIdpConfigRequest,
    ): Response
    {
        return try
        {
            val updated = organizationIdentityProviderConfigService.update(
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

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider update refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating org IdP config", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun delete(
        organizationId: String,
        configId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            organizationIdentityProviderConfigService.delete(
                organizationId = organizationId,
                configId = configId,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider deletion refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting org IdP config", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}

