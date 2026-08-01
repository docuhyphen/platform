package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationReadReceiptRequest
import com.docuhyphen.app.api.model.dto.NotificationReadReceiptResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.NotificationReadCriteria
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NotificationResource @Inject constructor(
    private val notificationService: InAppNotificationService,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NotificationResource::class.java)
    }

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
        return try
        {
            Response.ok(
                notificationService.listPage(
                    appUserId = appUserId,
                    limit = limit,
                    beforeTimestamp = beforeTimestamp?.let { java.sql.Timestamp(it) },
                    beforeId = cursorId,
                ),
            ).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list notifications for user {}", appUserId, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to load notifications")).build()
        }
    }

    @POST
    @Path("/read-receipts")
    fun createReadReceipts(request: NotificationReadReceiptRequest): Response
    {
        val appUserId = authTokenContext.authToken.appUser!!.id
        val validationError = validateReadReceiptRequest(request)
        if (validationError != null)
        {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError(validationError))
                .build()
        }

        val notificationIds = request.notificationIds.map { notificationId ->
            runCatching { UUID.fromString(notificationId) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Notification id is invalid."))
                    .build()
            }
        }.toSet()

        return try
        {
            val result = notificationService.markAsRead(
                appUserId = appUserId,
                criteria = NotificationReadCriteria(
                    all = request.all,
                    notificationIds = notificationIds,
                    eventTypes = request.eventTypes.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    data = request.data.mapKeys { it.key.trim() }
                        .mapValues { it.value.trim() }
                        .filterKeys { it.isNotBlank() }
                        .filterValues { it.isNotBlank() },
                ),
            )
            Response.ok(
                NotificationReadReceiptResponse(
                    readNotificationIds = result.readNotificationIds.map { it.toString() },
                    unreadCount = result.unreadCount,
                )
            ).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to mark notifications read for user {}", appUserId, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to update notification read state")).build()
        }
    }

    private fun validateReadReceiptRequest(request: NotificationReadReceiptRequest): String?
    {
        if (request.all)
        {
            return null
        }
        if (request.notificationIds.isEmpty() && request.eventTypes.isEmpty() && request.data.isEmpty())
        {
            return "At least one notification read criteria is required."
        }
        if (request.eventTypes.any { it.isBlank() })
        {
            return "Notification event types cannot be blank."
        }
        if (request.data.any { it.key.isBlank() || it.value.isBlank() })
        {
            return "Notification read criteria data cannot contain blank keys or values."
        }
        return null
    }
}
