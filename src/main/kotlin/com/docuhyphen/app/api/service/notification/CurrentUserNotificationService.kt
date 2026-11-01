package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationListResponse
import com.docuhyphen.app.api.model.dto.NotificationReadReceiptRequest
import com.docuhyphen.app.api.model.dto.NotificationReadReceiptResponse
import com.docuhyphen.app.api.model.notification.NotificationReadReceiptDtoMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class CurrentUserNotificationService @Inject constructor(
    private val notificationService: InAppNotificationService,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private const val DEFAULT_PAGE_SIZE = 20
        private val PAGE_SIZE_RANGE = 1..100
    }

    fun listPage(requestedLimit: Int?, beforeTimestamp: Long?, beforeId: String?): NotificationListResponse
    {
        val limit = requestedLimit ?: DEFAULT_PAGE_SIZE
        require(limit in PAGE_SIZE_RANGE) { "Notification limit must be between 1 and 100." }
        require((beforeTimestamp == null) == (beforeId == null)) { "Both notification cursor values are required." }
        val cursorId = beforeId?.let {
            runCatching { UUID.fromString(it) }
                .getOrElse { throw IllegalArgumentException("Notification cursor is invalid.") }
        }

        val appUserId = authTokenContext.authToken.appUser!!.id
        return notificationService.listPage(
            appUserId = appUserId,
            limit = limit,
            beforeTimestamp = beforeTimestamp?.let { Timestamp(it) },
            beforeId = cursorId,
        )
    }

    fun markAsRead(request: NotificationReadReceiptRequest): NotificationReadReceiptResponse
    {
        val appUserId = authTokenContext.authToken.appUser!!.id
        validateReadReceiptRequest(request)
        val notificationIds = request.notificationIds.map { notificationId ->
            runCatching { UUID.fromString(notificationId) }
                .getOrElse { throw IllegalArgumentException("Notification id is invalid.") }
        }.toSet()

        val result = notificationService.markAsRead(
            appUserId = appUserId,
            criteria = NotificationReadReceiptDtoMapper.toCriteria(request, notificationIds),
        )
        return NotificationReadReceiptDtoMapper.toResponse(result)
    }

    private fun validateReadReceiptRequest(request: NotificationReadReceiptRequest)
    {
        if (request.all)
        {
            return
        }
        require(!(request.notificationIds.isEmpty() && request.eventTypes.isEmpty() && request.data.isEmpty())) {
            "At least one notification read criteria is required."
        }
        require(request.eventTypes.none { it.isBlank() }) { "Notification event types cannot be blank." }
        require(request.data.none { it.key.isBlank() || it.value.isBlank() }) {
            "Notification read criteria data cannot contain blank keys or values."
        }
    }
}
