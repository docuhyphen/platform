package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.organization.OrganizationIdpSecretLifecycleDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretActivateRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRetireRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationIdentityProviderSecretResourceOperations
import com.docuhyphen.app.api.service.identity.OrganizationIdpSecretLifecycleService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationIdentityProviderSecretResource @Inject constructor(
    private val organizationIdpSecretLifecycleService: OrganizationIdpSecretLifecycleService,
) : OrganizationIdentityProviderSecretResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderSecretResource::class.java)
    }

    override fun rotateSecret(
        organizationId: String,
        configId: String,
        requestId: String?,
        payload: OrganizationIdpSecretRotateRequest,
    ): Response
    {
        return try
        {
            val outcome = organizationIdpSecretLifecycleService.rotateClientSecret(
                organizationId = organizationId,
                configId = configId,
                newSecret = payload.newClientSecret,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(OrganizationIdpSecretLifecycleDtoMapper.toRotationResponse(outcome)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider secret rotation refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error rotating org IdP secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun activateVersion(
        organizationId: String,
        configId: String,
        requestId: String?,
        payload: OrganizationIdpSecretActivateRequest,
    ): Response
    {
        return try
        {
            organizationIdpSecretLifecycleService.activateClientSecretVersion(
                organizationId = organizationId,
                configId = configId,
                versionId = payload.versionId,
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
                    logger.warn("Organization identity-provider secret activation refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error activating org IdP secret version", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun rollbackToPreviousVersion(
        organizationId: String,
        configId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            val outcome = organizationIdpSecretLifecycleService.rollbackClientSecretToPreviousVersion(
                organizationId = organizationId,
                configId = configId,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(OrganizationIdpSecretLifecycleDtoMapper.toRollbackResponse(outcome)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider secret rollback refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error rolling back org IdP secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun getSecretStatus(
        organizationId: String,
        configId: String,
    ): Response
    {
        return try
        {
            val status = organizationIdpSecretLifecycleService.getClientSecretStatus(
                organizationId = organizationId,
                configId = configId,
            )
            Response.ok(OrganizationIdpSecretLifecycleDtoMapper.toStatusResponse(status)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException ->
                {
                    logger.warn("Organization identity-provider secret status refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching org IdP secret status", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun disableSecret(
        organizationId: String,
        configId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            organizationIdpSecretLifecycleService.disableClientSecret(
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
                    logger.warn("Organization identity-provider secret disabling refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error disabling org IdP secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun enableSecret(
        organizationId: String,
        configId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            organizationIdpSecretLifecycleService.enableClientSecret(
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
                    logger.warn("Organization identity-provider secret enabling refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error enabling org IdP secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun retireSecret(
        organizationId: String,
        configId: String,
        requestId: String?,
        payload: OrganizationIdpSecretRetireRequest,
    ): Response
    {
        return try
        {
            organizationIdpSecretLifecycleService.retireClientSecret(
                organizationId = organizationId,
                configId = configId,
                recoveryWindowDays = payload.recoveryWindowDays,
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
                    logger.warn("Organization identity-provider secret retirement refused by subscription policy", exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error retiring org IdP secret", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}




