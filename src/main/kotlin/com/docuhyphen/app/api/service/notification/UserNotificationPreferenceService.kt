package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.SettingsService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class UserNotificationPreferenceService @Inject constructor(
    private val appUserService: AppUserService,
)
{
    fun isEnabled(
        appUserId: UUID,
        preference: UserNotificationPreference,
        channel: NotificationChannelType,
    ): Boolean
    {
        val settings = appUserService.getById(appUserId)?.settings
        return channelsFor(settings, preference).contains(channel)
    }

    private fun channelsFor(
        settings: AppUserSettings?,
        preference: UserNotificationPreference,
    ): Set<NotificationChannelType>
    {
        if (settings == null)
        {
            val defaults = SettingsService.getDefaultAppUserSettings()
            return when (preference)
            {
                UserNotificationPreference.EXCHANGE_INITIATED -> defaults.notifyShareStartChannels
                UserNotificationPreference.EXCHANGE_ACCEPTED -> defaults.notifyShareAcceptChannels
                UserNotificationPreference.EXCHANGE_DECLINED -> defaults.notifyShareDeclineChannels
                UserNotificationPreference.EXCHANGE_ENDED -> defaults.notifyShareEndChannels
                UserNotificationPreference.DOCUMENT_COMMENTED -> defaults.notifyDocCommentChannels
                UserNotificationPreference.DOCUMENT_DELETED -> defaults.notifyDocDeleteChannels
                UserNotificationPreference.DOCUMENT_ADDED -> defaults.notifyDocAddChannels
                UserNotificationPreference.DOCUMENT_UPLOADED -> defaults.notifyDocUploadChannels
            } ?: emptySet()
        }

        val serializedChannels = when (preference)
        {
            UserNotificationPreference.EXCHANGE_INITIATED -> settings.notifyShareStartChannels
            UserNotificationPreference.EXCHANGE_ACCEPTED -> settings.notifyShareAcceptChannels
            UserNotificationPreference.EXCHANGE_DECLINED -> settings.notifyShareDeclineChannels
            UserNotificationPreference.EXCHANGE_ENDED -> settings.notifyShareEndChannels
            UserNotificationPreference.DOCUMENT_COMMENTED -> settings.notifyDocCommentChannels
            UserNotificationPreference.DOCUMENT_DELETED -> settings.notifyDocDeleteChannels
            UserNotificationPreference.DOCUMENT_ADDED -> settings.notifyDocAddChannels
            UserNotificationPreference.DOCUMENT_UPLOADED -> settings.notifyDocUploadChannels
        }
        return SettingsService.parseNotificationChannels(serializedChannels)
    }
}
