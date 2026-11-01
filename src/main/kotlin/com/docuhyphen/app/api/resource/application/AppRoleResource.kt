package com.docuhyphen.app.api.resource.application

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.LastAppAdminException
import com.docuhyphen.app.api.resource.application.operations.AppRoleResourceOperations
import com.docuhyphen.app.api.resource.model.AppAdminDto
import com.docuhyphen.app.api.resource.model.AppUserSearchResultDto
import com.docuhyphen.app.api.resource.model.GrantAppAdminRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.AppRoleAdministrationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class AppRoleResource @Inject constructor(
    private val appRoleAdministrationService: AppRoleAdministrationService,
) : AppRoleResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppRoleResource::class.java)
        private const val FAILURE_MESSAGE = "An error occurred while managing app roles"
    }

    override fun listAppAdmins(): Response
    {
        return try
        {
            val admins = appRoleAdministrationService.listAppAdmins()
            Response.ok(object : GenericEntity<List<AppAdminDto>>(admins)
            {}).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is LastAppAdminException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing app administrators", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun grantAppAdmin(request: GrantAppAdminRequest): Response
    {
        return try
        {
            Response.status(CREATED).entity(appRoleAdministrationService.grantAppAdmin(request.appUserId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is LastAppAdminException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error granting app administrator role", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun revokeAppAdmin(assignmentId: String): Response
    {
        return try
        {
            appRoleAdministrationService.revokeAppAdmin(assignmentId)
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is LastAppAdminException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error revoking app administrator role", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun searchAppAdminCandidates(
        q: String?,
        limit: Int,
    ): Response
    {
        return try
        {
            val hits = appRoleAdministrationService.searchAppAdminCandidates(q, limit)
            Response.ok(object : GenericEntity<List<AppUserSearchResultDto>>(hits)
            {}).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is LastAppAdminException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error searching app administrator candidates", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }
}
