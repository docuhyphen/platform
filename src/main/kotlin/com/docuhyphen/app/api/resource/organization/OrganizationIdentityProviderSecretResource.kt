package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretActivateRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRetireRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRollbackResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotateRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretStatusResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.OrganizationIdpSecretLifecycleService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory

@Path("organizations/{organizationId}/identity-providers/{configId}/secrets")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationIdentityProviderSecretResource @Inject constructor(
    private val organizationIdpSecretLifecycleService: OrganizationIdpSecretLifecycleService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderSecretResource::class.java)
    }

    @POST
    @Path("/rotate")
    fun rotateSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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

            Response.ok(
                OrganizationIdpSecretRotationResponse(
                    secretRef = outcome.secretRef,
                    previousVersionId = outcome.previousVersionId,
                    activeVersionId = outcome.activeVersionId,
                    overlapUntilEpochMillis = outcome.overlapUntilEpochMillis,
                )
            ).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error rotating org IdP secret", e)
        }
    }

    @POST
    @Path("/activate")
    fun activateVersion(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error activating org IdP secret version", e)
        }
    }

    @POST
    @Path("/rollback")
    fun rollbackToPreviousVersion(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val outcome = organizationIdpSecretLifecycleService.rollbackClientSecretToPreviousVersion(
                organizationId = organizationId,
                configId = configId,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )

            Response.ok(
                OrganizationIdpSecretRollbackResponse(
                    secretRef = outcome.secretRef,
                    rolledBackToVersionId = outcome.rolledBackToVersionId,
                    previousCurrentVersionId = outcome.previousCurrentVersionId,
                )
            ).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error rolling back org IdP secret", e)
        }
    }

    @GET
    @Path("/status")
    fun getSecretStatus(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
    ): Response
    {
        return try
        {
            val status = organizationIdpSecretLifecycleService.getClientSecretStatus(
                organizationId = organizationId,
                configId = configId,
            )

            Response.ok(
                OrganizationIdpSecretStatusResponse(
                    secretRef = status.secretRef,
                    disabled = status.disabled,
                    rotationPhase = status.rotationPhase,
                    currentVersionId = status.currentVersionId,
                    previousVersionId = status.previousVersionId,
                    pendingVersionId = status.pendingVersionId,
                    overlapUntilEpochMillis = status.overlapUntilEpochMillis,
                    overlapActive = status.overlapActive,
                )
            ).build()
        }
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error fetching org IdP secret status", e)
        }
    }

    @POST
    @Path("/disable")
    fun disableSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error disabling org IdP secret", e)
        }
    }

    @POST
    @Path("/enable")
    fun enableSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error enabling org IdP secret", e)
        }
    }

    @DELETE
    fun retireSecret(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
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
        catch (e: Exception)
        {
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            handleException("Error retiring org IdP secret", e)
        }
    }


    private fun handleException(message: String, exception: Exception): Response
    {
        logger.error(message, exception)

        return when (exception)
        {
            is UnauthorizedException ->
            {
                Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
            }

            is IllegalArgumentException ->
            {
                Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
            }

            else ->
            {
                Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred"))
                    .build()
            }
        }
    }
}




