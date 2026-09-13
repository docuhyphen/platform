package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementDto
import com.docuhyphen.app.api.resource.model.PlatformUserFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The platform administration surface that records an individual account's feature decisions. It is
 * the personal counterpart of the released organization surface: without it the personal half of the
 * override table has no writer and a personal grant could only be created by direct SQL.
 */
class PlatformUserFeatureEntitlementServiceTest
{
    private val actor = AppUser().apply {
        id = UUID.randomUUID()
        email = "platform-admin@process.test"
        isActive = true
    }
    private val subject = AppUser().apply {
        id = UUID.randomUUID()
        email = "process-owner@process.test"
        isActive = true
    }
    private val authTokenContext = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = actor }
    }
    private val userRoleService = mock<UserRoleService>()
    private val auditService = mock<AuthAuditService>()
    private val appUserService = mock<AppUserService>()
    private val entitlementRepository = mock<SubscriptionFeatureEntitlementRepository>()
    private val service = PlatformUserFeatureEntitlementService(
        authTokenContext = authTokenContext,
        userRoleService = userRoleService,
        authAuditService = auditService,
        appUserService = appUserService,
        featureEntitlementAdminService = SubscriptionFeatureEntitlementAdminService(entitlementRepository),
    )

    @Test
    fun `APP_ADMIN is required before the feature decisions of an account are read`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(false)

        assertThrows(ForbiddenException::class.java) {
            service.get(subject.id.toString(), "request-id")
        }

        verify(appUserService, never()).getByIdWithPerson(any())
        verify(entitlementRepository, never()).findByAppUserId(any())
    }

    @Test
    fun `APP_ADMIN is required before the feature decisions of an account are replaced`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(false)

        assertThrows(ForbiddenException::class.java) {
            service.replace(
                appUserId = subject.id.toString(),
                request = PlatformUserFeatureEntitlementsUpdateRequest(
                    entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
                ),
                adminApprovalContext = AdminApprovalContext("request-id"),
            )
        }

        verify(entitlementRepository, never()).save(any())
    }

    @Test
    fun `a temporary recipient cannot hold feature decisions`()
    {
        val temporary = AppUser().apply {
            id = UUID.randomUUID()
            email = "recipient@process.test"
            isTemporary = true
        }
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(temporary.id)).thenReturn(temporary)

        assertThrows(IllegalArgumentException::class.java) {
            service.replace(
                appUserId = temporary.id.toString(),
                request = PlatformUserFeatureEntitlementsUpdateRequest(
                    entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
                ),
                adminApprovalContext = AdminApprovalContext("request-id"),
            )
        }

        verify(entitlementRepository, never()).save(any())
    }

    @Test
    fun `a registered application cannot hold feature decisions`()
    {
        val applicationAccount = AppUser().apply {
            id = UUID.randomUUID()
            email = "integration@process.test"
            application = Application()
        }
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(applicationAccount.id)).thenReturn(applicationAccount)

        assertThrows(IllegalArgumentException::class.java) {
            service.replace(
                appUserId = applicationAccount.id.toString(),
                request = PlatformUserFeatureEntitlementsUpdateRequest(
                    entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
                ),
                adminApprovalContext = AdminApprovalContext("request-id"),
            )
        }

        verify(entitlementRepository, never()).save(any())
    }

    @Test
    fun `a replacement records the decision against the individual account`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(subject.id)).thenReturn(subject)
        whenever(entitlementRepository.findByAppUserId(subject.id))
            .thenReturn(emptyList())
            .thenReturn(listOf(personalDecision("PROCESS_CAPABILITY", true)))

        val result = service.replace(
            appUserId = subject.id.toString(),
            request = PlatformUserFeatureEntitlementsUpdateRequest(
                entitlements = listOf(PlatformUserFeatureEntitlementDto(" process_capability ", true)),
                changeReason = "Controlled release participant",
            ),
            adminApprovalContext = AdminApprovalContext("request-id"),
        )

        val saved = argumentCaptor<SubscriptionFeatureEntitlement>()
        verify(entitlementRepository).save(saved.capture())
        assertEquals(SubscriptionOwnerType.USER.name, saved.firstValue.ownerType)
        assertEquals(subject.id, saved.firstValue.appUserId)
        assertNull(saved.firstValue.organizationId)
        assertEquals("PROCESS_CAPABILITY", saved.firstValue.featureCode)
        assertEquals(subject.id, result.user.id)
        assertEquals(listOf("PROCESS_CAPABILITY"), result.entitlements.map { it.featureCode })
    }

    @Test
    fun `the update audit names the account and carries before and after state`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(subject.id)).thenReturn(subject)
        whenever(entitlementRepository.findByAppUserId(subject.id))
            .thenReturn(listOf(personalDecision("PROCESS_CAPABILITY", false)))
            .thenReturn(listOf(personalDecision("PROCESS_CAPABILITY", true)))

        service.replace(
            appUserId = subject.id.toString(),
            request = PlatformUserFeatureEntitlementsUpdateRequest(
                entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
                changeReason = "Controlled release participant",
            ),
            adminApprovalContext = AdminApprovalContext("request-id"),
        )

        val auditArguments = mockingDetails(auditService).invocations
            .last { it.method.name == "emitRequired" }
            .arguments
        assertEquals("PLATFORM_USER_FEATURE_ENTITLEMENTS_UPDATE", auditArguments[0])
        assertEquals("SUCCESS", auditArguments[1])
        assertEquals(actor.id, auditArguments[3])
        assertEquals("APP_ADMIN", auditArguments[4])
        assertEquals("request-id", auditArguments[7])
        assertEquals("Controlled release participant", auditArguments[8])
        assertEquals("APP_USER", auditArguments[11])
        assertEquals(subject.id.toString(), auditArguments[12])
        @Suppress("UNCHECKED_CAST")
        val details = auditArguments[13] as Map<String, String>
        assertTrue(details.getValue("before_state").contains("PROCESS_CAPABILITY=false"))
        assertTrue(details.getValue("after_state").contains("PROCESS_CAPABILITY=true"))
    }

    @Test
    fun `reading the decisions of an account returns them and records the view`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(subject.id)).thenReturn(subject)
        whenever(entitlementRepository.findByAppUserId(subject.id))
            .thenReturn(listOf(personalDecision("PROCESS_CAPABILITY", true)))

        val result = service.get(subject.id.toString(), "request-id")

        assertEquals(subject.id, result.user.id)
        assertEquals(listOf("PROCESS_CAPABILITY"), result.entitlements.map { it.featureCode })
        val auditArguments = mockingDetails(auditService).invocations
            .last { it.method.name == "emit" }
            .arguments
        assertEquals("PLATFORM_USER_FEATURE_ENTITLEMENTS_VIEW", auditArguments[0])
        assertEquals("APP_USER", auditArguments[11])
        assertEquals(subject.id.toString(), auditArguments[12])
    }

    @Test
    fun `an unknown account is refused before any decision is written`()
    {
        val unknownId = UUID.randomUUID()
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(unknownId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            service.get(unknownId.toString(), "request-id")
        }
    }

    @Test
    fun `a change reason longer than the stored limit is refused`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(appUserService.getByIdWithPerson(subject.id)).thenReturn(subject)

        assertThrows(IllegalArgumentException::class.java) {
            service.replace(
                appUserId = subject.id.toString(),
                request = PlatformUserFeatureEntitlementsUpdateRequest(
                    entitlements = listOf(PlatformUserFeatureEntitlementDto("PROCESS_CAPABILITY", true)),
                    changeReason = "r".repeat(1025),
                ),
                adminApprovalContext = AdminApprovalContext("request-id"),
            )
        }

        verify(entitlementRepository, never()).save(any())
    }

    private fun personalDecision(code: String, enabled: Boolean): SubscriptionFeatureEntitlement =
        SubscriptionFeatureEntitlement().apply {
            ownerType = SubscriptionOwnerType.USER.name
            appUserId = subject.id
            featureCode = code
            isEnabled = enabled
            updatedByAppUserId = actor.id
        }
}
