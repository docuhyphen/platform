package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NotificationResource @Inject constructor(
    private val notificationService: InAppNotificationService,
    private val authTokenContext: AuthTokenContext,
)
{
    @GET
    fun listNotifications(
        @QueryParam("limit") requestedLimit: Int?,
        @QueryParam("beforeTimestamp") beforeTimestamp: Long?,
        @QueryParam("beforeId") beforeId: String?,
    ): Response
    {
        val limit = requestedLimit ?: 20
        if (limit !in 1..100)
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Notification limit must be between 1 and 100."))
                .build()
        }
        if ((beforeTimestamp == null) != (beforeId == null))
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("Both notification cursor values are required."))
                .build()
        }
        val cursorId = beforeId?.let {
            runCatching { java.util.UUID.fromString(it) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Notification cursor is invalid."))
                    .build()
            }
        }

        val appUserId = authTokenContext.authToken.appUser!!.id
        return Response.ok(
            notificationService.listPage(
                appUserId = appUserId,
                limit = limit,
                beforeTimestamp = beforeTimestamp?.let { java.sql.Timestamp(it) },
                beforeId = cursorId,
            ),
        ).build()
    }
}
