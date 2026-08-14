package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.mapper.OrganizationIdentityDomainDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationIdentityDomainCreateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.OrganizationIdentityDomainService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("organizations/{organizationId}/identity-domains")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationIdentityDomainResource @Inject constructor(
    private val domainService: OrganizationIdentityDomainService,
    private val dtoMapper: OrganizationIdentityDomainDtoMapper,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityDomainResource::class.java)
    }

    @GET
    fun list(@PathParam("organizationId") organizationId: String): Response
    {
        return try
        {
            Response.ok(domainService.list(organizationId).map(dtoMapper::toResponse)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error listing organization identity domains", exception)
        }
    }

    @POST
    fun create(
        @PathParam("organizationId") organizationId: String,
        request: OrganizationIdentityDomainCreateRequest,
    ): Response
    {
        return try
        {
            val created = domainService.create(organizationId, request.domain)
            Response.status(Response.Status.CREATED).entity(dtoMapper.toResponse(created)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error creating organization identity domain", exception)
        }
    }

    @POST
    @Path("/{domainId}/verification")
    fun verify(
        @PathParam("organizationId") organizationId: String,
        @PathParam("domainId") domainId: String,
    ): Response
    {
        return try
        {
            val verified = domainService.verify(organizationId, domainId)
            Response.ok(dtoMapper.toResponse(verified)).build()
        }
        catch (exception: Exception)
        {
            handleException("Error verifying organization identity domain", exception)
        }
    }

    @DELETE
    @Path("/{domainId}")
    fun delete(
        @PathParam("organizationId") organizationId: String,
        @PathParam("domainId") domainId: String,
    ): Response
    {
        return try
        {
            domainService.delete(organizationId, domainId)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            handleException("Error deleting organization identity domain", exception)
        }
    }

    private fun handleException(message: String, exception: Exception): Response
    {
        if (exception is SubscriptionDenialException)
        {
            throw exception
        }
        logger.error(message, exception)
        return when (exception)
        {
            is UnauthorizedException -> Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(exception.message)).build()
            is IllegalStateException -> Response.status(Response.Status.CONFLICT)
                .entity(ResponseError(exception.message)).build()
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("An unexpected error occurred")).build()
        }
    }
}
