package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.organization.OrganizationIdentityDomainDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationIdentityDomainCreateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.OrganizationIdentityDomainResourceOperations
import com.docuhyphen.app.api.service.identity.OrganizationIdentityDomainService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationIdentityDomainResource @Inject constructor(
    private val domainService: OrganizationIdentityDomainService,
    private val dtoMapper: OrganizationIdentityDomainDtoMapper,
) : OrganizationIdentityDomainResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityDomainResource::class.java)
    }

    override fun list(organizationId: String): Response
    {
        return try
        {
            Response.ok(domainService.list(organizationId).map(dtoMapper::toResponse)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing organization identity domains", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun create(
        organizationId: String,
        request: OrganizationIdentityDomainCreateRequest,
    ): Response
    {
        return try
        {
            val created = domainService.create(organizationId, request.domain)
            Response.status(CREATED).entity(dtoMapper.toResponse(created)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating organization identity domain", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun verify(
        organizationId: String,
        domainId: String,
    ): Response
    {
        return try
        {
            val verified = domainService.verify(organizationId, domainId)
            Response.ok(dtoMapper.toResponse(verified)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error verifying organization identity domain", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun delete(
        organizationId: String,
        domainId: String,
    ): Response
    {
        return try
        {
            domainService.delete(organizationId, domainId)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting organization identity domain", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
