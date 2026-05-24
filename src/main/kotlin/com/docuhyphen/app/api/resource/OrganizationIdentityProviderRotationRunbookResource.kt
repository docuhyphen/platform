package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationRunResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationPreviewCandidate
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationPreviewResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationStatusItemResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationStatusResponse
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.OrganizationIdpSecretRotationRunbookService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
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

@Path("organizations/{organizationId}/identity-providers/secrets/rotation")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationIdentityProviderRotationRunbookResource @Inject constructor(
    private val rotationRunbookService: OrganizationIdpSecretRotationRunbookService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdentityProviderRotationRunbookResource::class.java)
    }

    @GET
    @Path("/status")
    fun getRotationStatus(
        @PathParam("organizationId") organizationId: String,
    ): Response
    {
        return try
        {
            val status = rotationRunbookService.getRotationStatus(organizationId)
            Response.ok(
                OrganizationIdpSecretRotationStatusResponse(
                    intervalDays = status.intervalDays,
                    evaluated = status.evaluated,
                    dueCount = status.dueCount,
                    overlapActiveCount = status.overlapActiveCount,
                    disabledCount = status.disabledCount,
                    retirePhaseCount = status.retirePhaseCount,
                    items = status.items.map { item ->
                        OrganizationIdpSecretRotationStatusItemResponse(
                            configId = item.configId.toString(),
                            provider = item.provider,
                            secretRef = item.secretRef,
                            disabled = item.disabled,
                            rotationPhase = item.rotationPhase,
                            currentVersionId = item.currentVersionId,
                            previousVersionId = item.previousVersionId,
                            pendingVersionId = item.pendingVersionId,
                            overlapUntilEpochMillis = item.overlapUntilEpochMillis,
                            overlapActive = item.overlapActive,
                            due = item.due,
                            overdueByDays = item.overdueByDays,
                        )
                    },
                )
            ).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching emergency organization IdP secret rotation status", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }

    @GET
    @Path("/preview")
    fun previewEmergencyRotation(
        @PathParam("organizationId") organizationId: String,
    ): Response
    {
        return try
        {
            val preview = rotationRunbookService.previewEmergencyRotationCandidates(organizationId)
            Response.ok(
                OrganizationIdpSecretRotationPreviewResponse(
                    intervalDays = preview.intervalDays,
                    evaluated = preview.evaluated,
                    dueCount = preview.dueCount,
                    candidates = preview.candidates.map { candidate ->
                        OrganizationIdpSecretRotationPreviewCandidate(
                            configId = candidate.configId.toString(),
                            provider = candidate.provider,
                            secretRef = candidate.secretRef,
                            updatedDate = candidate.updatedDate.toString(),
                            due = candidate.due,
                            overdueByDays = candidate.overdueByDays,
                        )
                    },
                )
            ).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error previewing emergency organization IdP secret rotation", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }

    @POST
    @Path("/run")
    fun runEmergencyRotation(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val result = rotationRunbookService.runEmergencyRotation(
                organizationId = organizationId,
                adminApprovalContext = AdminApprovalContext(
                    stepUpAuthenticated = stepUpAuth.equals("true", ignoreCase = true),
                    dualApprovalId = dualApprovalId,
                    requestId = requestId,
                ),
            )

            Response.ok(
                OrganizationIdpSecretRotationRunResponse(
                    evaluated = result.evaluated,
                    rotated = result.rotated,
                    failed = result.failed,
                    skipped = result.skipped,
                )
            ).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error running emergency organization IdP secret rotation", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }
}









