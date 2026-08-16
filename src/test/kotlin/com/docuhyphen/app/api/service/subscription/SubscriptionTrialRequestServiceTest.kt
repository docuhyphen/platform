package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequest
import com.docuhyphen.app.api.repository.SubscriptionTrialRequestRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.organization.OrganizationService
import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.SQLException
import java.util.UUID

class SubscriptionTrialRequestServiceTest
{
    private val repository: SubscriptionTrialRequestRepository = mock()
    private val eligibilityService: SubscriptionTrialRequestEligibilityService = mock()
    private val userRoleService: UserRoleService = mock()
    private val notificationService: InAppNotificationService = mock()
    private val appUserService: AppUserService = mock()
    private val organizationService: OrganizationService = mock()
    private val service = SubscriptionTrialRequestService(
        repository,
        eligibilityService,
        userRoleService,
        notificationService,
        appUserService,
        organizationService,
    )
    private val requesterId = UUID.randomUUID()

    @Test
    fun `creating a request notifies every active App Administrator`()
    {
        val adminOne = UUID.randomUUID()
        val adminTwo = UUID.randomUUID()
        val requester = AppUser().apply { id = requesterId; email = "requester@example.test" }
        whenever(eligibilityService.evaluateForRequest(SubscriptionOwnerType.USER, requesterId))
            .thenReturn(SubscriptionTrialRequestEligibility(true))
        whenever(repository.findPending(SubscriptionOwnerType.USER.name, requesterId)).thenReturn(null)
        whenever(repository.insertAndFlush(any<SubscriptionTrialRequest>())).thenAnswer { it.arguments[0] }
        whenever(appUserService.getByIdWithPerson(requesterId)).thenReturn(requester)
        whenever(userRoleService.activeAppAdminIds()).thenReturn(setOf(adminOne, adminTwo))

        val result = service.create(SubscriptionOwnerType.USER, requesterId, requesterId, null)

        assertEquals("PENDING", result.request.status)
        assertEquals(PlanCode.PERSONAL.name, result.request.planCode)
        verify(notificationService).publishAdministrative(
            eq(adminOne), eq("subscription_trial_request.created"), any(), any(), any(),
        )
        verify(notificationService).publishAdministrative(
            eq(adminTwo), eq("subscription_trial_request.created"), any(), any(), any(),
        )
    }

    @Test
    fun `an existing pending request is reported as a conflict`()
    {
        whenever(eligibilityService.evaluateForRequest(SubscriptionOwnerType.USER, requesterId))
            .thenReturn(SubscriptionTrialRequestEligibility(true))
        whenever(repository.findPending(SubscriptionOwnerType.USER.name, requesterId))
            .thenReturn(SubscriptionTrialRequest())

        val exception = assertThrows<SubscriptionTrialRequestConflictException> {
            service.create(SubscriptionOwnerType.USER, requesterId, requesterId, null)
        }

        assertEquals("A trial request is already pending", exception.message)
    }

    @Test
    fun `a concurrent pending request uniqueness failure is reported as a conflict`()
    {
        whenever(eligibilityService.evaluateForRequest(SubscriptionOwnerType.USER, requesterId))
            .thenReturn(SubscriptionTrialRequestEligibility(true))
        whenever(repository.findPending(SubscriptionOwnerType.USER.name, requesterId)).thenReturn(null)
        whenever(repository.insertAndFlush(any<SubscriptionTrialRequest>())).thenThrow(
            PersistenceException(SQLException("duplicate pending request", "23505")),
        )

        val exception = assertThrows<SubscriptionTrialRequestConflictException> {
            service.create(SubscriptionOwnerType.USER, requesterId, requesterId, null)
        }

        assertEquals("A trial request is already pending", exception.message)
    }
}
