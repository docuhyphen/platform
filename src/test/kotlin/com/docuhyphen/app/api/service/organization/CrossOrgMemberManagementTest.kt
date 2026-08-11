package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossOrgMemberManagementTest
{
    @Mock private lateinit var organizationGroupService: OrganizationGroupService
    @Mock private lateinit var authenticationService: AuthenticationService
    @Mock private lateinit var appUserService: AppUserService
    @Mock private lateinit var authAuditService: AuthAuditService
    @Mock private lateinit var emailService: EmailService
    @Mock private lateinit var emailTemplateService: EmailTemplateService
    @Mock private lateinit var configurationService: ConfigurationService
    @Mock private lateinit var userRoleService: UserRoleService
    @Mock private lateinit var organizationMembershipService: OrganizationMembershipService

    private lateinit var authTokenContext: AuthTokenContext
    private lateinit var service: OrganizationAppUserService

    private val actorId: UUID = UUID.randomUUID()
    private val orgAId: UUID = UUID.randomUUID()
    private val orgBId: UUID = UUID.randomUUID()
    private val approvalCtx = AdminApprovalContext()

    @BeforeEach
    fun setup()
    {
        val actor = AppUser().apply { id = actorId }
        authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = actor }
        }
        service = OrganizationAppUserService(
            organizationGroupService = organizationGroupService,
            authenticationService = authenticationService,
            appUserService = appUserService,
            authTokenContext = authTokenContext,
            authAuditService = authAuditService,
            emailService = emailService,
            emailTemplateService = emailTemplateService,
            configurationService = configurationService,
            userRoleService = userRoleService,
            organizationMembershipService = organizationMembershipService,
        )
        `when`(userRoleService.isOrgAdminIn(actorId, orgAId)).thenReturn(true)
        `when`(userRoleService.isOrgAdminIn(actorId, orgBId)).thenReturn(false)
    }

    // Negative: actor is ORG_ADMIN of Org A, targets Org B

    @Test
    fun `addAppUser targeting foreign org is denied`()
    {
        val ex = assertThrows<IllegalArgumentException> {
            service.addAppUser(orgBId.toString(), setOf(OrganizationRoleName.ORG_MEMBER), "u@test.com", "A", "B", approvalCtx)
        }
        assert(ex.message!!.contains("does not have permission")) { ex.message.orEmpty() }
    }

    @Test
    fun `updateAppUser targeting foreign org is denied`()
    {
        val ex = assertThrows<IllegalArgumentException> {
            service.updateAppUser(orgBId.toString(), UUID.randomUUID().toString(), emptySet(), emptySet(), true, null, "A", "B", approvalCtx)
        }
        assert(ex.message!!.contains("does not have permission")) { ex.message.orEmpty() }
    }

    @Test
    fun `deleteAppUser targeting foreign org is denied`()
    {
        val ex = assertThrows<IllegalArgumentException> {
            service.deleteAppUser(orgBId.toString(), UUID.randomUUID().toString(), approvalCtx)
        }
        assert(ex.message!!.contains("does not have permission")) { ex.message.orEmpty() }
    }

    @Test
    fun `getAppUsers targeting foreign org is denied`()
    {
        val ex = assertThrows<IllegalArgumentException> {
            service.getAppUsers(orgBId.toString())
        }
        assert(ex.message!!.contains("does not have permission")) { ex.message.orEmpty() }
    }

    // Positive: same actor, own org passes the auth gate

    @Test
    fun `addAppUser targeting own org passes auth gate`()
    {
        val org = Organization().apply { id = orgAId }
        `when`(organizationGroupService.getOrganizationById(orgAId)).thenReturn(org)
        `when`(organizationMembershipService.membersOf(orgAId)).thenReturn(emptyList())

        // Auth passes; empty roles triggers the next validation, not an auth error.
        val ex = assertThrows<IllegalArgumentException> {
            service.addAppUser(orgAId.toString(), emptySet(), "u@test.com", "A", "B", approvalCtx)
        }
        assertFalse(ex.message!!.contains("does not have permission")) {
            "Auth gate blocked when it should not: ${ex.message}"
        }
    }

    @Test
    fun `addAppUser reuses an active registered account without replacing its credentials`()
    {
        val org = Organization().apply {
            id = orgAId
            name = "Organization A"
        }
        val registered = AppUser().apply {
            email = "member@test.com"
            isActive = true
            isTemporary = false
            isPasswordTemporary = false
        }
        whenever(organizationGroupService.getOrganizationById(orgAId)).thenReturn(org)
        whenever(organizationMembershipService.membersOf(orgAId)).thenReturn(emptyList())
        whenever(appUserService.findRegisteredByEmail(registered.email)).thenReturn(registered)

        val result = service.addAppUser(
            orgAId.toString(),
            setOf(OrganizationRoleName.ORG_MEMBER),
            registered.email,
            "Changed",
            "Name",
            approvalCtx,
        )

        assertSame(registered, result)
        verify(appUserService, never()).create(any())
        verify(authenticationService, never()).generatePasswordSalt()
        verify(organizationMembershipService).assignOrgRole(
            registered.id,
            orgAId,
            OrganizationRoleName.ORG_MEMBER,
            false,
            actorId,
        )
    }

    @Test
    fun `addAppUser denies a stale inactive account before membership mutation`()
    {
        val org = Organization().apply {
            id = orgAId
            name = "Organization A"
        }
        val inactive = AppUser().apply {
            email = "inactive@test.com"
            isActive = false
            isTemporary = false
        }
        whenever(organizationGroupService.getOrganizationById(orgAId)).thenReturn(org)
        whenever(organizationMembershipService.membersOf(orgAId)).thenReturn(emptyList())
        whenever(appUserService.findRegisteredByEmail(inactive.email)).thenReturn(inactive)

        assertThrows<IllegalArgumentException> {
            service.addAppUser(
                orgAId.toString(),
                setOf(OrganizationRoleName.ORG_MEMBER),
                inactive.email,
                "Inactive",
                "Member",
                approvalCtx,
            )
        }

        verify(appUserService, never()).create(any())
        verify(organizationMembershipService, never()).assignOrgRole(any(), any(), any(), any(), any())
    }

    @Test
    fun `updateAppUser targeting own org passes auth gate`()
    {
        // Auth passes; null appUserId triggers the next validation, not an auth error.
        val ex = assertThrows<IllegalArgumentException> {
            service.updateAppUser(orgAId.toString(), null, emptySet(), emptySet(), true, null, "A", "B", approvalCtx)
        }
        assertFalse(ex.message!!.contains("does not have permission")) {
            "Auth gate blocked when it should not: ${ex.message}"
        }
    }

    @Test
    fun `deleteAppUser targeting own org passes auth gate`()
    {
        // Auth passes; null appUserId triggers the next validation, not an auth error.
        val ex = assertThrows<IllegalArgumentException> {
            service.deleteAppUser(orgAId.toString(), null, approvalCtx)
        }
        assertFalse(ex.message!!.contains("does not have permission")) {
            "Auth gate blocked when it should not: ${ex.message}"
        }
    }

    @Test
    fun `getAppUsers targeting own org returns member list`()
    {
        val org = Organization().apply { id = orgAId }
        `when`(organizationGroupService.getOrganizationById(orgAId)).thenReturn(org)
        `when`(organizationMembershipService.membersOf(orgAId)).thenReturn(emptyList())

        val result = service.getAppUsers(orgAId.toString())
        assert(result.isEmpty())
    }
}
