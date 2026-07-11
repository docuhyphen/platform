package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

/**
 * Default implementation of [AuthorizationService].
 *
 * Algorithm:
 *
 *  1. Collect direct role assignments for the principal across applicable scopes
 *     (APP, ORG matching the resource owner org, PRINCIPAL_GROUP for groups the principal
 *     is in, RESOURCE for this exact resource).
 *  2. Collect direct shares for the principal on this resource, plus shares granted to
 *     any [com.docuhyphen.app.api.model.entity.PrincipalGroup] the principal belongs to.
 *  3. Translate every grant to a Capability set via [RoleCapabilities].
 *  4. Union the capability sets.
 *  5. Apply per-share constraints (require_mfa, ip_allowlist, expiry, status).
 *  6. Apply resource-state denies: archived or suspended resources deny non-admin writes,
 *     resolved via [ResourceAuthorizationContextRegistry].
 *  7. Return Allow if required capability is in the union, else Deny.
 *
 * PUBLIC_LINK grants are resolved from the [ShareLinkRepository] when
 * [AuthorizationContext.shareLinkTokenHash] is present in the context.
 *
 * NOTE: The following are not yet implemented:
 *   - org-level sharing policy denies (iteration 6)
 *   - nested group membership walking (iteration 2)
 */
@ApplicationScoped
class DefaultAuthorizationService @Inject constructor(
    private val shareRepository: ShareRepository,
    private val shareLinkRepository: ShareLinkRepository,
    private val appRoleAssignmentRepository: AppRoleAssignmentRepository,
    private val principalGroupMemberRepository: PrincipalGroupMemberRepository,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val applicationService: ApplicationService,
    private val resourceContextRegistry: ResourceAuthorizationContextRegistry,
) : AuthorizationService
{
    override fun authorize(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Decision
    {
        val grants = grantsOn(principal, resource, context)
        if (grants.isEmpty())
        {
            return Decision.Deny(Decision.REASON_NO_GRANT, "No grants for ${principal.kind}/${principal.id} on $resource")
        }

        val union = grants.flatMap { it.capabilities }.toSet()
        if (action.required !in union)
        {
            return Decision.Deny(Decision.REASON_NO_GRANT, "Missing capability ${action.required} for $action")
        }

        // Step 6: resource-state denies. Archived and suspended resources block non-admin
        // writes. Admin-capable callers (EXCHANGE_ADMIN) bypass this check.
        val resourceCtx = resourceContextRegistry.resolve(resource)
        if (resourceCtx != null && !union.contains(Capability.EXCHANGE_ADMIN))
        {
            if (resourceCtx.isArchived)
            {
                return Decision.Deny(Decision.REASON_EXCHANGE_ARCHIVED, "Exchange ${resource.id} is archived")
            }
            if (resourceCtx.isSuspended)
            {
                return Decision.Deny(Decision.REASON_EXCHANGE_SUSPENDED, "Exchange ${resource.id} is suspended")
            }
        }

        // Constraint denies are evaluated against the *shares* (not role assignments).
        // A user holding a role that grants the capability still needs to satisfy any
        // share-level MFA/IP constraint that applies, if their access comes via that share.
        val now = Timestamp.from(Instant.now())
        val activeShares = shareRepository.findActiveForPrincipalOnResource(
            principal.kind, principal.id, resource.type, resource.id,
        )
        for (share in activeShares)
        {
            val deny = evaluateShareConstraints(share, context, now)
            if (deny != null)
            {
                // Only block if *all* grants to this principal come via shares (no role
                // assignment is independently sufficient).
                val viaRoleAssignment = grants.any {
                    it.sourceKind == Grant.SourceKind.ROLE_ASSIGNMENT &&
                        action.required in it.capabilities
                }
                if (!viaRoleAssignment)
                {
                    return deny
                }
            }
        }

        return Decision.Allow(computeObligations(activeShares, now))
    }

    override fun capabilities(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Set<Capability> =
        grantsOn(principal, resource, context).flatMap { it.capabilities }.toSet()

    override fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): List<Grant>
    {
        val grants = mutableListOf<Grant>()
        val now = Timestamp.from(Instant.now())

        // 1) Human platform roles apply only to USER principals.
        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectRoleAssignmentGrants(principal, resource, context, now)
        }

        // 1a) APPLICATION principal role grants. The APPLICATION role maps to emptySet() by
        // default; all resource-level capabilities come from explicit Share grants or
        // capability grants. Collecting the role grant here routes APPLICATION principals through
        // the same centralized path rather than relying solely on token scope and endpoint prefix.
        if (principal.kind == PrincipalKind.APPLICATION)
        {
            grants += collectApplicationRoleGrants(principal, context)
        }

        // 1b) Organization membership role grants. The relevant organization is the one that
        // owns the resource, resolved via [ResourceAuthorizationContextRegistry] for EXCHANGE
        // resources. For PRINCIPAL_GROUP resources the group's owning org is used. The
        // active org from context is used only as a fallback for resource types without a
        // registered provider. Org roles only ever yield ORG_*/GROUP_* capabilities, so this
        // never widens EXCHANGE/DOCUMENT authorization.
        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectOrgMembershipGrants(principal, resource, context)
        }

        // 1c) Group-membership role grants. Only meaningful when the resource *is* the group.
        if (principal.kind == PrincipalKind.USER && resource.type == ResourceType.PRINCIPAL_GROUP)
        {
            grants += collectGroupMembershipGrants(principal, resource)
        }

        // 2) Direct shares to this principal on this resource.
        val directShares = shareRepository.findActiveForPrincipalOnResource(
            principal.kind, principal.id, resource.type, resource.id,
        )
        for (share in directShares)
        {
            if (!isShareCurrentlyEffective(share, now)) continue
            grants += share.toGrant(
                if (share.sourceShareId == null) Grant.SourceKind.DIRECT_SHARE
                else Grant.SourceKind.INHERITED_GROUP_SHARE
            )
        }

        // 3) Group-mediated shares: every group the principal is a member of may itself
        // have an active share on this resource.
        if (principal.kind == PrincipalKind.USER || principal.kind == PrincipalKind.PARTICIPANT)
        {
            val groups = principalGroupMemberRepository.findGroupsForPrincipal(principal.kind, principal.id)
            for (gm in groups)
            {
                val groupShares = shareRepository.findActiveForPrincipalOnResource(
                    PrincipalKind.PRINCIPAL_GROUP, gm.principalGroupId, resource.type, resource.id,
                )
                for (share in groupShares)
                {
                    if (!isShareCurrentlyEffective(share, now)) continue
                    val alreadyMaterialised = directShares.any { it.sourceShareId == share.id }
                    if (alreadyMaterialised) continue
                    grants += share.toGrant(Grant.SourceKind.INHERITED_GROUP_SHARE)
                }
            }
        }

        // 4) PUBLIC_LINK: resolve via share link token hash in context.
        // The raw token is presented by the client (X-Share-Link-Token header); the filter
        // hashes it before placing it in AuthorizationContext. If a valid, non-exhausted
        // ShareLink exists and its associated Share covers this resource, we add a SHARE_LINK
        // grant. An invalid/exhausted link adds no grant — the principal gets NO_GRANT.
        val linkHash = context.shareLinkTokenHash
        if (linkHash != null)
        {
            val linkShare = resolveLinkGrant(linkHash, resource, context, now)
            if (linkShare != null)
            {
                grants += linkShare
            }
        }

        return grants
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun collectRoleAssignmentGrants(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
        now: Timestamp,
    ): List<Grant>
    {
        val userId = principal.id.takeIf { principal.kind == PrincipalKind.USER } ?: return emptyList()

        val results = mutableListOf<Grant>()
        val assignments = appRoleAssignmentRepository.findActiveForUser(userId)

        for (ra in assignments)
        {
            if (ra.expiresAt != null && ra.expiresAt!!.before(now)) continue

            val caps = RoleCapabilities.forAppRole(ra.roleName)
            if (caps.isEmpty()) continue

            results += Grant(
                sourceKind = Grant.SourceKind.ROLE_ASSIGNMENT,
                sourceId = ra.id,
                roleName = ra.roleName.name,
                capabilities = caps,
                expiresAtEpochMillis = ra.expiresAt?.time,
            )
        }
        return results
    }

    /**
     * Resolves the registered [com.docuhyphen.app.api.model.entity.Application] and produces a
     * [Grant] combining the APPLICATION role's base capabilities (emptySet by default) with any
     * capabilities explicitly granted to the specific application via
     * [com.docuhyphen.app.api.model.entity.Application.grantedCapabilitiesJson].
     *
     * This is the mechanism by which, for example, a CLM integration receives EXCHANGE_INITIATE
     * for a specific owner organization without inheriting any other Exchange or customer-content
     * capability from the APPLICATION role itself.
     */
    private fun collectApplicationRoleGrants(
        principal: PrincipalRef,
        context: AuthorizationContext,
    ): List<Grant>
    {
        val appId = context.applicationId ?: return emptyList()
        if (appId != principal.id) return emptyList()

        val application = applicationService.findActive(appId) ?: return emptyList()
        val roleCaps = RoleCapabilities.forApplicationRole(application.roleName)
        val grantedCaps = parseApplicationCapabilities(application.grantedCapabilitiesJson)
        val allCaps = roleCaps + grantedCaps

        return listOf(
            Grant(
                sourceKind = Grant.SourceKind.APPLICATION_ROLE,
                sourceId = application.id,
                roleName = application.roleName.name,
                capabilities = allCaps,
                expiresAtEpochMillis = null,
            )
        )
    }

    private fun parseApplicationCapabilities(json: String): Set<Capability>
    {
        val trimmed = json.trim()
        if (trimmed == "[]" || trimmed.isBlank()) return emptySet()
        return trimmed.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
            .mapNotNull { name -> runCatching { Capability.valueOf(name) }.getOrNull() }
            .toSet()
    }

    /**
     * Translates the caller's [com.docuhyphen.app.api.model.entity.OrganizationMembership] role
     * into a [Grant].
     *
     * The org used for authorization is the one that owns the target resource, resolved via
     * [ResourceAuthorizationContextRegistry]. For EXCHANGE resources this means the exchange's
     * stored owner org, not the caller's active org. A personal exchange (Personal owner) yields
     * no org membership grants. The fallback to [AuthorizationContext.activeOrgId] applies only
     * for resource types that have no registered provider.
     */
    private fun collectOrgMembershipGrants(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): List<Grant>
    {
        val orgId = when (resource.type)
        {
            ResourceType.PRINCIPAL_GROUP ->
                principalGroupRepository.findById(resource.id)?.ownerOrganizationId
            else ->
            {
                val resolved = resourceContextRegistry.resolve(resource)
                if (resolved != null)
                    (resolved.ownerContext as? OwnerContext.Organization)?.organizationId
                else
                    context.activeOrgId
            }
        } ?: return emptyList()

        val membership = organizationMembershipRepository.findActiveByUserAndOrg(principal.id, orgId)
            ?: return emptyList()
        return membership.roles.map { role ->
            Grant(
                sourceKind = Grant.SourceKind.ORG_MEMBERSHIP,
                sourceId = membership.id,
                roleName = role.name,
                capabilities = RoleCapabilities.forOrganizationRole(role),
                expiresAtEpochMillis = null,
            )
        }
    }

    /**
     * Translates the caller's Principal Group role within the target
     * group into a [Grant]. Only called when the resource is the group itself.
     */
    private fun collectGroupMembershipGrants(
        principal: PrincipalRef,
        resource: ResourceRef,
    ): List<Grant>
    {
        val member = principalGroupMemberRepository
            .findMembership(resource.id, PrincipalKind.USER, principal.id)
            ?.takeIf { it.isActive }
            ?: return emptyList()
        val caps = RoleCapabilities.forPrincipalGroupRole(member.groupRole)
        if (caps.isEmpty()) return emptyList()

        return listOf(
            Grant(
                sourceKind = Grant.SourceKind.GROUP_MEMBERSHIP,
                sourceId = member.id,
                roleName = member.groupRole.name,
                capabilities = caps,
                expiresAtEpochMillis = null,
            )
        )
    }

    /**
     * Resolves a [com.docuhyphen.app.api.model.entity.ShareLink] from [tokenHash] and, when
     * valid and covering [resource], returns a [Grant.SourceKind.SHARE_LINK] grant.
     *
     * Validation gates (all must pass):
     *  - ShareLink found by token hash.
     *  - ShareLink status is ACTIVE and not past its expiry.
     *  - usedCount is below maxUses (when maxUses is set).
     *  - The associated Share is ACTIVE, not expired, and covers this resource.
     *  - MFA constraint satisfied (when requireMfa = true).
     *
     * Returning null means no grant is added; the principal will receive NO_GRANT.
     */
    private fun resolveLinkGrant(
        tokenHash: String,
        resource: ResourceRef,
        context: AuthorizationContext,
        now: Timestamp,
    ): Grant?
    {
        val shareLink = shareLinkRepository.findByTokenHash(tokenHash) ?: return null

        if (shareLink.status == ShareLinkStatus.REVOKED) return null
        if (shareLink.status == ShareLinkStatus.EXPIRED) return null
        if (shareLink.expiresAt != null && !shareLink.expiresAt!!.after(now)) return null

        val maxUses = shareLink.maxUses
        if (maxUses != null && shareLink.usedCount >= maxUses) return null

        if (shareLink.requireMfa && !context.mfaSatisfied) return null

        val share = shareRepository.findById(shareLink.shareId) ?: return null
        if (!isShareCurrentlyEffective(share, now)) return null
        if (share.resourceType != resource.type || share.resourceId != resource.id) return null

        return share.toGrant(Grant.SourceKind.SHARE_LINK)
    }

    private fun isShareCurrentlyEffective(share: Share, now: Timestamp): Boolean
    {
        if (share.status != ShareStatus.ACTIVE) return false
        val exp = share.expiresAt
        if (exp != null && !exp.after(now)) return false
        return true
    }

    /**
     * Returns a [Decision.Deny] if a share's constraints disallow access in this
     * [AuthorizationContext]; null if the constraints pass.
     *
     * Enforces status / expiry / require_mfa / ip_allowlist. Capability-shaping constraints
     * (can_download, can_reshare) are applied in [toGrant] so they fold into the
     * capability-union check; watermark / allowedDownloadFormats are non-blocking obligations
     * attached to the [Decision] in [computeObligations].
     */
    private fun evaluateShareConstraints(
        share: Share,
        context: AuthorizationContext,
        now: Timestamp,
    ): Decision.Deny?
    {
        if (share.status == ShareStatus.EXPIRED || (share.expiresAt != null && !share.expiresAt!!.after(now)))
        {
            return Decision.Deny(Decision.REASON_SHARE_EXPIRED, "Share ${share.id} expired")
        }
        if (share.status != ShareStatus.ACTIVE)
        {
            return Decision.Deny(Decision.REASON_SHARE_NOT_ACTIVE, "Share ${share.id} status=${share.status}")
        }

        val constraints = ShareConstraints.parse(share.constraintsJson)
            ?: return Decision.Deny(Decision.REASON_INVALID_CONSTRAINTS, "Share ${share.id} has malformed constraints")

        if (constraints.requireMfa && !context.mfaSatisfied)
        {
            return Decision.Deny(Decision.REASON_MFA_REQUIRED, "Share ${share.id} requires MFA")
        }

        val ranges = constraints.allowedIpRanges
        if (!ranges.isNullOrEmpty() && !ShareConstraints.isIpAllowed(context.clientIp, ranges))
        {
            return Decision.Deny(
                Decision.REASON_IP_DENIED,
                "Share ${share.id} denied: client IP ${context.clientIp} not in allowed ranges",
            )
        }

        return null
    }

    /**
     * Most-restrictive union of obligations across the principal's currently-effective
     * shares: any watermark wins; the intersection of all allowed download format sets wins
     * (a null from any share means unrestricted; otherwise intersect all non-null sets).
     */
    private fun computeObligations(shares: List<Share>, now: Timestamp): ShareObligations
    {
        var watermark = false
        var formatRestricted = false
        var allowedFormats: Set<String>? = null

        for (share in shares)
        {
            if (!isShareCurrentlyEffective(share, now)) continue
            val c = ShareConstraints.parse(share.constraintsJson) ?: continue
            if (c.watermark) watermark = true
            val formats = c.allowedDownloadFormats
            if (formats != null)
            {
                formatRestricted = true
                allowedFormats = if (allowedFormats == null) formats.toSet()
                else allowedFormats!!.intersect(formats.toSet())
            }
        }

        return ShareObligations(
            watermark = watermark,
            allowedDownloadFormats = if (formatRestricted) allowedFormats ?: emptySet() else null,
        )
    }

    private fun Share.toGrant(sourceKind: Grant.SourceKind): Grant
    {
        val base = RoleCapabilities.forExchangeShareRole(this.roleName)
        val caps = ShareConstraints.parse(this.constraintsJson)?.adjustCapabilities(base) ?: base
        return Grant(
            sourceKind = sourceKind,
            sourceId = this.id,
            roleName = this.roleName.name,
            capabilities = caps,
            expiresAtEpochMillis = this.expiresAt?.time,
        )
    }
}
