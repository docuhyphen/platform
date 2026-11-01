package com.docuhyphen.app.api.resource.scim

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.ScimDtoMapper
import com.docuhyphen.app.api.model.dto.ScimPatchRequest
import com.docuhyphen.app.api.model.dto.ScimUser
import com.docuhyphen.app.api.resource.scim.operations.ScimUserResourceOperations
import com.docuhyphen.app.api.service.scim.ScimRequestException
import com.docuhyphen.app.api.service.scim.ScimUserService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class ScimUserResource @Inject constructor(
    private val scimUserService: ScimUserService,
) : ScimUserResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ScimUserResource::class.java)
        private const val FAILURE_DETAIL = "SCIM request failed"
    }

    override fun list(
        authorization: String?,
        filter: String?,
        startIndex: Int?,
        count: Int?,
    ): Response
    {
        return try
        {
            Response.ok(scimUserService.list(authorization, filter, startIndex, count)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error listing SCIM users", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

    override fun getById(
        authorization: String?,
        id: String,
    ): Response
    {
        return try
        {
            Response.ok(scimUserService.getById(authorization, id)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error fetching SCIM user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

    override fun create(
        authorization: String?,
        payload: ScimUser,
    ): Response
    {
        return try
        {
            Response.status(CREATED).entity(scimUserService.create(authorization, payload)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error creating SCIM user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

    override fun replace(
        authorization: String?,
        id: String,
        payload: ScimUser,
    ): Response
    {
        return try
        {
            Response.ok(scimUserService.replace(authorization, id, payload)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error replacing SCIM user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

    override fun patch(
        authorization: String?,
        id: String,
        payload: ScimPatchRequest,
    ): Response
    {
        return try
        {
            Response.ok(scimUserService.patch(authorization, id, payload)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error patching SCIM user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

    override fun delete(
        authorization: String?,
        id: String,
    ): Response
    {
        return try
        {
            scimUserService.deprovision(authorization, id)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is SubscriptionDenialException -> throw exception

                is ScimRequestException ->
                    Response.status(exception.statusCode)
                        .entity(ScimDtoMapper.toError(exception.statusCode, exception.detail, exception.scimType))
                        .build()

                else ->
                {
                    logger.error("Error deprovisioning SCIM user", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ScimDtoMapper.toError(INTERNAL_SERVER_ERROR.statusCode, FAILURE_DETAIL, null))
                        .build()
                }
            }
        }
    }

}
