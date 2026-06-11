package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.RoleAssignmentRepository
import com.docuhyphen.app.api.repository.ShareRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

/**
 * Default implementation of [AuthorizationService].
 *
 * Algorithm (see plan-sharingCollaborationRedesign.v2.prompt.md §2.1):
 *
 *  1. Collect direct role assignments for the principal across applicable scopes
 *     (APP, ORG matching activeOrgId, PRINCIPAL_GROUP for groups the principal is in,
 *     RESOURCE for this exact resource).
 *  2. Collect direct shares for the principal on this resource, plus shares granted to
 *     any [PrincipalGroup] the principal belongs to (V8 caps nesting depth at 1, true
 *     transitive membership lands in a later iteration).
 *  3. Translate every grant to a Capability set via [RoleCapabilities].
 *  4. Union the capability sets.
 *  5. Apply per-share constraints (require_mfa, ip_allowlist, expiry, status).
 *  6. Apply resource-state denies (SUSPENDED / ARCHIVED session denies non-admin writes
 *    , looked up in iteration 4 once Exchange state machine ships).
 *  7. Return Allow if required capability ∈ union, else Deny.
 *
 * NOTE: This is the *foundational* implementation. It intentionally does not yet:
 *   - resolve PUBLIC_LINK tokens (iteration 5)
 *   - enforce Exchange state machine (iteration 4)
 *   - apply org-level sharing policy denies (iteration 6)
 *   - walk nested group membership (iteration 2)
 * Each of those is a localised follow-up; the contract above doesn't change.
 */
@ApplicationScoped
class DefaultAuthorizationService : AuthorizationService
{
    @Inject private lateinit var shareRepository: ShareRepository
    @Inject private lateinit var roleAssignmentRepository: RoleAssignmentRepository
    @Inject private lateinit var principalGroupMemberRepository: PrincipalGroupMemberRepository
    @Inject private lateinit var organizationMembershipRepository: OrganizationMembershipRepository
    @Inject private lateinit var principalGroupRepository: PrincipalGroupRepository

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
                // assignment is independently sufficient). The simple rule for V8: if a
                // share-derived grant is the only source of `action.required`, the
                // share's constraints must pass.
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

        // 1) Role assignments, only for USER / SERVICE_ACCOUNT principals.
        if (principal.kind == PrincipalKind.USER || principal.kind == PrincipalKind.SERVICE_ACCOUNT)
        {
            grants += collectRoleAssignmentGrants(principal, resource, context, now)
        }

