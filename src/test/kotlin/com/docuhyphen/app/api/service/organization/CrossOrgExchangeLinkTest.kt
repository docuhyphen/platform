package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationExchangeLink
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.OrganizationExchangeLinkRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossOrgExchangeLinkTest
{
    @Mock private lateinit var organizationExchangeLinkRepository: OrganizationExchangeLinkRepository
    @Mock private lateinit var organizationRepository: OrganizationRepository
    @Mock private lateinit var emailService: EmailService
    @Mock private lateinit var configurationService: ConfigurationService
    @Mock private lateinit var orgService: OrganizationService
    @Mock private lateinit var authAuditService: AuthAuditService
    @Mock private lateinit var realtimeEventService: RealtimeEventService
    @Mock private lateinit var userRoleService: UserRoleService
    @Mock private lateinit var organizationMembershipService: OrganizationMembershipService

    private lateinit var authContext: AuthTokenContext
    private lateinit var service: OrganizationExchangeLinkService

    private val actorId: UUID = UUID.randomUUID()
    private val orgAId: UUID = UUID.randomUUID()
    private val orgBId: UUID = UUID.randomUUID()
    private val orgCId: UUID = UUID.randomUUID()
    private val approvalCtx = AdminApprovalContext()

    @BeforeEach
    fun setup()
    {
        val actor = AppUser().apply { id = actorId }
        authContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = actor }
        }
        service = OrganizationExchangeLinkService(
            organizationExchangeLinkRepository = organizationExchangeLinkRepository,
            organizationRepository = organizationRepository,
            authContext = authContext,
            emailService = emailService,
            configurationService = configurationService,
            appUserService = orgService,
            authAuditService = authAuditService,
            realtimeEventService = realtimeEventService,
            userRoleService = userRoleService,
            organizationMembershipService = organizationMembershipService,
        )
        `when`(userRoleService.isOrgAdminIn(actorId, orgAId)).thenReturn(true)
        `when`(userRoleService.isOrgAdminIn(actorId, orgBId)).thenReturn(false)
        `when`(userRoleService.isOrgAdminIn(actorId, orgCId)).thenReturn(false)
    }

    // Negative: actor is ORG_ADMIN of Org A only; operations target Org B or Org C

    @Test
    fun `createLink where actor is not admin of requesting org is denied`()
    {
        `when`(organizationRepository.findById(orgBId)).thenReturn(Organization().apply { id = orgBId })
        `when`(organizationRepository.findById(orgCId)).thenReturn(Organization().apply { id = orgCId })

        val ex = assertThrows<IllegalArgumentException> {
            service.createLink(orgBId.toString(), orgCId.toString(), null, approvalCtx)
        }
        assert(ex.message!!.contains("Only organization administrators can create exchange links")) { ex.message.orEmpty() }
    }

    @Test
    fun `acceptLink where actor is not admin of requested org is denied`()
    {
        val linkId = UUID.randomUUID()
        val link = OrganizationExchangeLink().apply {
            id = linkId
            status = LinkStatus.PENDING
            requestedOrganization = Organization().apply { id = orgBId }
        }
        `when`(organizationExchangeLinkRepository.findById(linkId)).thenReturn(link)

        val ex = assertThrows<IllegalArgumentException> {
            service.acceptLink(linkId.toString(), LinkStatus.ACCEPTED, null, approvalCtx)
        }
        assert(ex.message!!.contains("Only administrators of the requested organization can accept or decline")) { ex.message.orEmpty() }
    }

    @Test
    fun `deLink where actor is not admin of either linked org is denied`()
    {
        val linkId = UUID.randomUUID()
        val link = OrganizationExchangeLink().apply {
            id = linkId
            status = LinkStatus.ACCEPTED
            requestingOrganization = Organization().apply { id = orgBId }
            requestedOrganization = Organization().apply { id = orgCId }
        }
        `when`(organizationExchangeLinkRepository.findById(linkId)).thenReturn(link)

        val ex = assertThrows<IllegalArgumentException> {
            service.deLink(linkId.toString(), approvalCtx)
        }
        assert(ex.message!!.contains("Only an administrator of either linked organization can remove the link")) { ex.message.orEmpty() }
    }

    @Test
    fun `getLinksByOrganization where actor is not admin of target org is denied`()
    {
        `when`(organizationRepository.findById(orgBId)).thenReturn(Organization().apply { id = orgBId })

        val ex = assertThrows<IllegalArgumentException> {
            service.getLinksByOrganization(orgBId.toString())
        }
        assert(ex.message!!.contains("Only organization administrators can view exchange links")) { ex.message.orEmpty() }
    }

    // Positive: same actor, operations on Org A pass the auth gate

    @Test
    fun `createLink where actor is admin of requesting org passes auth gate`()
    {
        `when`(organizationRepository.findById(orgAId)).thenReturn(Organization().apply { id = orgAId })
        `when`(organizationRepository.findById(orgBId)).thenReturn(Organization().apply { id = orgBId })
        // Auth passes; isMember check (next gate) is false by default, producing a non-auth error.
        `when`(organizationMembershipService.isMember(actorId, orgAId)).thenReturn(false)

        val ex = assertThrows<IllegalArgumentException> {
            service.createLink(orgAId.toString(), orgBId.toString(), null, approvalCtx)
        }
        assertFalse(ex.message!!.contains("Only organization administrators can create exchange links")) {
            "Auth gate blocked when it should not: ${ex.message}"
        }
    }

    @Test
    fun `acceptLink where actor is admin of requested org completes successfully`()
    {
        val linkId = UUID.randomUUID()
        val link = OrganizationExchangeLink().apply {
            id = linkId
            status = LinkStatus.PENDING
            requestedOrganization = Organization().apply { id = orgAId; name = "Org A" }
        }
        `when`(organizationExchangeLinkRepository.findById(linkId)).thenReturn(link)
        `when`(organizationExchangeLinkRepository.update(link)).thenReturn(link)

        val result = service.acceptLink(linkId.toString(), LinkStatus.ACCEPTED, null, approvalCtx)
        assertNotNull(result)
    }

    @Test
    fun `deLink where actor is admin of requesting org completes successfully`()
    {
        val linkId = UUID.randomUUID()
        val link = OrganizationExchangeLink().apply {
            id = linkId
            status = LinkStatus.PENDING
            requestingOrganization = Organization().apply { id = orgAId; name = "Org A" }
            requestedOrganization = Organization().apply { id = orgBId; name = "Org B" }
        }
        `when`(organizationExchangeLinkRepository.findById(linkId)).thenReturn(link)

        service.deLink(linkId.toString(), approvalCtx)
    }

    @Test
    fun `getLinksByOrganization where actor is admin of target org returns links`()
    {
        `when`(organizationRepository.findById(orgAId)).thenReturn(Organization().apply { id = orgAId })
        `when`(organizationMembershipService.isMember(actorId, orgAId)).thenReturn(true)
        `when`(organizationExchangeLinkRepository.findByRequestingOrganization(orgAId)).thenReturn(emptyList())
        `when`(organizationExchangeLinkRepository.findByRequestedOrganization(orgAId)).thenReturn(emptyList())

        val result = service.getLinksByOrganization(orgAId.toString())
        assertNotNull(result)
    }
}
