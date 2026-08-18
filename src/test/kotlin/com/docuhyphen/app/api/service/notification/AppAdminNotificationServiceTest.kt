package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.user.AppUserService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AppAdminNotificationServiceTest
{
    private val userRoleService: UserRoleService = mock()
    private val appUserService: AppUserService = mock()
    private val emailService: EmailService = mock()
    private val service = AppAdminNotificationService(userRoleService, appUserService, emailService)

    @Test
    fun `notification is emailed to the first effective App Administrator`()
    {
        val adminId = UUID.randomUUID()
        whenever(userRoleService.firstActiveAppAdminId()).thenReturn(adminId)
        whenever(appUserService.getById(adminId)).thenReturn(
            AppUser().apply { id = adminId; email = "first-admin@example.test" },
        )

        val delivered = service.notifyNewUserRegistration("new-user@example.test")

        assertTrue(delivered)
        verify(emailService).sendEmail(
            to = eq("first-admin@example.test"),
            subject = eq("New User Registration"),
            body = any(),
            useHtml = eq(false),
        )
    }

    @Test
    fun `notification is skipped when no effective App Administrator exists`()
    {
        whenever(userRoleService.firstActiveAppAdminId()).thenReturn(null)

        val delivered = service.notifySubscriptionTrialRequest(
            requesterName = "Requester",
            requesterEmail = "requester@example.test",
            ownerName = "Example Organization",
            planCode = "BUSINESS",
            requestId = UUID.randomUUID().toString(),
        )

        assertFalse(delivered)
        verify(emailService, never()).sendEmail(any(), any(), any(), any())
    }
}