        // 1b) Org-membership role grants. Org roles live on `organization_membership.role_name`
        // (NOT in role_assignment), so without this bridge an ORG_ADMIN/ORG_OWNER would hold no
        // capabilities at all. The relevant org is the one that owns the resource when it is a
        // group (so group management is checked against the *group's* org, not whatever org the
        // caller happens to have active), otherwise the caller's active org from context.
        // Org roles only ever yield ORG_*/GROUP_* capabilities (see RoleCapabilities), so this
        // never widens SESSION/DOCUMENT authorization.
        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectOrgMembershipGrants(principal, resource, context)
        }

        // 1c) Group-membership role grants. Only meaningful when the resource *is* the group:
        // a member's GroupRole (OWNER/MANAGER/MEMBER/OBSERVER) maps to capabilities on that
        // group. This is what lets a group OWNER/MANAGER manage members without being an org
        // admin. Bounded to PRINCIPAL_GROUP resources, so blast radius is nil elsewhere.
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
        // have an active share on this resource. Materialised inheritance is preferred
        // (V8 backfills + iteration-4 service refactor will create INHERITED_FROM_GROUP
        // rows), but we still walk groups here to cover groups whose shares haven't
        // been materialised yet.
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
                    // Avoid double-counting if a materialised INHERITED_FROM_GROUP row
                    // already exists for this principal pointing at this share.
                    val alreadyMaterialised = directShares.any { it.sourceShareId == share.id }
                    if (alreadyMaterialised) continue
                    grants += share.toGrant(Grant.SourceKind.INHERITED_GROUP_SHARE)
                }
            }
        }

        // 4) PUBLIC_LINK: deferred to iteration 5, once ShareLinkValidationService
        // resolves the token, the caller passes the resulting Share as a synthetic
        // grant on the AuthorizationContext.

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
        // SERVICE_ACCOUNT role lookup is symmetric, added in iteration 2.

        val results = mutableListOf<Grant>()
        val assignments = roleAssignmentRepository.findActiveForUser(userId)

        for (ra in assignments)
        {
            if (ra.expiresAt != null && ra.expiresAt!!.before(now)) continue

            val applies = when (ra.scopeType)
            {
                RoleScopeType.APP -> true
                RoleScopeType.ORG -> ra.scopeId == context.activeOrgId
                RoleScopeType.PRINCIPAL_GROUP -> isPrincipalInGroup(principal, ra.scopeId)
                RoleScopeType.RESOURCE -> ra.scopeId == resource.id
            }
            if (!applies) continue

            val caps = RoleCapabilities.forRole(ra.roleName)
            if (caps.isEmpty()) continue

            results += Grant(
                sourceKind = Grant.SourceKind.ROLE_ASSIGNMENT,
                sourceId = ra.id,
                roleName = ra.roleName,
                capabilities = caps,
                expiresAtEpochMillis = ra.expiresAt?.time,
            )
        }
        return results
    }

    /**
     * Translates the caller's [com.docuhyphen.app.api.model.entity.OrganizationMembership] role
     * into a [Grant]. The org chosen is the resource's owning org when the resource is a group
     * (so group ops are authorised against the group's org), else the active org from context.
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
            else -> context.activeOrgId
        } ?: return emptyList()

        val membership = organizationMembershipRepository.findActiveByUserAndOrg(principal.id, orgId)
            ?: return emptyList()
        val caps = RoleCapabilities.forRole(membership.roleName)
        if (caps.isEmpty()) return emptyList()

        return listOf(
            Grant(
                sourceKind = Grant.SourceKind.ORG_MEMBERSHIP,
                sourceId = membership.id,
                roleName = membership.roleName,
                capabilities = caps,
                expiresAtEpochMillis = null,
            )
        )
    }

    /**
     * Translates the caller's [com.docuhyphen.app.api.model.entity.GroupRole] within the target
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
        val caps = RoleCapabilities.forRole(member.groupRole.name)
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

    private fun isPrincipalInGroup(principal: PrincipalRef, groupId: java.util.UUID?): Boolean
    {
        if (groupId == null) return false
        val kind = when (principal.kind)
        {
            PrincipalKind.USER, PrincipalKind.PARTICIPANT -> principal.kind
            else -> return false
        }
        return principalGroupMemberRepository.findMembership(groupId, kind, principal.id) != null
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
     * [AuthorizationContext]; null if it's fine.
     *
     * Enforces status / expiry / require_mfa. Capability-shaping constraints
     * (`can_download`, `can_reshare`) are applied in [toGrant] so they fold into the
     * capability-union check; watermark / max_views are non-blocking obligations attached
     * to the [Decision] in [computeObligations].
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
        if (constraints.requireMfa && !context.mfaSatisfied)
        {
            return Decision.Deny(Decision.REASON_MFA_REQUIRED, "Share ${share.id} requires MFA")
        }
        return null
    }

    /**
     * Most-restrictive union of obligations across the principal's currently-effective
     * shares: any watermark wins; the smallest declared max_views wins.
     */
    private fun computeObligations(shares: List<Share>, now: Timestamp): ShareObligations
    {
        var watermark = false
        var maxViews: Int? = null
        for (share in shares)
        {
            if (!isShareCurrentlyEffective(share, now)) continue
            val c = ShareConstraints.parse(share.constraintsJson)
            if (c.watermark) watermark = true
            val mv = c.maxViews
            if (mv != null) maxViews = if (maxViews == null) mv else minOf(maxViews!!, mv)
        }
        return ShareObligations(watermark = watermark, maxViews = maxViews)
    }

    private fun Share.toGrant(sourceKind: Grant.SourceKind): Grant
    {
        // Base role caps, then shaped by this share's constraints: VIEWER/PARTICIPANT only
        // gain DOCUMENT_DOWNLOAD via can_download=true; an explicit can_download=false strips
        // it from richer roles; can_reshare=false strips EXCHANGE_SHARE.
        val base = RoleCapabilities.forRole(this.roleName)
        val caps = ShareConstraints.parse(this.constraintsJson).adjustCapabilities(base)
        return Grant(
            sourceKind = sourceKind,
            sourceId = this.id,
            roleName = this.roleName,
            capabilities = caps,
            expiresAtEpochMillis = this.expiresAt?.time,
        )
    }
}



