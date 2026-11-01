package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.docuhyphen.app.api.resource.model.AddOrganizationGroupRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationGroupRequest
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationGroupResourceOperations
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File

class OrganizationGroupResource @Inject constructor(
    private val organizationGroupService: OrganizationGroupService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
) : OrganizationGroupResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupResource::class.java)
        private const val GROUP_LOOKUP_ENDPOINT_KEY = "organization-groups"
    }

    override fun addOrganizationGroup(
        organizationId: String,
        requestId: String?,
        addOrganizationGroupRequest: AddOrganizationGroupRequest,
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

            organizationGroupService.addOrganizationGroup(
                organizationId,
                addOrganizationGroupRequest.name,
                members,
                AdminApprovalContext(requestId = requestId),
                addOrganizationGroupRequest.externallyPublished,
            )

            Response.status(CREATED).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization group creation refused by subscription policy", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding organization group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while adding an organization group"))
                        .build()
                }
            }
        }
    }

    override fun updateOrganizationGroup(
        organizationId: String,
        groupId: String,
        requestId: String?,
        updateOrganizationGroupRequest: UpdateOrganizationGroupRequest,
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

            with(updateOrganizationGroupRequest) {
                organizationGroupService.updateOrganizationGroup(
                    organizationId,
                    groupId,
                    name,
                    isActive,
                    groupMembers,
                    AdminApprovalContext(requestId = requestId),
                    externallyPublished,
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization group update refused by subscription policy", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException, is OrganizationGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating organization group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while updating an organization group"))
                        .build()
                }
            }
        }
    }

    override fun getOrganizationGroups(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            val limitedResponse = directoryLookupGuardService.enforce(
                endpointKey = GROUP_LOOKUP_ENDPOINT_KEY,
                targetOrganizationId = organizationId,
                requestId = requestId,
            )
            if (limitedResponse != null)
            {
                return limitedResponse
            }

            val groups = directoryLookupGuardService
                .capResults(organizationGroupService.getOrganizationGroupViews(organizationId))
                .toTypedArray()

            Response.ok(groups).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting organization groups", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting organization groups"))
                        .build()
                }
            }
        }
    }

    override fun deleteOrganizationGroup(
        organizationId: String?,
        groupId: String?,
        requestId: String?,
    ): Response
    {
        return try
        {
            organizationGroupService.deleteOrganizationGroup(
                organizationId,
                groupId,
                AdminApprovalContext(requestId = requestId),
            )

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization group deletion refused by subscription policy", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException,
                is OrganizationGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting organization group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deleting organization group"))
                        .build()
                }
            }
        }
    }

    override fun uploadOrganizationGroupIcon(
        file: File?,
        contentType: String?,
        organizationId: String,
        groupId: String,
    ): Response
    {
        return try
        {
            Response.ok(
                organizationGroupService.updateOrganizationGroupIcon(organizationId, groupId, file, contentType),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is OrganizationGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error uploading organization group icon", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while uploading the group icon"))
                        .build()
                }
            }
        }
    }

    override fun deleteOrganizationGroupIcon(
        organizationId: String,
        groupId: String,
    ): Response
    {
        return try
        {
            Response.ok(organizationGroupService.removeOrganizationGroupIcon(organizationId, groupId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is OrganizationGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error removing organization group icon", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while removing the group icon"))
                        .build()
                }
            }
        }
    }
}
