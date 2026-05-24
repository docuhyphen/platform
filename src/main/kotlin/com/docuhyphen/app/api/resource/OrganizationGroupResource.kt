package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.resourceservice.MemberPermissionsModel
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.resource.model.AddOrganizationGroupRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationGroupRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationGroupResource @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val configurationService: ConfigurationService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupResource::class.java)
    }

    @Path("/{organizationId}/groups")
    @POST
    @Transactional
    fun addOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
        addOrganizationGroupRequest: AddOrganizationGroupRequest
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {

            val members = addOrganizationGroupRequest.members?.map { member ->
                OrganizationGroupMemberModel(
                    appUserId = member.appUserId ?: throw IllegalArgumentException("Member ID cannot be null"),
                    permissions = MemberPermissionsModel(
                        allowSessionAccept = member.allowSessionAccept,
                        allowSessionReject = member.allowSessionReject,
                        allowSessionEdit = member.allowSessionEdit,
                        allowSessionDelete = member.allowSessionDelete,
                        allowSessionEnd = member.allowSessionEnd,
                        allowDocumentAddition = member.allowDocumentAddition,
                        allowDocumentDeletion = member.allowDocumentDeletion,
                        allowDocumentDownload = member.allowDocumentDownload,
                        allowDocumentUpdate = member.allowDocumentUpdate,
                        allowDocumentUpload = member.allowDocumentUpload
                    )
                )
            } ?: emptyList()

            val adminApprovalContext = AdminApprovalContext(
                stepUpAuthenticated = stepUpAuth.equals("true", ignoreCase = true),
                dualApprovalId = dualApprovalId,
                requestId = requestId,
            )

            organizationGroupService.addOrganizationGroup(
                organizationId,
                addOrganizationGroupRequest.name,
                members,
                adminApprovalContext,
            )

            return Response.status(CREATED).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error adding organization group", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while adding an organization group")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @Path("/{organizationId}/groups/{groupId}")
    @PUT
    fun updateOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
        updateOrganizationGroupRequest: UpdateOrganizationGroupRequest
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val groupMembers = updateOrganizationGroupRequest.members?.map { member ->
                OrganizationGroupMemberModel(
                    appUserId = member.appUserId ?: throw IllegalArgumentException("Member ID cannot be null"),
                    permissions = MemberPermissionsModel(
                        allowSessionAccept = member.allowSessionAccept,
                        allowSessionReject = member.allowSessionReject,
                        allowSessionEdit = member.allowSessionEdit,
                        allowSessionDelete = member.allowSessionDelete,
                        allowSessionEnd = member.allowSessionEnd,
                        allowDocumentAddition = member.allowDocumentAddition,
                        allowDocumentDeletion = member.allowDocumentDeletion,
                        allowDocumentDownload = member.allowDocumentDownload,
                        allowDocumentUpdate = member.allowDocumentUpdate,
                        allowDocumentUpload = member.allowDocumentUpload
                    )
                )
            } ?: emptyList()

            val adminApprovalContext = AdminApprovalContext(
                stepUpAuthenticated = stepUpAuth.equals("true", ignoreCase = true),
                dualApprovalId = dualApprovalId,
                requestId = requestId,
            )

            with(updateOrganizationGroupRequest) {
                organizationGroupService.updateOrganizationGroup(
                    organizationId, groupId, name, isActive, groupMembers, adminApprovalContext
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization group", exception)

            when (exception)
            {
                is OrganizationNotFoundException, is OrganizationGroupNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }
                else ->
                {
                    val responseError = ResponseError("An error occurred while updating an organization group")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    @Path("/{organizationId}/groups")
    @GET
    fun getOrganizationGroups(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val limitedResponse = directoryLookupGuardService.enforce(
                endpointKey = "organization-groups",
                targetOrganizationId = organizationId,
                requestId = requestId,
            )
            if (limitedResponse != null)
            {
                return limitedResponse
            }

            val groups = organizationGroupService
                .getOrganizationGroups(organizationId)
                .map { DetailedEntityToDtoTransformer.toDto(it) }
                .take(configurationService.getDirectoryLookupMaxResults())
                .toTypedArray()

            Response.ok(groups).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error getting organization groups", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }
                else ->
                {
                    val responseError = ResponseError("An error occurred while getting organization groups")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @DELETE
    @Path("/{organizationId}/groups/{groupId}")
    fun deleteOrganizationGroup(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("groupId") groupId: String?,
        @HeaderParam("X-Step-Up-Auth") stepUpAuth: String?,
        @HeaderParam("X-Dual-Approval-Id") dualApprovalId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val adminApprovalContext = AdminApprovalContext(
                stepUpAuthenticated = stepUpAuth.equals("true", ignoreCase = true),
                dualApprovalId = dualApprovalId,
                requestId = requestId,
            )
            organizationGroupService.deleteOrganizationGroup(organizationId, groupId, adminApprovalContext)

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error deleting organization group", exception)

            when (exception)
            {
                is OrganizationNotFoundException,
                is OrganizationGroupNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while deleting organization group")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}