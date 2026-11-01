package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.model.AddPersonalGroupMembersRequest
import com.docuhyphen.app.api.resource.model.CreatePersonalGroupRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdatePersonalGroupRequest
import com.docuhyphen.app.api.resource.organization.operations.PersonalGroupResourceOperations
import com.docuhyphen.app.api.service.organization.PersonalGroupNotFoundException
import com.docuhyphen.app.api.service.organization.PersonalGroupService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File

class PersonalGroupResource @Inject constructor(
    private val personalGroupService: PersonalGroupService,
) : PersonalGroupResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PersonalGroupResource::class.java)
    }

    override fun createGroup(request: CreatePersonalGroupRequest): Response
    {
        return try
        {
            Response.status(CREATED)
                .entity(personalGroupService.create(request.name, request.description))
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error creating personal group"))
                        .build()
                }
            }
        }
    }

    override fun listGroups(): Response
    {
        return try
        {
            Response.ok(personalGroupService.list().toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing personal groups", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error listing personal groups"))
                        .build()
                }
            }
        }
    }

    override fun getGroup(groupId: String): Response
    {
        return try
        {
            Response.ok(personalGroupService.get(groupId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error getting personal group"))
                        .build()
                }
            }
        }
    }

    override fun updateGroup(
        groupId: String,
        request: UpdatePersonalGroupRequest,
    ): Response
    {
        return try
        {
            Response.ok(personalGroupService.update(groupId, request.name, request.description)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error updating personal group"))
                        .build()
                }
            }
        }
    }

    override fun addMembers(
        groupId: String,
        request: AddPersonalGroupMembersRequest,
    ): Response
    {
        return try
        {
            Response.ok(personalGroupService.addMembers(groupId, request.members)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding members to personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error adding members to personal group"))
                        .build()
                }
            }
        }
    }

    override fun removeMember(
        groupId: String,
        principalId: String,
        principalKindParam: String?,
    ): Response
    {
        return try
        {
            personalGroupService.removeMember(groupId, principalId, principalKindParam)
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error removing member from personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error removing member from personal group"))
                        .build()
                }
            }
        }
    }

    override fun deleteGroup(groupId: String): Response
    {
        return try
        {
            personalGroupService.delete(groupId)
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting personal group", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error deleting personal group"))
                        .build()
                }
            }
        }
    }

    override fun uploadGroupIcon(
        file: File?,
        contentType: String?,
        groupId: String,
    ): Response
    {
        return try
        {
            Response.ok(personalGroupService.updateIcon(groupId, file, contentType)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error uploading personal group icon", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error uploading group icon"))
                        .build()
                }
            }
        }
    }

    override fun deleteGroupIcon(groupId: String): Response
    {
        return try
        {
            Response.ok(personalGroupService.removeIcon(groupId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is PersonalGroupNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error removing personal group icon", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Error removing group icon"))
                        .build()
                }
            }
        }
    }
}

