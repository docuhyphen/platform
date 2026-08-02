package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
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
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.jboss.resteasy.reactive.RestForm
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64
import java.util.UUID

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationGroupResource @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val configurationService: ConfigurationService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
    private val principalGroupRepository: PrincipalGroupRepository,
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
        @HeaderParam("X-Request-Id") requestId: String?,
        addOrganizationGroupRequest: AddOrganizationGroupRequest
    ): Response
    {
        return try
        {

            val members = addOrganizationGroupRequest.members?.map { member ->
                OrganizationGroupMemberModel(
                    appUserId = member.appUserId ?: throw IllegalArgumentException("Member ID cannot be null"),
                    groupRole = member.groupRole,
                )
            } ?: emptyList()

            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )

            organizationGroupService.addOrganizationGroup(
                organizationId,
                addOrganizationGroupRequest.name,
                members,
                adminApprovalContext,
                addOrganizationGroupRequest.externallyPublished,
            )

            return Response.status(CREATED).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
        @HeaderParam("X-Request-Id") requestId: String?,
        updateOrganizationGroupRequest: UpdateOrganizationGroupRequest
    ): Response
    {
        return try
        {
            val groupMembers = updateOrganizationGroupRequest.members?.map { member ->
                OrganizationGroupMemberModel(
                    appUserId = member.appUserId ?: throw IllegalArgumentException("Member ID cannot be null"),
                    groupRole = member.groupRole,
                )
            } ?: emptyList()

            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )

            with(updateOrganizationGroupRequest) {
                organizationGroupService.updateOrganizationGroup(
                    organizationId, groupId, name, isActive, groupMembers, adminApprovalContext, externallyPublished
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
                .getOrganizationGroupViews(organizationId)
                .take(configurationService.getDirectoryLookupMaxResults())
                .toTypedArray()

            Response.ok(groups).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {

        return try
        {
            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )
            organizationGroupService.deleteOrganizationGroup(organizationId, groupId, adminApprovalContext)

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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

    @POST
    @Path("/{organizationId}/groups/{groupId}/icon")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    fun uploadOrganizationGroupIcon(
        @RestForm("file") file: File?,
        @RestForm("contentType") contentType: String?,
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
    ): Response
    {
        return try
        {
            if (file == null) return Response.status(BAD_REQUEST).entity(ResponseError("No file provided")).build()
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return Response.status(BAD_REQUEST).entity(ResponseError("Uploaded file is empty")).build()
            val mime = contentType?.takeIf { it.startsWith("image/") } ?: "image/png"
            val dataUrl = "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val gid = UUID.fromString(groupId)
            val group = principalGroupRepository.findById(gid)
                ?: return Response.status(NOT_FOUND).entity(ResponseError("Group not found")).build()
            group.iconData = dataUrl
            principalGroupRepository.save(group)
            Response.ok(organizationGroupService.getOrganizationGroupViews(organizationId).find { it.id == gid }).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
            logger.error("Error uploading organization group icon", exception)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An error occurred while uploading the group icon")).build()
        }
    }

    @DELETE
    @Path("/{organizationId}/groups/{groupId}/icon")
    @Transactional
    fun deleteOrganizationGroupIcon(
        @PathParam("organizationId") organizationId: String,
        @PathParam("groupId") groupId: String,
    ): Response
    {
        return try
        {
            val gid = UUID.fromString(groupId)
            val group = principalGroupRepository.findById(gid)
                ?: return Response.status(NOT_FOUND).entity(ResponseError("Group not found")).build()
            group.iconData = null
            principalGroupRepository.save(group)
            Response.ok(organizationGroupService.getOrganizationGroupViews(organizationId).find { it.id == gid }).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
            logger.error("Error removing organization group icon", exception)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An error occurred while removing the group icon")).build()
        }
    }
}
