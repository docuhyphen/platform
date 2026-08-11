package com.docuhyphen.app.api.service

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SettingsServiceTest
{
    private val userRoleService = mock<UserRoleService>()
    private val appUserService = mock<AppUserService>()
    private val authTokenContext = AuthTokenContext()
    private val service = SettingsService(
        ServiceActionAuthorizationService(userRoleService),
        authTokenContext,
        appUserService,
        mock<OrganizationService>(),
        userRoleService,
        mock<OrganizationFeatureSubscriptionGuard>(),
    )

    @Test
    fun `standalone user with no saved settings can update a notification channel`()
    {
        val appUser = AppUser()
        authTokenContext.authToken = AuthToken().apply { this.appUser = appUser }
        val request = SettingsService.getDefaultAppUserSettings().copy(
            notifyShareStart = true,
            notifyShareStartChannels = setOf(NotificationChannelType.EMAIL),
        )

        val updated = service.updateAppUserSettings(null, request)

        assertEquals("EMAIL", updated.notifyShareStartChannels)
        assertFalse(updated.notifyLogin)
        verify(appUserService).update(appUser)
    }

    @Test
    fun `new settings and response defaults agree on login notifications`()
    {
        assertEquals(
            SettingsService.getDefaultAppUserSettings().notifyLogin,
            AppUserSettings().notifyLogin,
        )
    }
}
