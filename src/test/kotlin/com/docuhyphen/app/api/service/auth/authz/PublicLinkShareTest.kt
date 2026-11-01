package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class PublicLinkShareTest
{
    private val tokenHash = "a".repeat(64)
    private val exchangeId: UUID = UUID.randomUUID()
    private val shareId: UUID = UUID.randomUUID()

    @Test
    fun `valid active ShareLink with covering Share grants access`()
    {
        val link = activeLink()
        val share = coveringShare()

        val svc = buildService(link = link, share = share)
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        val decision = svc.authorize(
            PrincipalRef(PrincipalKind.USER, UUID.randomUUID()),
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(exchangeId),
            context,
        )
        assertTrue(decision.isAllowed, "Valid link should grant access")
    }

    @Test
    fun `link token alongside an authenticated user session is accepted`()
    {
        val link = activeLink()
        val share = coveringShare()
        val svc = buildService(link = link, share = share)

        val userId = UUID.randomUUID()
        val context = AuthorizationContext(
            shareLinkTokenHash = tokenHash,
        )
        val decision = svc.authorize(
            PrincipalRef.user(userId),
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(exchangeId),
            context,
        )
        assertTrue(decision.isAllowed)
    }

    @Test
    fun `unknown token hash produces no grant and denies`()
    {
        val linkRepo = mock<ShareLinkRepository>()
        whenever(linkRepo.findByTokenHash(any())).thenReturn(null)

        val svc = buildService(linkRepo = linkRepo)
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        val decision = svc.authorize(
            PrincipalRef(PrincipalKind.USER, UUID.randomUUID()),
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(exchangeId),
            context,
        )
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `revoked ShareLink denies access`()
    {
        val link = activeLink().apply { status = ShareLinkStatus.REVOKED }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `expired ShareLink by status denies access`()
    {
        val link = activeLink().apply { status = ShareLinkStatus.EXPIRED }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `expired ShareLink by timestamp denies access`()
    {
        val link = activeLink().apply {
            expiresAt = Timestamp.from(Instant.now().minusSeconds(3600))
        }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `exhausted ShareLink usedCount at maxUses denies access`()
    {
        val link = activeLink().apply {
            maxUses = 10
            usedCount = 10
        }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `ShareLink with maxUses not yet reached allows access`()
    {
        val link = activeLink().apply {
            maxUses = 10
            usedCount = 9
        }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertTrue(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `ShareLink requireMfa denies when mfaSatisfied=false`()
    {
        val link = activeLink().apply { requireMfa = true }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash, mfaSatisfied = false)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `ShareLink requireMfa allows when mfaSatisfied=true`()
    {
        val link = activeLink().apply { requireMfa = true }
        val svc = buildService(link = link, share = coveringShare())
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash, mfaSatisfied = true)

        assertTrue(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `ShareLink whose Share covers a different exchange denies access`()
    {
        val otherExchangeId = UUID.randomUUID()
        val share = coveringShare().apply { resourceId = otherExchangeId }
        val svc = buildService(link = activeLink(), share = share)
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        assertFalse(svc.authorize(PrincipalRef.user(UUID.randomUUID()), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), context).isAllowed)
    }

    @Test
    fun `VERIFICATION_BOOTSTRAP-mode ShareLink never produces a content grant`()
    {
        val link = activeLink().apply { linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP }
        val share = coveringShare()

        val svc = buildService(link = link, share = share)
        val context = AuthorizationContext(shareLinkTokenHash = tokenHash)

        val decision = svc.authorize(
            PrincipalRef(PrincipalKind.USER, UUID.randomUUID()),
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(exchangeId),
            context,
        )
        assertFalse(decision.isAllowed, "A bootstrap-mode link must never resolve as a content grant")
        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `no shareLinkTokenHash in context produces no SHARE_LINK grant`()
    {
        val link = activeLink()
        val share = coveringShare()
        val svc = buildService(link = link, share = share)

        val context = AuthorizationContext(shareLinkTokenHash = null)
        val decision = svc.authorize(
            PrincipalRef.user(UUID.randomUUID()),
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(exchangeId),
            context,
        )
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
    }

    private fun activeLink(): ShareLink = ShareLink().apply {
        this.id = UUID.randomUUID()
        this.shareId = this@PublicLinkShareTest.shareId
        this.tokenHash = this@PublicLinkShareTest.tokenHash
        this.status = ShareLinkStatus.ACTIVE
        this.requireMfa = false
    }

    private fun coveringShare(): Share = Share().apply {
        this.id = shareId
        this.principalKind = PrincipalKind.PUBLIC_LINK
        this.principalId = UUID.randomUUID()
        this.resourceType = ResourceType.EXCHANGE
        this.resourceId = exchangeId
        this.roleName = ExchangeShareRoleName.VIEWER.name
        this.source = ShareSource.LINK
        this.status = ShareStatus.ACTIVE
    }

    private fun buildService(
        link: ShareLink? = null,
        share: Share? = null,
        linkRepo: ShareLinkRepository? = null,
    ): DefaultAuthorizationService
    {
        val shareLinkRepo = linkRepo ?: mock<ShareLinkRepository>().also { repo ->
            if (link != null) whenever(repo.findByTokenHash(tokenHash)).thenReturn(link)
            else whenever(repo.findByTokenHash(any())).thenReturn(null)
        }

        val shareRepo = mock<ShareRepository>().also { repo ->
            whenever(repo.findActiveForPrincipalOnResource(any(), any(), any(), any()))
                .thenReturn(emptyList())
            if (share != null) whenever(repo.findById(shareId)).thenReturn(share)
        }

        val registry = mock<ResourceAuthorizationContextRegistry>()
        whenever(registry.resolution(any<ResourceRef>())).thenReturn(
            ResourceContextResolution.Resolved(
                ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(UUID.randomUUID())),
            ),
        )
        return DefaultAuthorizationService(
            shareRepository = shareRepo,
            shareLinkRepository = shareLinkRepo,
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
            },
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = registry,
        )
    }
}
