package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AppAdminTenantCapabilityScopeTest
{
    @Mock private lateinit var shareRepository: ShareRepository
    @Mock private lateinit var shareLinkRepository: ShareLinkRepository
    @Mock private lateinit var appRoleAssignmentRepository: AppRoleAssignmentRepository
    @Mock private lateinit var principalGroupMemberRepository: PrincipalGroupMemberRepository
    @Mock private lateinit var organizationMembershipRepository: OrganizationMembershipRepository
    @Mock private lateinit var principalGroupRepository: PrincipalGroupRepository
    @Mock private lateinit var applicationService: ApplicationService
    @Mock private lateinit var resourceContextRegistry: ResourceAuthorizationContextRegistry

    private lateinit var authorizationService: DefaultAuthorizationService
    private val appAdminId = UUID.randomUUID()

    @BeforeEach
    fun setUp()
    {
        authorizationService = DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = shareLinkRepository,
            appRoleAssignmentRepository = appRoleAssignmentRepository,
            principalGroupMemberRepository = principalGroupMemberRepository,
            organizationMembershipRepository = organizationMembershipRepository,
            principalGroupRepository = principalGroupRepository,
            applicationService = applicationService,
            resourceContextRegistry = resourceContextRegistry,
        )
        whenever(appRoleAssignmentRepository.findActiveForUser(appAdminId)).thenReturn(
            listOf(
                AppRoleAssignment().apply {
                    appUserId = appAdminId
                    roleName = AppRoleName.APP_ADMIN
                }
            )
        )
        whenever(principalGroupMemberRepository.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
    }

    @Test
    fun `APP_ADMIN platform governance capabilities do not authorize organization governance`()
    {
        val principal = PrincipalRef.user(appAdminId)
        val organization = ResourceRef.organization(UUID.randomUUID())
        val context = AuthorizationContext(activeOrgId = organization.id)

        val capabilities = authorizationService.capabilities(principal, organization, context)

        assertFalse(Capability.AUDIT_RETENTION_MANAGE in capabilities)
        assertFalse(Capability.AUDIT_LEGAL_HOLD_MANAGE in capabilities)
        assertFalse(Capability.AUDIT_INTEGRITY_VERIFY in capabilities)
        assertFalse(Capability.AUDIT_ENGAGEMENT_MANAGE in capabilities)
        assertFalse(Capability.AUDIT_EXPORT_APPROVE in capabilities)
        assertFalse(
            authorizationService.authorize(
                principal,
                Action.AUDIT_RETENTION_MANAGE,
                organization,
                context,
            ).isAllowed
        )
    }

    @Test
    fun `APP_ADMIN retains audit governance capabilities on the platform resource`()
    {
        val principal = PrincipalRef.user(appAdminId)
        val platform = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))

        val capabilities = authorizationService.capabilities(principal, platform, AuthorizationContext())

        assertTrue(Capability.AUDIT_RETENTION_MANAGE in capabilities)
        assertTrue(
            authorizationService.authorize(
                principal,
                Action.AUDIT_RETENTION_MANAGE,
                platform,
                AuthorizationContext(),
            ).isAllowed
        )
    }
}
