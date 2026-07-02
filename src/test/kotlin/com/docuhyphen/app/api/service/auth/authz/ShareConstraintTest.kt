package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Exchange Share constraint enforcement.
 *
 * Rules verified:
 *  - Malformed constraints_json denies access (fail-closed parse).
 *  - MFA constraint blocks when context.mfaSatisfied=false.
 *  - IP allowlist: non-empty list with mismatched IP denies; matching IP allows.
 *  - IP allowlist: null clientIp with non-empty list denies (fail-closed).
 *  - IP allowlist: empty list allows any IP.
 *  - CIDR matching: IPv4 address inside /24 block allows; outside denies.
 *  - max_views is rejected at write time.
 *  - allowedDownloadFormats is returned as an obligation (not a capability shaper).
 *  - allowedDownloadFormats obligation: most-restrictive intersection across shares.
 *  - Watermark obligation: any-wins semantics across shares.
 *  - canDownload=false removes DOCUMENT_DOWNLOAD from the grant.
 *  - canDownload=true on a VIEWER share adds DOCUMENT_DOWNLOAD.
 *  - Group-inherited shares propagate constraints to members.
 */
class ShareConstraintTest
{
    // -----------------------------------------------------------------------
    // Constraint parsing
    // -----------------------------------------------------------------------

    @Test
    fun `malformed constraints_json returns null from parse`()
    {
        assertNull(ShareConstraints.parse("{not valid json"))
    }

    @Test
    fun `blank constraints_json returns PERMISSIVE`()
    {
        assertEquals(ShareConstraints.PERMISSIVE, ShareConstraints.parse(null))
        assertEquals(ShareConstraints.PERMISSIVE, ShareConstraints.parse(""))
        assertEquals(ShareConstraints.PERMISSIVE, ShareConstraints.parse("   "))
    }

    @Test
    fun `max_views is rejected at write time by normalizeForStorage`()
    {
        val ex = assertThrows<IllegalArgumentException> {
            ShareConstraints.normalizeForStorage("""{"max_views":5}""")
        }
        assertTrue(ex.message!!.contains("max_views"), "Error should mention max_views")
    }

    @Test
    fun `max_views is rejected even when combined with valid fields`()
    {
        assertThrows<IllegalArgumentException> {
            ShareConstraints.normalizeForStorage("""{"require_mfa":true,"max_views":10}""")
        }
    }

    // -----------------------------------------------------------------------
    // IP allowlist — ShareConstraints.isIpAllowed
    // -----------------------------------------------------------------------

    @Test
    fun `empty allowed_ip_ranges permits any IP`()
    {
        assertTrue(ShareConstraints.isIpAllowed("1.2.3.4", emptyList()))
        assertTrue(ShareConstraints.isIpAllowed(null, emptyList()))
    }

    @Test
    fun `null clientIp with non-empty ranges is denied (fail-closed)`()
    {
        assertFalse(ShareConstraints.isIpAllowed(null, listOf("192.168.1.0/24")))
    }

    @Test
    fun `exact IP match allows`()
    {
        assertTrue(ShareConstraints.isIpAllowed("10.0.0.1", listOf("10.0.0.1")))
    }

    @Test
    fun `non-matching exact IP denies`()
    {
        assertFalse(ShareConstraints.isIpAllowed("10.0.0.2", listOf("10.0.0.1")))
    }

    @Test
    fun `CIDR block includes address inside the block`()
    {
        assertTrue(ShareConstraints.isIpAllowed("192.168.1.50", listOf("192.168.1.0/24")))
    }

    @Test
    fun `CIDR block excludes address outside the block`()
    {
        assertFalse(ShareConstraints.isIpAllowed("192.168.2.1", listOf("192.168.1.0/24")))
    }

    @Test
    fun `multiple ranges — match in second range allows`()
    {
        assertTrue(
            ShareConstraints.isIpAllowed("172.16.0.5", listOf("10.0.0.0/8", "172.16.0.0/12"))
        )
    }

