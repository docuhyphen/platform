package com.docuhyphen.app.api.resource.notification

import com.docuhyphen.app.api.model.dto.NotificationReadReceiptRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.notification.operations.NotificationResourceOperations
import com.docuhyphen.app.api.service.notification.CurrentUserNotificationService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class NotificationResource @Inject constructor(
    private val currentUserNotificationService: CurrentUserNotificationService,
) : NotificationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NotificationResource::class.java)
    }

    override fun listNotifications(
        requestedLimit: Int?,
        beforeTimestamp: Long?,
        beforeId: String?,
    ): Response
    {
        return try
        {
            Response.ok(currentUserNotificationService.listPage(requestedLimit, beforeTimestamp, beforeId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing in-app notifications", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to load notifications"))
                        .build()
                }
            }
        }
    }

    override fun createReadReceipts(request: NotificationReadReceiptRequest): Response
    {
        return try
        {
            Response.ok(currentUserNotificationService.markAsRead(request)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error creating notification read receipts", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to update notification read state"))
                        .build()
                }
            }
        }
    }
}
