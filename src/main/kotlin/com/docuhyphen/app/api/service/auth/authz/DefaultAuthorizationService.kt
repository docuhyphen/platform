package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
 *     resolved via [ResourceAuthorizationContextRegistry]. A resource whose type states that it
 *     carries its own authorization facts is denied outright when those facts cannot be produced.
 *  7. Return Allow if required capability is in the union, else Deny.
 *
 * A resource kind may also register that it takes the grants held on its parent resource, which
 * [ParentGrantInheritanceResolver] admits for exactly one owner-matched, cycle-free level, and may
 * register a [ResourcePolicyEvaluator] that narrows an already-allowed decision with facts only
 * that kind understands. Neither is available to a kind that has not registered it.
 *
 * PUBLIC_LINK grants are resolved from the [ShareLinkRepository] when
 * [AuthorizationContext.shareLinkTokenHash] is present in the context. A ShareLink whose
 * [ShareLinkMode] is `VERIFICATION_BOOTSTRAP` never resolves this way, regardless of its
 * constraints or covering Share: that mode only proves recipient contact for a runtime request
 * session minted elsewhere, never a content grant.
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
    private val parentGrantInheritanceResolver: ParentGrantInheritanceResolver =
        ParentGrantInheritanceResolver(ParentGrantInheritancePolicyRegistry(), resourceContextRegistry),
    private val resourcePolicyEvaluatorRegistry: ResourcePolicyEvaluatorRegistry =
        ResourcePolicyEvaluatorRegistry(),
) : AuthorizationService
{
    private val platformAuditGovernanceCapabilities = setOf(
        Capability.AUDIT_EXPORT_APPROVE,
        Capability.AUDIT_RETENTION_MANAGE,
        Capability.AUDIT_LEGAL_HOLD_MANAGE,
        Capability.AUDIT_INTEGRITY_VERIFY,
        Capability.AUDIT_ENGAGEMENT_MANAGE,
    )

    override fun authorize(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Decision
    {
        val resolution = resourceContextRegistry.resolution(resource)
        if (resolution is ResourceContextResolution.Unresolved)
        {
            return Decision.Deny(
                Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED,
                "Authorization context could not be resolved for ${resource.type}",
            )
        }

        val inheritance = parentGrantInheritanceResolver.evaluate(resource, resolution)
        if (inheritance is ParentGrantInheritance.Refused)
        {
            return Decision.Deny(inheritance.reasonCode, inheritance.message)
        }

        val grants = grantsOn(principal, resource, context, resolution, inheritance).toMutableList()
        if (action == Action.EXCHANGE_ACCEPT)
        {
            shareRepository.findDirectForPrincipalOnResource(
                principal.kind,
                principal.id,
                resource.type,
                resource.id,
            )
                .filter { it.status == ShareStatus.PENDING_APPROVAL }
                .mapTo(grants) { it.toGrant(Grant.SourceKind.DIRECT_SHARE) }
            if (principal.kind == PrincipalKind.USER || principal.kind == PrincipalKind.PARTICIPANT)
            {
                principalGroupMemberRepository.findGroupsForPrincipal(principal.kind, principal.id)
                    .filter {
                        it.groupRole == PrincipalGroupRoleName.OWNER ||
                            it.groupRole == PrincipalGroupRoleName.MANAGER
                    }
                    .forEach { membership ->
                        shareRepository.findDirectForPrincipalOnResource(
                            PrincipalKind.PRINCIPAL_GROUP,
                            membership.principalGroupId,
                            resource.type,
                            resource.id,
                        )
                            .filter { it.status == ShareStatus.PENDING_APPROVAL }
                            .mapTo(grants) { it.toGrant(Grant.SourceKind.INHERITED_GROUP_SHARE) }
                    }
            }
        }
        if (grants.isEmpty())
        {
            return Decision.Deny(Decision.REASON_NO_GRANT, "No grants for ${principal.kind}/${principal.id} on $resource")
        }

        val capableGrants = grants.filter { action.required in it.capabilities }
        if (capableGrants.isEmpty())
        {
            return Decision.Deny(Decision.REASON_NO_GRANT, "Missing capability ${action.required} for $action")
        }

        val shareSources = setOf(
            Grant.SourceKind.DIRECT_SHARE,
            Grant.SourceKind.INHERITED_GROUP_SHARE,
            Grant.SourceKind.INHERITED_ORG_SHARE,
            Grant.SourceKind.SHARE_LINK,
        )
        val now = Timestamp.from(Instant.now())
        val capableShareIds = capableGrants
            .filter { it.sourceKind in shareSources }
            .map { it.sourceId }
            .toSet()
        val candidateShares = mutableListOf<Share>()
        candidateShares += shareRepository.findActiveForPrincipalOnResource(
            principal.kind, principal.id, resource.type, resource.id,
        )
        if (principal.kind == PrincipalKind.USER || principal.kind == PrincipalKind.PARTICIPANT)
        {
            principalGroupMemberRepository.findGroupsForPrincipal(principal.kind, principal.id).forEach { membership ->
                candidateShares += shareRepository.findActiveForPrincipalOnResource(
                    PrincipalKind.PRINCIPAL_GROUP,
                    membership.principalGroupId,
                    resource.type,
                    resource.id,
                )
            }
        }
        val capableShares = candidateShares
            .filter { it.id in capableShareIds }
            .distinctBy { it.id }
            .toMutableList()
        capableShareIds
            .filter { shareId -> capableShares.none { it.id == shareId } }
            .mapNotNullTo(capableShares) { shareRepository.findById(it) }
        val validCapableShares = capableShares.filter {
            evaluateShareConstraints(
                share = it,
                context = context,
                now = now,
                allowPendingApproval = action == Action.EXCHANGE_ACCEPT,
            ) == null
        }
        val independentlyCapable = capableGrants.any { it.sourceKind !in shareSources }
        if (!independentlyCapable && validCapableShares.isEmpty())
        {
            return capableShares.asSequence()
                .mapNotNull {
                    evaluateShareConstraints(
                        share = it,
                        context = context,
                        now = now,
                        allowPendingApproval = action == Action.EXCHANGE_ACCEPT,
                    )
                }
                .firstOrNull()
                ?: Decision.Deny(Decision.REASON_NO_GRANT, "No effective grant for $action")
        }

        val effectiveGrants = grants.filter { grant ->
            grant.sourceKind !in shareSources || validCapableShares.any { it.id == grant.sourceId }
        }
        val union = effectiveGrants.flatMap { it.capabilities }.toSet()

        val resourceCtx = (resolution as? ResourceContextResolution.Resolved)?.context
        if (resourceCtx != null && !union.contains(Capability.EXCHANGE_ADMIN))
        {
            if (resourceCtx.isArchived && !(resource.type in setOf(
                    com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST,
                    com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST_REQUIREMENT,
                ) && action in com.docuhyphen.app.api.service.informationrequest.InformationRequestParentPolicy.readActions))
            {
                return Decision.Deny(Decision.REASON_EXCHANGE_ARCHIVED, "Exchange ${resource.id} is archived")
            }
            if (resourceCtx.isSuspended)
            {
                return Decision.Deny(Decision.REASON_EXCHANGE_SUSPENDED, "Exchange ${resource.id} is suspended")
            }
        }

        return decideWithResourcePolicy(
            principal = principal,
            action = action,
            resource = resource,
            resourceContext = resourceCtx,
            capabilities = union,
            context = context,
            obligations = computeObligations(validCapableShares, now),
        )
    }

    /**
     * Gives the resource's own kind the last word on a decision the central capability union has
     * already allowed. A registered evaluator may deny it or attach obligations to it; a kind with
     * no evaluator leaves it exactly as it was.
     */
    private fun decideWithResourcePolicy(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        resourceContext: ResourceAuthorizationContext?,
        capabilities: Set<Capability>,
        context: AuthorizationContext,
        obligations: ShareObligations,
    ): Decision
    {
        val kind = resourceContextRegistry.kindOf(resource) ?: return Decision.Allow(obligations)
        val evaluator = resourcePolicyEvaluatorRegistry.evaluatorFor(kind) ?: return Decision.Allow(obligations)
        if (resourceContext == null)
        {
            return Decision.Deny(
                Decision.REASON_RESOURCE_POLICY_FACTS_UNAVAILABLE,
                "Resource policy for $kind has no resolved resource context to evaluate",
            )
        }

        val request = ResourcePolicyRequest(
            principal = principal,
            action = action,
            resource = resource,
            resourceContext = resourceContext,
            capabilities = capabilities,
            authorizationContext = context,
        )
        return when (val outcome = evaluator.evaluate(request))
        {
            is ResourcePolicyOutcome.Deny -> Decision.Deny(outcome.reasonCode, outcome.message)
            is ResourcePolicyOutcome.FactsUnavailable -> Decision.Deny(
                Decision.REASON_RESOURCE_POLICY_FACTS_UNAVAILABLE,
                outcome.message,
            )

            is ResourcePolicyOutcome.Permit -> Decision.Allow(mergeObligations(obligations, outcome.obligations))
        }
    }

    /** Most restrictive union of two obligation sets. */
    private fun mergeObligations(left: ShareObligations, right: ShareObligations): ShareObligations
    {
        val formats = when
        {
            left.allowedDownloadFormats == null -> right.allowedDownloadFormats
            right.allowedDownloadFormats == null -> left.allowedDownloadFormats
            else -> left.allowedDownloadFormats.intersect(right.allowedDownloadFormats)
        }
        return ShareObligations(
            watermark = left.watermark || right.watermark,
            allowedDownloadFormats = formats,
        )
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
        val resolution = resourceContextRegistry.resolution(resource)
        val inheritance = parentGrantInheritanceResolver.evaluate(resource, resolution)
        // A refused inheritance contributes nothing rather than falling back to the resource's
        // own grants, so a caller listing grants sees exactly what a decision would use.
        if (inheritance is ParentGrantInheritance.Refused) return emptyList()
        return grantsOn(principal, resource, context, resolution, inheritance)
    }

    /**
     * Collects grants against an already-answered [resolution] so one decision resolves the
     * resource's own facts once. [inheritance] is answered by the caller for the same reason and
     * is never re-derived here, which is what keeps parent inheritance to exactly one level.
     */
    private fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
        resolution: ResourceContextResolution,
        inheritance: ParentGrantInheritance = ParentGrantInheritance.None,
    ): List<Grant>
    {
        // A resource that states its own authorization facts contributes nothing while those
        // facts cannot be produced, so no unrelated grant can be mistaken for one scoped to it.
        if (resolution is ResourceContextResolution.Unresolved)
        {
            return emptyList()
        }

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
            grants += collectApplicationRoleGrants(principal, resource, context)
        }

        // 1b) Organization membership role grants. The relevant organization is the one that
        // owns the resource, taken from the resolved resource context. For PRINCIPAL_GROUP
        // resources the group's owning org is used. The active org from context applies only to
        // resource types that carry no resource-level context at all, being the platform
        // control-plane references. Org roles only ever yield ORG_*/GROUP_* capabilities, so this
        // never widens EXCHANGE/DOCUMENT authorization.
        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectOrgMembershipGrants(principal, resource, context, resolution)
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
            if (share.source == com.docuhyphen.app.api.model.entity.ShareSource.INHERITED_FROM_GROUP)
            {
                val parent = share.sourceShareId?.let(shareRepository::findById) ?: continue
                val membership = principalGroupMemberRepository.findMembership(
                    parent.principalId,
                    principal.kind,
                    principal.id,
                )
                if (parent.principalKind != PrincipalKind.PRINCIPAL_GROUP || membership?.isActive != true)
                {
                    continue
                }
            }
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
        // grant. An invalid or exhausted link adds no grant, so the principal gets NO_GRANT.
        val linkHash = context.shareLinkTokenHash
        if (linkHash != null)
        {
            val linkShare = resolveLinkGrant(linkHash, resource, context, now)
            if (linkShare != null)
            {
                grants += linkShare
            }
        }

        // 5) Grants held on the parent resource, when this kind has registered that it takes them.
        // The parent is collected without any inheritance of its own, so exactly one level is
        // followed. Each inherited grant keeps its own source kind and identity, so every share
        // constraint, expiry, and obligation rule continues to apply to it unchanged.
        if (inheritance is ParentGrantInheritance.Inherited)
        {
            grantsOn(
                principal,
                inheritance.parent,
                context,
                inheritance.parentResolution,
                ParentGrantInheritance.None,
            ).forEach { parentGrant ->
                val capabilities = inheritance.policy.inheritedCapabilities(parentGrant.capabilities)
                if (capabilities.isNotEmpty())
                {
                    grants += parentGrant.copy(
                        capabilities = capabilities,
                        inheritedFrom = inheritance.parent,
                    )
                }
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

            val roleCapabilities = RoleCapabilities.forAppRole(ra.roleName)
            val caps = if (resource.type == ResourceType.ORGANIZATION)
            {
                roleCapabilities - platformAuditGovernanceCapabilities
            }
            else
            {
                roleCapabilities
            }
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
        resource: ResourceRef,
        context: AuthorizationContext,
    ): List<Grant>
    {
        val appId = context.applicationId ?: return emptyList()
        if (appId != principal.id) return emptyList()

        val application = applicationService.findActive(appId) ?: return emptyList()
        if (!applicationGrantMatchesOwner(application.ownerOrganizationId, resource))
        {
            return emptyList()
        }
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

    private fun applicationGrantMatchesOwner(applicationOrganizationId: UUID?, resource: ResourceRef): Boolean
    {
        val owner = when
        {
            resource.type == ResourceType.ORGANIZATION -> OwnerContext.Organization(resource.id)
            resource.type == ResourceType.APPLICATION && resource.id == UUID(0, 0) -> OwnerContext.Platform
            else -> resourceContextRegistry.resolve(resource)?.ownerContext ?: return false
        }
        return when (owner)
        {
            OwnerContext.Platform -> applicationOrganizationId == null
            is OwnerContext.Organization -> applicationOrganizationId == owner.organizationId
            is OwnerContext.Personal -> false
        }
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
     * The org used for authorization is the one that owns the target resource, taken from
     * [resolution]. For EXCHANGE resources this means the exchange's stored owner org, not the
     * caller's active org. A personal resource (Personal owner) yields no org membership grants.
     * [AuthorizationContext.activeOrgId] is used only for a type that carries no resource-level
     * context, where no owner was ever recorded to read.
     */
    private fun collectOrgMembershipGrants(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
        resolution: ResourceContextResolution,
    ): List<Grant>
    {
        val orgId = when (resource.type)
        {
            ResourceType.PRINCIPAL_GROUP ->
                principalGroupRepository.findById(resource.id)?.ownerOrganizationId
            else -> when (resolution)
            {
                is ResourceContextResolution.Resolved ->
                    (resolution.context.ownerContext as? OwnerContext.Organization)?.organizationId

                ResourceContextResolution.NotGoverned -> context.activeOrgId
                ResourceContextResolution.Unresolved -> null
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

        // A VERIFICATION_BOOTSTRAP-mode link proves recipient contact for a runtime request
        // session; it never resolves as a content grant, regardless of its covering Share.
        if (shareLink.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP) return null

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
        allowPendingApproval: Boolean = false,
    ): Decision.Deny?
    {
        if (share.status == ShareStatus.EXPIRED || (share.expiresAt != null && !share.expiresAt!!.after(now)))
        {
            return Decision.Deny(Decision.REASON_SHARE_EXPIRED, "Share ${share.id} expired")
        }
        if (share.status != ShareStatus.ACTIVE &&
            !(allowPendingApproval && share.status == ShareStatus.PENDING_APPROVAL))
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
        val base = RoleCapabilities.forShareRole(this.resourceType, this.roleName)
        val caps = ShareConstraints.parse(this.constraintsJson)?.adjustCapabilities(base) ?: base
        return Grant(
            sourceKind = sourceKind,
            sourceId = this.id,
            roleName = this.roleName,
            capabilities = caps,
            expiresAtEpochMillis = this.expiresAt?.time,
        )
    }
}