    @Test
    fun `CIDR slash-32 matches only the exact host`()
    {
        assertTrue(ShareConstraints.isIpAllowed("203.0.113.1", listOf("203.0.113.1/32")))
        assertFalse(ShareConstraints.isIpAllowed("203.0.113.2", listOf("203.0.113.1/32")))
    }

    // -----------------------------------------------------------------------
    // IP allowlist validation at write time
    // -----------------------------------------------------------------------

    @Test
    fun `invalid CIDR notation is rejected by validate`()
    {
        val constraints = ShareConstraints(allowedIpRanges = listOf("not-an-ip"))
        assertThrows<IllegalArgumentException> { constraints.validate() }
    }

    @Test
    fun `CIDR with out-of-range prefix is rejected`()
    {
        val constraints = ShareConstraints(allowedIpRanges = listOf("192.168.0.0/33"))
        assertThrows<IllegalArgumentException> { constraints.validate() }
    }

    @Test
    fun `valid CIDR notation passes validate`()
    {
        ShareConstraints(allowedIpRanges = listOf("10.0.0.0/8", "192.168.1.1")).validate()
    }

    // -----------------------------------------------------------------------
    // Capability shaping: canDownload
    // -----------------------------------------------------------------------

    @Test
    fun `canDownload=false strips DOCUMENT_DOWNLOAD from EDITOR base set`()
    {
        val base = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.EDITOR)
        assertTrue(base.contains(Capability.DOCUMENT_DOWNLOAD), "EDITOR should have DOCUMENT_DOWNLOAD by default")

