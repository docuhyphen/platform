package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UserSessionDto
import com.docuhyphen.app.api.resource.model.UserSessionListResponse
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.UserSessionService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/auth/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserSessionResource @Inject constructor(
    private val userSessionService: UserSessionService,
    private val authTokenContext: AuthTokenContext,
    private val authAuditService: AuthAuditService,
    private val authenticationService: AuthenticationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserSessionResource::class.java)
    }

    @GET
    fun listSessions(): Response
    {
        return try
        {
            val appUser = authTokenContext.authToken.appUser!!
            val sessions = userSessionService.listSessions(appUser.id)
            val now = java.time.Instant.now()
            val currentSessionId = authenticationService
                .verifyAccessToken(authTokenContext.authToken.token)
                ?.let { it["exchange_id"] as? String }
            val dtos = sessions.map { s ->
                val isActive = s.isActive &&
                    s.revokedAt == null &&
                    (s.expiresAt == null || s.expiresAt!!.toInstant().isAfter(now))
                UserSessionDto(
                    sessionId = s.sessionId.toString(),
                    deviceId = s.deviceId,
                    deviceName = s.deviceName,
                    ipAddress = s.ipAddress,
                    userAgent = s.userAgent,
                    createdDate = s.createdDate.toInstant().toString(),
                    lastSeenAt = s.lastSeenAt.toInstant().toString(),
                    expiresAt = s.expiresAt?.toInstant()?.toString(),
                    revokedAt = s.revokedAt?.toInstant()?.toString(),
                    revocationReasonCode = s.revocationReasonCode,
                    isActive = isActive,
                    isCurrent = s.sessionId.toString() == currentSessionId,
                )
            }
            Response.ok(UserSessionListResponse(sessions = dtos, total = dtos.size)).build()
        }
        catch (e: Exception)
        {
            logger.error("Error listing sessions", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to list sessions."))
                .build()
        }
    }

    @DELETE
    @Path("/{sessionId}")
    fun revokeSession(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            val appUser = authTokenContext.authToken.appUser!!
            val sid = runCatching { UUID.fromString(sessionId) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Invalid session ID."))
                    .build()
            }

            if (!userSessionService.isSessionOwnedByUser(sid, appUser.id))
            {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseError("Session not found."))
                    .build()
            }

            userSessionService.revokeSession(sid, RevocationReasonCode.LOGOUT_DEVICE)
            authAuditService.emit(
                action = "EXCHANGE_REVOKE",
                outcome = "SUCCESS",
                sessionId = sid.toString(),
                actorId = appUser.id,
                reasonCode = RevocationReasonCode.LOGOUT_DEVICE,
            )
            Response.noContent().build()
        }
        catch (e: Exception)
        {
            logger.error("Error revoking session", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to revoke session."))
                .build()
        }
    }

    @DELETE
    @Path("/{sessionId}/record")
    fun deleteSessionRecord(@PathParam("sessionId") sessionId: String): Response
    {
        return try
        {
            val appUser = authTokenContext.authToken.appUser!!
            val sid = runCatching { UUID.fromString(sessionId) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Invalid session ID."))
                    .build()
            }

            if (!userSessionService.isSessionOwnedByUser(sid, appUser.id))
            {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseError("Session not found."))
                    .build()
            }

            val deleted = userSessionService.deleteInactiveSession(sid, appUser.id)
            if (!deleted)
            {
                return Response.status(Response.Status.CONFLICT)
                    .entity(ResponseError("Only ended sessions can be deleted."))
                    .build()
            }

            authAuditService.emit(
                action = "SESSION_DELETE_RECORD",
                outcome = "SUCCESS",
                sessionId = sid.toString(),
                actorId = appUser.id,
            )
            Response.noContent().build()
        }
        catch (e: Exception)
        {
            logger.error("Error deleting session record", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to delete session record."))
                .build()
        }
    }
}
