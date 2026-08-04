package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import io.quarkus.security.UnauthorizedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ServiceActionAuthorizationServiceTest
{
    private val userRoleService = mock<UserRoleService>()
    private val service = ServiceActionAuthorizationService(userRoleService)

    @Test
    fun `standalone user can update own notification settings`()
    {
        val appUser = AppUser()

        assertDoesNotThrow {
            service.validateUpdateNotificationSettings(appUser, appUser)
        }
    }

    @Test
    fun `standalone user can update own login notification settings`()
    {
        val appUser = AppUser()

        assertDoesNotThrow {
            service.validateUpdateNotifyLoginSettings(appUser, appUser)
        }
    }

    @Test
    fun `standalone user cannot update another user notification settings`()
    {
        val appUser = AppUser()
        val targetUser = AppUser()
        whenever(userRoleService.isOrgAdmin(appUser.id)).thenReturn(false)

        assertThrows<UnauthorizedException> {
            service.validateUpdateNotificationSettings(appUser, targetUser)
        }
    }
}