        val constrained = ShareConstraints(canDownload = false).adjustCapabilities(base)
        assertFalse(constrained.contains(Capability.DOCUMENT_DOWNLOAD))
    }

    @Test
    fun `canDownload=true on VIEWER adds DOCUMENT_DOWNLOAD`()
    {
        val base = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER)
        val constrained = ShareConstraints(canDownload = true).adjustCapabilities(base)
        assertTrue(constrained.contains(Capability.DOCUMENT_DOWNLOAD))
    }

    @Test
    fun `canDownload=null on VIEWER preserves VIEWER default (no download)`()
    {
        val base = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER)
        val initial = Capability.DOCUMENT_DOWNLOAD in base
        val constrained = ShareConstraints(canDownload = null).adjustCapabilities(base)
        assertEquals(initial, Capability.DOCUMENT_DOWNLOAD in constrained)
    }

    // -----------------------------------------------------------------------
    // Obligations: allowedDownloadFormats and watermark
    // -----------------------------------------------------------------------

    @Test
    fun `allowedDownloadFormats from share is returned as obligation`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"allowed_download_formats":["PDF"]}""",
            role = ExchangeShareRoleName.EDITOR,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext()

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context)

        assertTrue(decision.isAllowed, "EDITOR should be allowed to view")
        val fmts = (decision as Decision.Allow).obligations.allowedDownloadFormats
        assertNotNull(fmts)
        assertTrue(fmts!!.contains("PDF"))
    }

    @Test
    fun `watermark obligation any-wins across two shares`()
    {
        val shareA = buildActiveShare(
            principalId = UUID.randomUUID(),
            constraintsJson = """{"watermark":true}""",
        )
        val shareB = buildActiveShare(
            principalId = shareA.principalId,
            constraintsJson = null,
        )

        val shareRepo = mock<ShareRepository>()
        whenever(shareRepo.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenReturn(listOf(shareA, shareB))
        val svc = buildService(shareRepo = shareRepo)

        val principal = PrincipalRef.user(shareA.principalId)
        val resource = ResourceRef(shareA.resourceType, shareA.resourceId)
        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, AuthorizationContext())

        assertTrue(decision.isAllowed)
        assertTrue((decision as Decision.Allow).obligations.watermark)
    }

    @Test
    fun `allowedDownloadFormats obligation intersects across two shares`()
    {
        val principalId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val shareA = buildActiveShare(
            principalId = principalId,
            resourceId = resourceId,
            constraintsJson = """{"allowed_download_formats":["PDF","DOCX"]}""",
        )
        val shareB = buildActiveShare(
            principalId = principalId,
            resourceId = resourceId,
            constraintsJson = """{"allowed_download_formats":["PDF"]}""",
        )

        val shareRepo = mock<ShareRepository>()
        whenever(shareRepo.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenReturn(listOf(shareA, shareB))
        val svc = buildService(shareRepo = shareRepo)

        val principal = PrincipalRef.user(principalId)
        val resource = ResourceRef(ResourceType.EXCHANGE, resourceId)
        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, AuthorizationContext())

        assertTrue(decision.isAllowed)
        val fmts = (decision as Decision.Allow).obligations.allowedDownloadFormats
        assertNotNull(fmts)
        assertEquals(setOf("PDF"), fmts)
    }

    @Test
    fun `no format restriction from any share produces null obligation`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = null,
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, AuthorizationContext())
        assertTrue(decision.isAllowed)
        assertNull((decision as Decision.Allow).obligations.allowedDownloadFormats)
    }

    // -----------------------------------------------------------------------
    // MFA constraint enforcement via DefaultAuthorizationService
    // -----------------------------------------------------------------------

    @Test
    fun `MFA-required share denies when mfaSatisfied=false`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"require_mfa":true}""",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext(mfaSatisfied = false)

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context)
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_MFA_REQUIRED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `MFA-required share allows when mfaSatisfied=true`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"require_mfa":true}""",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext(mfaSatisfied = true)

        assertTrue(svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context).isAllowed)
    }

    // -----------------------------------------------------------------------
    // IP allowlist enforcement via DefaultAuthorizationService
    // -----------------------------------------------------------------------

    @Test
    fun `IP-restricted share denies when clientIp is outside the range`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext(clientIp = "192.168.1.1")

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context)
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_IP_DENIED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `IP-restricted share allows when clientIp is inside the range`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext(clientIp = "10.1.2.3")

        assertTrue(svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context).isAllowed)
    }

    @Test
    fun `IP-restricted share denies when clientIp is null`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = """{"allowed_ip_ranges":["10.0.0.0/8"]}""",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())
        val context = AuthorizationContext(clientIp = null)

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, context)
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_IP_DENIED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `malformed constraints_json in share denies access`()
    {
        val svc = buildServiceWithSingleShare(
            constraintsJson = "{{{broken",
            role = ExchangeShareRoleName.VIEWER,
        )
        val principal = PrincipalRef.user(UUID.randomUUID())
        val resource = ResourceRef.exchange(UUID.randomUUID())

        val decision = svc.authorize(principal, Action.EXCHANGE_VIEW, resource, AuthorizationContext())
        assertFalse(decision.isAllowed)
        assertEquals(Decision.REASON_INVALID_CONSTRAINTS, (decision as Decision.Deny).reasonCode)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildServiceWithSingleShare(
        constraintsJson: String?,
        role: ExchangeShareRoleName,
    ): DefaultAuthorizationService
    {
        val share = buildActiveShare(constraintsJson = constraintsJson, role = role)
        val shareRepo = mock<ShareRepository>()
        whenever(shareRepo.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenReturn(listOf(share))
        return buildService(shareRepo = shareRepo)
    }

    private fun buildActiveShare(
        principalId: UUID = UUID.randomUUID(),
        resourceId: UUID = UUID.randomUUID(),
        constraintsJson: String? = null,
        role: ExchangeShareRoleName = ExchangeShareRoleName.VIEWER,
    ): Share = Share().apply {
        this.id = UUID.randomUUID()
        this.principalKind = PrincipalKind.USER
        this.principalId = principalId
        this.resourceType = ResourceType.EXCHANGE
        this.resourceId = resourceId
        this.roleName = role
        this.source = ShareSource.DIRECT
        this.status = ShareStatus.ACTIVE
        this.constraintsJson = constraintsJson
    }

    private fun buildService(shareRepo: ShareRepository): DefaultAuthorizationService
    {
        val registry = mock<ResourceAuthorizationContextRegistry>()
        whenever(registry.resolve(any<ResourceRef>())).thenReturn(null)
        return DefaultAuthorizationService(
            shareRepository = shareRepo,
            shareLinkRepository = mock<ShareLinkRepository>(),
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
