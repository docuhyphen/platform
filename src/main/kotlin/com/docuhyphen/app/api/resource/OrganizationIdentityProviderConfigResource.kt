package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.OrganizationIdentityProviderConfigService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("organizations/{organizationId}/identity-providers")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationIdentityProviderConfigResource @Inject constructor(
    private val organizationIdentityProviderConfigService: OrganizationIdentityProviderConfigService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderConfigResource::class.java)
    }

    @GET
    fun list(@PathParam("organizationId") organizationId: String): Response
    {
        return try
        {
            val response = organizationIdentityProviderConfigService.list(organizationId)
                .map { it.toResponse() }
            Response.ok(response).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error listing org IdP configs", e)
        }
    }

    @POST
    fun create(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
            Response.ok(created.toResponse()).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error creating org IdP config", e)
        }
    }

    @PUT
    @Path("/{configId}")
    fun update(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
            Response.ok(updated.toResponse()).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error updating org IdP config", e)
        }
    }

    @DELETE
    @Path("/{configId}")
    fun delete(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error deleting org IdP config", e)
        }
    }


    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)

        return when (exception)
        {
            is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
            is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
            else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
        }
    }

    private fun com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig.toResponse(): OrganizationIdpConfigResponse
    {
        return OrganizationIdpConfigResponse(
            id = id.toString(),
            organizationId = organization?.id?.toString().orEmpty(),
            provider = provider,
            clientId = clientId,
            clientSecretRef = clientSecretRef,
            tenantId = tenantId,
            scopes = splitCsv(scopes),
            isActive = isActive,
            accessTokenExpiryMinutes = accessTokenExpiryMinutes,
            refreshTokenExpiryMinutes = refreshTokenExpiryMinutes,
            maxSessionDurationHours = maxSessionDurationHours,
            idleTimeoutMinutes = idleTimeoutMinutes,
            oidcIssuer = oidcIssuer,
            allowedAudiences = splitCsv(allowedAudiences),
            allowedAlgs = splitCsv(allowedAlgs),
            requiredClaims = splitCsv(requiredClaims),
            createdDate = createdDate.toString(),
            updatedDate = updatedDate.toString(),
        )
    }

    private fun splitCsv(value: String?): List<String>
    {
        return value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}

