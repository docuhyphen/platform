package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationAppUserRequest
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.OrganizationAppUserResourceOperations
import com.docuhyphen.app.api.service.organization.OrganizationAppUserService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class OrganizationAppUserResource @Inject constructor(
    private val organizationAppUserService: OrganizationAppUserService,
    private val organizationMembershipService: OrganizationMembershipService,
) : OrganizationAppUserResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAppUserResource::class.java)
    }

    override fun addAppUser(
        organizationId: String,
        requestId: String?,
        addOrganizationAppUserRequest: AddOrganizationAppUserRequest,
    ): Response
    {
        return try
        {
            val appUser = with(addOrganizationAppUserRequest) {
                organizationAppUserService.addAppUser(
                    organizationId,
                    roles,
                    email,
                    person?.firstName,
                    person?.lastName,
                    AdminApprovalContext(requestId = requestId),
                )
            }

            Response
                .status(CREATED)
                .entity(DetailedEntityToDtoTransformer.toPublicDto(appUser))
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.error("Subscription denied while adding an organization app user", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding organization app user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while adding an organization app user"))
                        .build()
                }
            }
        }
    }

    override fun getAppUsers(organizationId: String?): Response
    {
        return try
        {
            val members = organizationAppUserService.getAppUsers(organizationId)
            val roles = organizationMembershipService.rolesOf(UUID.fromString(organizationId))
            val appUsers = members
                .map { DetailedEntityToDtoTransformer.toPublicDto(it, roles[it.id].orEmpty()) }
                .toTypedArray()

            Response.ok(appUsers).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.error("Subscription denied while getting organization app users", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error getting organization app users", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while getting organization app users"))
                        .build()
                }
            }
        }
    }

    override fun updateAppUser(
        organizationId: String?,
        appUserId: String?,
        requestId: String?,
        updateOrganizationAppUserRequest: UpdateOrganizationAppUserRequest,
    ): Response
    {
        return try
        {
            with(updateOrganizationAppUserRequest)
            {
                organizationAppUserService.updateAppUser(
                    organizationId,
                    appUserId,
                    rolesToAdd,
                    rolesToRemove,
                    isActive,
                    email,
                    this.person?.firstName,
                    this.person?.lastName,
                    AdminApprovalContext(requestId = requestId),
                )
            }

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Subscription denied while changing an organization app user", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException,
                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating organization app user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while updating an organization app user"))
                        .build()
                }
            }
        }
    }

    override fun deleteAppUser(
        organizationId: String?,
        appUserId: String?,
        requestId: String?,
    ): Response
    {
        return try
        {
            organizationAppUserService.deleteAppUser(
                organizationId,
                appUserId,
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
                    logger.warn("Subscription denied while removing an organization app user", exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is OrganizationNotFoundException,
                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deactivating organization app user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deactivating an organization app user"))
                        .build()
                }
            }
        }
    }

    override fun doCheck(
        check: String?,
        organizationId: String?,
        appUserId: String?,
    ): Response
    {
        return try
        {
            if (organizationAppUserService.passesCheck(organizationId, appUserId, check))
            {
                Response.ok().build()
            }
            else
            {
                Response.status(CONFLICT).build()
            }
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                else ->
                {
                    logger.error("Error checking organization app user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while checking organization app user."))
                        .build()
                }
            }
        }
    }
}
