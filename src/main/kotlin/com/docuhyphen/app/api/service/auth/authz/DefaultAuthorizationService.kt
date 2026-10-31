package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.*
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
    private val groupShareExpansionPolicy: GroupShareExpansionPolicy = GroupShareExpansionPolicy.UNRESTRICTED,
    private val resourceGrantSources: ResourceGrantSourceRegistry = ResourceGrantSourceRegistry(),
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
            return Decision.Deny(
                Decision.REASON_NO_GRANT,
                "No grants for ${principal.kind}/${principal.id} on $resource"
            )
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
            if (resourceCtx.isArchived && action !in resourceContextRegistry.actionsWhileArchived(resource))
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
        if (inheritance is ParentGrantInheritance.Refused) return emptyList()
        return grantsOn(principal, resource, context, resolution, inheritance)
    }

    private fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
        resolution: ResourceContextResolution,
        inheritance: ParentGrantInheritance = ParentGrantInheritance.None,
    ): List<Grant>
    {
        if (resolution is ResourceContextResolution.Unresolved)
        {
            return emptyList()
        }

        val grants = mutableListOf<Grant>()
        val now = Timestamp.from(Instant.now())

        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectRoleAssignmentGrants(principal, resource, context, now)
        }

        if (principal.kind == PrincipalKind.APPLICATION)
        {
            grants += collectApplicationRoleGrants(principal, resource, context)
        }

        if (principal.kind == PrincipalKind.USER)
        {
            grants += collectOrgMembershipGrants(principal, resource, context, resolution)
        }

        if (principal.kind == PrincipalKind.USER && resource.type == ResourceType.PRINCIPAL_GROUP)
        {
            grants += collectGroupMembershipGrants(principal, resource)
        }

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
                    if (!groupShareExpansionPolicy.expandsToNewMembers(share)) continue
                    grants += share.toGrant(Grant.SourceKind.INHERITED_GROUP_SHARE)
                }
            }
        }

        val linkHash = context.shareLinkTokenHash
        if (linkHash != null)
        {
            val linkShare = resolveLinkGrant(linkHash, resource, context, now)
            if (linkShare != null)
            {
                grants += linkShare
            }
        }

        if (resolution is ResourceContextResolution.Resolved) {
            resourceContextRegistry.kindOf(resource)?.let { kind ->
                resourceGrantSources.sourcesFor(kind).forEach { source ->
                    grants += source.grantsFor(principal, resource, resolution.context)
                }
            }
        }

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

    private fun resolveLinkGrant(
        tokenHash: String,
        resource: ResourceRef,
        context: AuthorizationContext,
        now: Timestamp,
    ): Grant?
    {
        val shareLink = shareLinkRepository.findByTokenHash(tokenHash) ?: return null

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
            !(allowPendingApproval && share.status == ShareStatus.PENDING_APPROVAL)
        )
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
