package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.organization.OrganizationFeatureEntitlementRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.subscription.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.platform.PlatformOrganizationService
import com.docuhyphen.app.api.service.platform.PlatformOrganizationStatusService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

class PlatformOrganizationAuthorizationMatrixTest
{
    @Test
    fun `effective APP_ADMIN can call the platform organization API without an active organization`()
    {
        val harness = harness(
            PrincipalCase(
                label = "effective APP_ADMIN",
                appRoles = setOf(AppRoleName.APP_ADMIN),
            ),
        )

        val response = harness.resource.list(
            query = null,
            status = null,
            tierCode = null,
            sort = null,
            direction = null,
            limit = 50,
            offset = 0,
            requestId = "request-1",
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(harness.organizationRepository).findForPlatformAdministration(
            null,
            null,
            null,
            "name",
            "asc",
            50,
            0,
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deniedPrincipals")
    fun `non-admin and ineffective assignments cannot call the platform organization API`(
        principalCase: PrincipalCase,
    )
    {
        val harness = harness(principalCase)

        val response = harness.resource.list(
            query = null,
            status = null,
            tierCode = null,
            sort = null,
            direction = null,
            limit = 50,
            offset = 0,
            requestId = "request-1",
        )

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        verify(harness.organizationRepository, never()).findForPlatformAdministration(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
        )
    }

    private fun harness(principalCase: PrincipalCase): Harness
    {
        val actor = AppUser().apply {
            id = UUID.randomUUID()
            email = "actor@example.com"
            isActive = principalCase.userActive
            deprovisionedAt = if (principalCase.userDeprovisioned)
            {
                Timestamp.from(Instant.parse("2026-07-28T12:00:00Z"))
            }
            else
            {
                null
            }
        }
        val appRoleRepository = mock<AppRoleAssignmentRepository>()
        val effectiveAssignments = if (principalCase.assignmentEffective)
        {
            principalCase.appRoles.map { role ->
                AppRoleAssignment().apply {
                    appUserId = actor.id
                    roleName = role
                }
            }
        }
        else
        {
            emptyList()
        }
        whenever(appRoleRepository.findActiveForUser(actor.id)).thenReturn(effectiveAssignments)

        val membershipRepository = mock<OrganizationMembershipRepository>()
        if (principalCase.organizationRoles.isNotEmpty())
        {
            val membership = OrganizationMembership().apply {
                appUserId = actor.id
                organizationId = UUID.randomUUID()
                status = OrganizationMembershipStatus.ACTIVE
                roles = principalCase.organizationRoles.toMutableSet()
            }
            whenever(membershipRepository.findPrimaryForUser(actor.id)).thenReturn(membership)
            whenever(membershipRepository.findActiveByUser(actor.id)).thenReturn(listOf(membership))
        }

        val organizationRepository = mock<OrganizationRepository>()
        whenever(
            organizationRepository.findForPlatformAdministration(
                null,
                null,
                null,
                "name",
                "asc",
                50,
                0,
            ),
        ).thenReturn(emptyList())
        whenever(
            organizationRepository.countForPlatformAdministration(null, null, null),
        ).thenReturn(0)

        val userRoleService = UserRoleService(membershipRepository, appRoleRepository)
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = actor }
        }
        val service = PlatformOrganizationService(
            authTokenContext = authTokenContext,
            userRoleService = userRoleService,
            organizationRepository = organizationRepository,
            organizationSubscriptionPolicyRepository = mock<OrganizationSubscriptionPolicyRepository>(),
            organizationFeatureEntitlementRepository = mock<OrganizationFeatureEntitlementRepository>(),
            organizationMembershipService = mock<OrganizationMembershipService>(),
            mapper = PlatformOrganizationDtoMapper(),
            authAuditService = mock<AuthAuditService>(),
        )

        return Harness(
            resource = PlatformOrganizationResource(
                service,
                mock<PlatformOrganizationStatusService>(),
            ),
            organizationRepository = organizationRepository,
        )
    }

    private data class Harness(
        val resource: PlatformOrganizationResource,
        val organizationRepository: OrganizationRepository,
    )

    data class PrincipalCase(
        val label: String,
        val appRoles: Set<AppRoleName> = setOf(AppRoleName.APP_USER),
        val organizationRoles: Set<OrganizationRoleName> = emptySet(),
        val assignmentEffective: Boolean = true,
        val userActive: Boolean = true,
        val userDeprovisioned: Boolean = false,
    )
    {
        override fun toString(): String = label
    }

    companion object
    {
        @JvmStatic
        fun deniedPrincipals(): Stream<PrincipalCase> = Stream.of(
            PrincipalCase(
                label = "APP_AUDITOR",
                appRoles = setOf(AppRoleName.APP_AUDITOR),
            ),
            PrincipalCase(
                label = "ORG_OWNER",
                organizationRoles = setOf(OrganizationRoleName.ORG_OWNER),
            ),
            PrincipalCase(
                label = "ORG_ADMIN",
                organizationRoles = setOf(OrganizationRoleName.ORG_ADMIN),
            ),
            PrincipalCase(
                label = "ordinary organization member",
                organizationRoles = setOf(OrganizationRoleName.ORG_MEMBER),
            ),
            PrincipalCase(
                label = "revoked APP_ADMIN assignment",
                appRoles = setOf(AppRoleName.APP_ADMIN),
                assignmentEffective = false,
            ),
            PrincipalCase(
                label = "expired APP_ADMIN assignment",
                appRoles = setOf(AppRoleName.APP_ADMIN),
                assignmentEffective = false,
            ),
            PrincipalCase(
                label = "inactive APP_ADMIN user",
                appRoles = setOf(AppRoleName.APP_ADMIN),
                assignmentEffective = false,
                userActive = false,
            ),
            PrincipalCase(
                label = "deprovisioned APP_ADMIN user",
                appRoles = setOf(AppRoleName.APP_ADMIN),
                assignmentEffective = false,
                userDeprovisioned = true,
            ),
        )
    }
}
