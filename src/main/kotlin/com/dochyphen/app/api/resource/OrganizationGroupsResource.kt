package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.model.DetailedEntityToDtoTransformer
import com.dochyphen.app.api.model.resourceservice.MemberPermissionsModel
import com.dochyphen.app.api.model.resourceservice.OrganizationGroupMemberModel
import com.dochyphen.app.api.resource.model.AddOrganizationGroupRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.OrganizationGroupService
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
class OrganizationGroupsResource @Inject constructor(
    private val organizationGroupService: OrganizationGroupService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationGroupsResource::class.java)
    }

    @Path("/{organizationId}/groups")
    @POST
    @Transactional
    fun addOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        addOrganizationGroupRequest: AddOrganizationGroupRequest
    ): Response
    {
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

            organizationGroupService.addOrganizationGroup(
                organizationId,
                addOrganizationGroupRequest.name,
                members
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

    @Path("/{organizationId}/groups")
    @GET
    fun getOrganizationGroups(
        @PathParam("organizationId") organizationId: String
    ): Response
    {
        return try
        {
            var groups = organizationGroupService
                .getOrganizationGroups(organizationId)
                .map { DetailedEntityToDtoTransformer.toDto(it) }
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
}