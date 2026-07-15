package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.AppUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class UserNotificationPreferenceServiceTest
{
    private val appUserService = mock<AppUserService>()
    private val service = UserNotificationPreferenceService(appUserService)

    @Test
    fun `maps every Preferences notification row to its configured channels`()
    {
        val appUserId = UUID.randomUUID()
        val settings = AppUserSettings().apply {
            notifyShareStartChannels = "IN_APP"
            notifyShareAcceptChannels = "EMAIL"
            notifyShareDeclineChannels = ""
            notifyShareEndChannels = "EMAIL,IN_APP"
            notifyDocCommentChannels = "IN_APP"
            notifyDocDeleteChannels = "EMAIL"
            notifyDocAddChannels = "IN_APP"
            notifyDocUploadChannels = ""
        }
        whenever(appUserService.getById(appUserId)).thenReturn(
            AppUser().apply {
                id = appUserId
                this.settings = settings
            },
        )

        val expected = mapOf(
            UserNotificationPreference.EXCHANGE_INITIATED to true,
            UserNotificationPreference.EXCHANGE_ACCEPTED to false,
            UserNotificationPreference.EXCHANGE_DECLINED to false,
            UserNotificationPreference.EXCHANGE_ENDED to true,
            UserNotificationPreference.DOCUMENT_COMMENTED to true,
            UserNotificationPreference.DOCUMENT_DELETED to false,
            UserNotificationPreference.DOCUMENT_ADDED to true,
            UserNotificationPreference.DOCUMENT_UPLOADED to false,
        )

        expected.forEach { (preference, enabled) ->
            assertEquals(
                enabled,
                service.isEnabled(appUserId, preference, NotificationChannelType.IN_APP),
                preference.name,
            )
        }
    }

    @Test
    fun `uses application defaults when a user has no saved settings`()
    {
        val appUserId = UUID.randomUUID()
        whenever(appUserService.getById(appUserId)).thenReturn(AppUser().apply { id = appUserId })

        assertEquals(
            true,
            service.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.IN_APP,
            ),
        )
        assertEquals(
            false,
            service.isEnabled(
                appUserId,
                UserNotificationPreference.EXCHANGE_ENDED,
                NotificationChannelType.IN_APP,
            ),
        )
    }
}
