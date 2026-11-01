package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.auth.operations.UserSessionResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UserSessionListResponse
import com.docuhyphen.app.api.service.auth.UserSessionManagementService
import com.docuhyphen.app.api.service.auth.UserSessionNotFoundException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class UserSessionResource @Inject constructor(
    private val userSessionManagementService: UserSessionManagementService,
) : UserSessionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserSessionResource::class.java)
    }

    override fun listSessions(): Response
    {
        return try
        {
            val sessions = userSessionManagementService.listSessions()
            Response.ok(UserSessionListResponse(sessions = sessions, total = sessions.size)).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error listing sessions", exception)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to list sessions."))
                .build()
        }
    }

    override fun revokeSession(sessionId: String): Response
    {
        return try
        {
            userSessionManagementService.revokeSession(sessionId)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is UserSessionNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error revoking session", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to revoke session."))
                        .build()
                }
            }
        }
    }

    override fun deleteSessionRecord(sessionId: String): Response
    {
        return try
        {
            userSessionManagementService.deleteSessionRecord(sessionId)
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is UserSessionNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting session record", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to delete session record."))
                        .build()
                }
            }
        }
    }
}
