package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.OrganizationLinkNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationSharingSessionLinkService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations/links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationSharingSessionLinkResource @Inject constructor(
    private val linkService: OrganizationSharingSessionLinkService,
    private val configurationService: ConfigurationService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationSharingSessionLinkResource::class.java)
    }

    @GET
    @Path("/linking")
    fun getOrganizationsForLinking(
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val limitedResponse = directoryLookupGuardService.enforce(
                endpointKey = "organizations-linking",
                targetOrganizationId = null,
                requestId = requestId,
            )
            if (limitedResponse != null)
            {
                return limitedResponse
            }

            val organizations = linkService.getOrganizationsForLinking()
                .map { org -> BasicEntityToDtoTransformer.toDto(org) }
                .take(configurationService.getDirectoryLookupMaxResults())
                .toTypedArray()

            if (organizations.isNotEmpty())
            {
                Response.ok(organizations).build()
            }
            else
            {
                Response.noContent().build()
            }
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching organizations for linking", exception)

            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while fetching organizations for linking")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @POST
    fun createLink(
        @QueryParam("requestingOrganizationId") requestingOrganizationId: String?,
        @QueryParam("requestedOrganizationId") requestedOrganizationId: String?,
        @QueryParam("message") message: String? = null,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val adminApprovalContext = buildAdminApprovalContext(stepUpAuth, dualApprovalId, requestId)
            val link = linkService.createLink(
                requestingOrganizationId, requestedOrganizationId, message, adminApprovalContext
            )

            Response.ok(BasicEntityToDtoTransformer.toDto(link)).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error creating organization link", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    errorResponse(NOT_FOUND, exception.message)
                }

                is IllegalArgumentException ->
                {
                    errorResponse(BAD_REQUEST, exception.message)
                }

                else ->
                {
                    errorResponse(INTERNAL_SERVER_ERROR, "An error occurred while creating the organization link")
                }
            }
        }
    }

    @GET
    fun getLinks(): Response
    {
        return try
        {
            linkService.getLinksByCurrentAppUser()?.let {

                if (it.isNotEmpty())
                {
                    val orgLinks = it
                        .map { orgLink -> BasicEntityToDtoTransformer.toDto(orgLink) }
                        .toTypedArray()

                    Response.ok(orgLinks).build()
                }
                else
                {
                    Response.noContent().build()
                }

            } ?: Response.noContent().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching organization links", exception)

            when (exception)
            {
                is UnauthorizedException ->
                {
                    errorResponse(UNAUTHORIZED, exception.message)
                }

                is OrganizationNotFoundException,
                is IllegalArgumentException ->
                {
                    errorResponse(BAD_REQUEST, exception.message)
                }

                else ->
                {
                    errorResponse(INTERNAL_SERVER_ERROR, "An error occurred while fetching organization links")
                }
            }
        }
    }

    @PUT
    @Path("{linkId}")
    fun acceptLinkOrDecline(
        @PathParam("linkId") linkId: String?,
        @QueryParam("status") status: LinkStatus?,
        @QueryParam("rejectionReason") rejectionReason: String?,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val adminApprovalContext = buildAdminApprovalContext(stepUpAuth, dualApprovalId, requestId)
            linkService.acceptLink(linkId, status, rejectionReason, adminApprovalContext)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization link status", exception)

            when (exception)
            {
                is OrganizationLinkNotFoundException ->
                {
                    errorResponse(NOT_FOUND, exception.message)
                }

                is IllegalArgumentException ->
                {
                    errorResponse(BAD_REQUEST, exception.message)
                }

                else ->
                {
                    errorResponse(INTERNAL_SERVER_ERROR, "An error occurred while updating the organization link")
                }
            }
        }
    }

    @Path("/{linkId}")
    @DELETE
    fun deLink(
        @PathParam("linkId") linkId: String?,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val adminApprovalContext = buildAdminApprovalContext(stepUpAuth, dualApprovalId, requestId)
            linkService.deLink(linkId, adminApprovalContext)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error removing organization link", exception)

            when (exception)
            {
                is OrganizationLinkNotFoundException ->
                {
                    errorResponse(NOT_FOUND, exception.message)
                }

                is IllegalArgumentException ->
                {
                    errorResponse(BAD_REQUEST, exception.message)
                }

                else ->
                {
                    errorResponse(INTERNAL_SERVER_ERROR, "An error occurred while removing the organization link")
                }
            }
        }
    }

    private fun buildAdminApprovalContext(
        stepUpAuth: String?,
        dualApprovalId: String?,
        requestId: String?,
    ): AdminApprovalContext
    {
        return AdminApprovalContext(
            stepUpAuthenticated = stepUpAuth.equals("true", ignoreCase = true),
            dualApprovalId = dualApprovalId,
            requestId = requestId,
        )
    }

    private fun errorResponse(status: Response.Status, message: String?): Response
    {
        return Response.status(status).entity(ResponseError(message)).build()
    }
}