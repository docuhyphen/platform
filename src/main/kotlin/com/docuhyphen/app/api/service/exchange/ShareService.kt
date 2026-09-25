package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Write-side service for the unified [Share] model. The legacy recipient columns
 * (`recipient_id`, `recipient_type`, `group_id`, `allow_document_*`) were removed;
 * all access is now resolved exclusively through [Share] rows.
 *
 * Group inheritance is *materialised*: sharing with a `PRINCIPAL_GROUP` creates the DIRECT
 * grant for the group plus `INHERITED_FROM_GROUP` rows for each active member (pointing back
 * via `sourceShareId`), keeping authorization a flat query rather than a recursive walk.
 */
@ApplicationScoped
class ShareService @Inject constructor(
    private val shareRepository: ShareRepository,
    private val groupMemberRepository: PrincipalGroupMemberRepository,
    private val auditRecorder: AuditRecorder,
    private val resourceAuthorizationContextRegistry: ResourceAuthorizationContextRegistry,
    private val exchangeRecipientAttestationService: ExchangeRecipientAttestationService,
    private val trustedRecipientValidationService: TrustedRecipientValidationService,
    private val exchangeRecipientServiceProvider: Provider<ExchangeRecipientService>,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ShareService::class.java)
    }

    fun getById(shareId: UUID): Share? = shareRepository.findById(shareId)

    fun findDirectForPrincipalOnResource(
        principalKind: PrincipalKind,
        principalId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): Share? =
        shareRepository.findDirectForPrincipalOnResource(
            principalKind,
            principalId,
            resourceType,
            resourceId,
        ).firstOrNull()

    /**
     * Grant (or refresh) a DIRECT share of [resourceType]/[resourceId] to a principal.
     * Idempotent: an existing ACTIVE DIRECT share to the same principal is updated in place
     * rather than duplicated. For group principals, member inheritance is (re)materialised.
     */
    fun grant(
        resourceType: ResourceType,
        resourceId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
        roleName: ExchangeShareRoleName,
        grantedBy: PrincipalRef? = null,
        source: ShareSource = ShareSource.DIRECT,
        constraintsJson: String? = null,
        expiresAt: Timestamp? = null,
        status: ShareStatus = ShareStatus.ACTIVE,
        resourceLabel: String? = null,
    ): Share =
        grantInternal(
            resourceType = resourceType,
            resourceId = resourceId,
            principalKind = principalKind,
            principalId = principalId,
            roleName = roleName.name,
            grantor = grantedBy,
            source = source,
            constraintsJson = constraintsJson,
            expiresAt = expiresAt,
            status = status,
            resourceLabel = resourceLabel,
        )

    fun grantRoleKeyWithPrincipalProvenance(
        resourceType: ResourceType,
        resourceId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
        roleName: String,
        grantedByPrincipal: PrincipalRef? = null,
        source: ShareSource = ShareSource.DIRECT,
        constraintsJson: String? = null,
        expiresAt: Timestamp? = null,
        status: ShareStatus = ShareStatus.ACTIVE,
        resourceLabel: String? = null,
    ): Share =
        grantInternal(
            resourceType = resourceType,
            resourceId = resourceId,
            principalKind = principalKind,
            principalId = principalId,
            roleName = roleName,
            grantor = grantedByPrincipal,
            source = source,
            constraintsJson = constraintsJson,
            expiresAt = expiresAt,
            status = status,
            resourceLabel = resourceLabel,
        )

    private fun grantInternal(
        resourceType: ResourceType,
        resourceId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
        roleName: String,
        grantor: PrincipalRef?,
        source: ShareSource,
        constraintsJson: String?,
        expiresAt: Timestamp?,
        status: ShareStatus,
        resourceLabel: String?,
    ): Share
    {
        val existingDirect = shareRepository
            .findActiveForPrincipalOnResource(principalKind, principalId, resourceType, resourceId)
            .firstOrNull { it.source == source && it.sourceShareId == null }

        val previouslyDirect = if (existingDirect == null && source == ShareSource.DIRECT)
            shareRepository.findDirectForPrincipalOnResource(principalKind, principalId, resourceType, resourceId).firstOrNull()
        else null

        val share = (existingDirect ?: previouslyDirect ?: Share()).apply {
            this.resourceType = resourceType
            this.resourceId = resourceId
            this.principalKind = principalKind
            this.principalId = principalId
            require(RoleCapabilities.isShareRoleValid(resourceType, roleName)) {
                "Role $roleName is not valid for $resourceType"
            }
            this.roleName = roleName
            this.source = source
            this.sourceShareId = null
            this.status = status
            recordGrantor(this, grantor)
            this.constraintsJson = constraintsJson
            this.expiresAt = expiresAt
            if (existingDirect == null)
            {
                this.grantedAt = Timestamp.from(Instant.now())
            }
            this.revokedAt = null
            this.revokedByPrincipalKind = null
            this.revokedByPrincipalId = null
        }
        val persisted = if (existingDirect == null && previouslyDirect == null) shareRepository.save(share) else shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_GRANT,
            share = persisted,
            actor = grantor,
            extraPayload = mapOf("role_name" to roleName, "principal_kind" to principalKind.name),
            resourceLabel = resourceLabel,
        )

        if (principalKind == PrincipalKind.PRINCIPAL_GROUP && status == ShareStatus.ACTIVE)
        {
            materialiseGroupInheritance(persisted)
        }
        return persisted
    }

    /**
     * Create `INHERITED_FROM_GROUP` shares for each active member of the group named by
     * [parentShare]. Re-runnable: members already holding the inherited share are left as-is.
     *
     * Individual inherited-member shares are not each given their own ledger event: for a group
     * with many members that would be audit noise unrelated to a distinct human decision. The
     * triggering [grant]/[activate] call already records one event with
     * `principal_kind=PRINCIPAL_GROUP` in its payload, which is enough to discover that
     * inheritance was re-materialized; this preserves the scope of the original grant.
     */
    private fun materialiseGroupInheritance(parentShare: Share)
    {
        val members = groupMemberRepository.findActiveMembers(parentShare.principalId)
        for (member in members)
        {
            if (member.principalKind != PrincipalKind.USER && member.principalKind != PrincipalKind.PARTICIPANT)
            {
                continue
            }
            synchronizeInheritedShare(parentShare, member.principalKind, member.principalId, true)
        }
    }

    fun synchronizeGroupMemberAccess(
        groupId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
        active: Boolean,
    )
    {
        shareRepository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)
            .filter { it.source == ShareSource.DIRECT }
            .forEach { parentShare ->
                if (active && !canExpandTrustedGroup(parentShare))
                {
                    return@forEach
                }
                synchronizeInheritedShare(parentShare, principalKind, principalId, active)
            }
    }

    fun revokeGroupAccess(groupId: UUID)
    {
        shareRepository.findAllForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)
            .filter { it.source == ShareSource.DIRECT && it.status != ShareStatus.REVOKED }
            .forEach { revoke(it.id) }
    }

    private fun synchronizeInheritedShare(
        parentShare: Share,
        principalKind: PrincipalKind,
        principalId: UUID,
        active: Boolean,
    )
    {
        val existing = shareRepository.findBySourceShareId(parentShare.id)
            .filter { it.principalKind == principalKind && it.principalId == principalId }
        if (!active)
        {
            val now = Timestamp.from(Instant.now())
            existing.forEach { markRevoked(it, null, now) }
            return
        }

        val inherited = (existing.firstOrNull() ?: Share()).apply {
            resourceType = parentShare.resourceType
            resourceId = parentShare.resourceId
            this.principalKind = principalKind
            this.principalId = principalId
            roleName = parentShare.roleName
            source = ShareSource.INHERITED_FROM_GROUP
            sourceShareId = parentShare.id
            status = ShareStatus.ACTIVE
            grantedByPrincipalKind = parentShare.grantedByPrincipalKind
            grantedByPrincipalId = parentShare.grantedByPrincipalId
            constraintsJson = parentShare.constraintsJson
            expiresAt = parentShare.expiresAt
            revokedAt = null
            revokedByPrincipalKind = null
            revokedByPrincipalId = null
        }
        if (existing.isEmpty()) shareRepository.save(inherited) else shareRepository.update(inherited)
        existing.drop(1).forEach { duplicate ->
            markRevoked(duplicate, null, Timestamp.from(Instant.now()))
        }
    }

    /**
     * Promote a PENDING_APPROVAL share to ACTIVE (e.g. after an approval workflow completes).
     * For a group's DIRECT grant this also materialises member inheritance, which is skipped
     * while the grant is pending. No-op (returns the share unchanged) if it isn't pending.
     */
    fun activate(shareId: UUID): Share?
    {
        val share = shareRepository.findById(shareId) ?: return null
        if (share.status != ShareStatus.PENDING_APPROVAL) return share
        if (share.principalKind == PrincipalKind.PRINCIPAL_GROUP && !canExpandTrustedGroup(share))
        {
            throw IllegalArgumentException("Trusted group is no longer eligible")
        }
        share.status = ShareStatus.ACTIVE
        val activated = shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_ACTIVATE,
            share = activated,
            actor = null,
            extraPayload = mapOf("role_name" to activated.roleName, "principal_kind" to activated.principalKind.name),
        )

        if (activated.principalKind == PrincipalKind.PRINCIPAL_GROUP)
        {
            materialiseGroupInheritance(activated)
        }
        return activated
    }

    fun reconcileGroupShare(shareId: UUID)
    {
        val share = shareRepository.findById(shareId) ?: return
        if (share.status == ShareStatus.ACTIVE && share.principalKind == PrincipalKind.PRINCIPAL_GROUP &&
            canExpandTrustedGroup(share))
        {
            materialiseGroupInheritance(share)
        }
    }

    private fun canExpandTrustedGroup(parentShare: Share): Boolean
    {
        val attestation = exchangeRecipientAttestationService.findForDirectShare(parentShare.id) ?: return true
        return trustedRecipientValidationService.isGroupAttestationCurrentlyEligible(attestation)
    }

    /** Promote every PENDING_APPROVAL share on a resource to ACTIVE; returns the count activated. */
    fun activatePendingForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        excludedShareIds: Set<UUID> = emptySet(),
    ): Int =
        shareRepository.findByResourceAndStatus(resourceType, resourceId, ShareStatus.PENDING_APPROVAL)
            .filterNot { it.id in excludedShareIds }
            .count { activate(it.id)?.status == ShareStatus.ACTIVE }

    /** Revoke every PENDING_APPROVAL share on a resource (e.g. when an approval is rejected). */
    fun revokePendingForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        revokedBy: PrincipalRef? = null,
        resourceLabel: String? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findByResourceAndStatus(resourceType, resourceId, ShareStatus.PENDING_APPROVAL)
            .forEach { markRevoked(it, revokedBy, now, resourceLabel) }
    }

    /**
     * Change the role on a share. If the share is a group's DIRECT grant, the new role is
     * propagated to its inherited member shares so the group stays internally consistent.
     * Returns the updated share, or null if it doesn't exist.
     */
    fun updateRole(shareId: UUID, roleName: ExchangeShareRoleName): Share?
    {
        return updateRoleAndConstraints(shareId, roleName, constraintsJson = null, applyConstraints = false)
    }

    fun updateRoleAndConstraints(
        shareId: UUID,
        roleName: ExchangeShareRoleName,
        constraintsJson: String?,
        applyConstraints: Boolean,
        resourceLabel: String? = null,
    ): Share?
    {
        val share = shareRepository.findById(shareId) ?: return null
        val previousRole = share.roleName
        share.roleName = roleName.name
        if (applyConstraints)
        {
            share.constraintsJson = constraintsJson
        }
        val updated = shareRepository.update(share)

        shareRepository.findBySourceShareId(shareId).forEach { child ->
            child.roleName = roleName.name
            if (applyConstraints)
            {
                child.constraintsJson = constraintsJson
            }
            shareRepository.update(child)
        }

        recordShareEvent(
            eventType = AuditEventType.SHARE_ROLE_CHANGE,
            share = updated,
            actor = null,
            extraPayload = mapOf(
                "previous_role" to previousRole,
                "new_role" to roleName.name,
                "constraints_changed" to applyConstraints.toString(),
            ),
            resourceLabel = resourceLabel,
        )

        return updated
    }

    /**
     * The "recipient" shares of a session: active USER shares that are neither the owner nor a
     * pure participant. Replaces the legacy single `Exchange.recipient`.
     */
    fun recipientShares(exchangeId: UUID): List<Share> =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.principalKind == PrincipalKind.USER &&
                        it.roleName != ExchangeShareRoleName.OWNER.name
            }

    /**
     * All non-owner, non-participant shares on a session, includes both USER and
     * PRINCIPAL_GROUP recipients. Used for constraint reads/writes so that group-recipient
     * sessions are handled correctly.
     */
    private fun allRecipientShares(exchangeId: UUID): List<Share> =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.roleName != ExchangeShareRoleName.OWNER.name
            }

    fun recipientUserIds(exchangeId: UUID): List<UUID> =
        recipientShares(exchangeId).map { it.principalId }.distinct()

    fun recipientUserIdsForDisplay(exchangeId: UUID): List<UUID> =
        shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.principalKind == PrincipalKind.USER &&
                        it.roleName != ExchangeShareRoleName.OWNER.name
            }
            .map { it.principalId }
            .distinct()

    /** Returns the user ID of the primary direct (non-inherited) recipient, or null when the
     *  exchange is shared with a group rather than a user directly.  Using DIRECT-only here
     *  prevents an initiator's own INHERITED_FROM_GROUP share (created when they are also a
     *  member of the recipient group) from being mistaken for the "primary recipient". */
    fun primaryRecipientUserId(exchangeId: UUID): UUID? =
        primaryDirectRecipientShare(exchangeId)
            ?.takeIf { it.principalKind == PrincipalKind.USER }
            ?.principalId

    fun primaryRecipientPrincipal(exchangeId: UUID): PrincipalRef? =
        primaryDirectRecipientShare(exchangeId)?.let { PrincipalRef(it.principalKind, it.principalId) }

    /** Returns the group ID of the primary PRINCIPAL_GROUP recipient share, or null if none. */
    fun primaryRecipientGroupId(exchangeId: UUID): UUID? =
        primaryDirectRecipientShare(exchangeId)
            ?.takeIf { it.principalKind == PrincipalKind.PRINCIPAL_GROUP }
            ?.principalId

    private fun primaryDirectRecipientShare(exchangeId: UUID): Share? =
        primaryDirectRecipientShareForDisplay(exchangeId)
            ?.takeIf { it.status == ShareStatus.ACTIVE }

    private fun primaryDirectRecipientShareForDisplay(exchangeId: UUID): Share? =
        exchangeRecipientServiceProvider.get().findPrimary(exchangeId)
            ?.let { recipient -> shareRepository.findById(recipient.directShareId) }

    /**
     * Display-only variant of [primaryRecipientUserId]: searches ALL share rows regardless of
     * status so that ended/archived exchanges (whose shares are revoked) still resolve a
     * recipient for display purposes. Must not be used for authorization checks.
     */
    fun primaryRecipientUserIdForDisplay(exchangeId: UUID): UUID? =
        primaryDirectRecipientShareForDisplay(exchangeId)
            ?.takeIf { it.principalKind == PrincipalKind.USER }
            ?.principalId

    /**
     * Display-only variant of [primaryRecipientGroupId]: searches ALL share rows regardless of
     * status. Must not be used for authorization checks.
     */
    fun primaryRecipientGroupIdForDisplay(exchangeId: UUID): UUID? =
        primaryDirectRecipientShareForDisplay(exchangeId)
            ?.takeIf { it.principalKind == PrincipalKind.PRINCIPAL_GROUP }
            ?.principalId

    /** Whether the session's recipient share permits the given constraint flag (e.g. "allow_document_upload"). */
    fun recipientConstraintAllows(exchangeId: UUID, flag: String): Boolean
    {
        val constraints = allRecipientShares(exchangeId).firstOrNull()?.constraintsJson ?: return false
        return constraints.contains("\"$flag\":true")
    }

    /** Returns the constraints JSON of the primary recipient share, or null if none. */
    fun recipientConstraintsJson(exchangeId: UUID): String? =
        allRecipientShares(exchangeId).firstOrNull()?.constraintsJson

    /**
     * Update the constraints JSON on all recipient shares (and their inherited children)
     * for the given session. Used when the initiator changes document permissions.
     */
    fun updateRecipientConstraints(exchangeId: UUID, constraintsJson: String)
    {
        val shares = allRecipientShares(exchangeId)
        if (shares.isEmpty())
        {
            logger.warn("updateRecipientConstraints: no recipient shares found for session={}", exchangeId)
            return
        }
        for (share in shares)
        {
            share.constraintsJson = constraintsJson
            shareRepository.update(share)
            // Propagate to inherited shares (e.g. from group grants)
            shareRepository.findBySourceShareId(share.id).forEach { child ->
                child.constraintsJson = constraintsJson
                shareRepository.update(child)
            }
        }
    }

    /** Revoke a single share and any inherited children that point at it. */
    fun revoke(
        shareId: UUID,
        revokedBy: PrincipalRef? = null,
        resourceLabel: String? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findById(shareId)?.let { share ->
            markRevoked(share, revokedBy, now, resourceLabel)
            shareRepository.findBySourceShareId(shareId).forEach { child ->
                markRevoked(child, revokedBy, now, resourceLabel)
            }
        }
    }

    fun retainInformationRequestOwnerRead(requestId: UUID, ownerUserId: UUID)
    {
        val existing = shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.USER, ownerUserId, ResourceType.INFORMATION_REQUEST, requestId)
        if (existing.isEmpty())
            grantRoleKeyWithPrincipalProvenance(ResourceType.INFORMATION_REQUEST, requestId,
                PrincipalKind.USER, ownerUserId, InformationRequestShareRoleKey.SUBJECT.name)
    }

    fun revokeAllForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        revokedBy: PrincipalRef? = null,
        resourceLabel: String? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findActiveByResource(resourceType, resourceId).forEach {
            markRevoked(it, revokedBy, now, resourceLabel)
        }
    }

    /**
     * The single choke point for every revoke variant (direct [revoke],
     * [revokePendingForResource], [revokeAllForResource]) so SHARE_REVOKE is captured exactly
     * once per share regardless of which caller triggered it. The `status == REVOKED` guard
     * above (unchanged) also prevents a duplicate event for an already-revoked share.
     */
    private fun markRevoked(share: Share, revoker: PrincipalRef?, now: Timestamp, resourceLabel: String? = null)
    {
        if (share.status == ShareStatus.REVOKED) return
        share.status = ShareStatus.REVOKED
        share.revokedAt = now
        recordRevoker(share, revoker)
        shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_REVOKE,
            share = share,
            actor = revoker,
            extraPayload = mapOf("role_name" to share.roleName, "principal_kind" to share.principalKind.name),
            resourceLabel = resourceLabel,
        )
    }

    /**
     * Captures Share grant, activation, role changes, and revocation through [AuditRecorder].
     * Target is the resource being shared (denormalized `resourceType.name`/
     * `resourceId`), not the Share row itself, so the event is discoverable alongside every other
     * event about that resource. When an acting principal is known, its canonical kind is mapped to
     * the audit actor kind. Grants and activations without an actor are recorded as SYSTEM.
     * Failures are caught and logged, never propagated, so audit plumbing can never break a
     * Share mutation.
     */
    private fun recordShareEvent(
        eventType: AuditEventType,
        share: Share,
        actor: PrincipalRef?,
        extraPayload: Map<String, String>,
        resourceLabel: String? = null,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = resolveOwnerScope(share),
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actor?.id,
                    actorKind = actor?.kind?.let(AuditActorKind::forPrincipal) ?: AuditActorKind.SYSTEM,
                    targetType = share.resourceType.name,
                    targetId = share.resourceId.toString(),
                    targetLabel = resourceLabel,
                    payload = buildMap {
                        put("share_id", share.id.toString())
                        putAll(extraPayload)
                    },
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ShareService: AuditRecorder rejected draft for eventType={}: {}", eventType.key, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("ShareService: AuditRecorder capture failed (fail-closed) for eventType={}: {}", eventType.key, e.message, e)
        }
    }

    private fun resolveOwnerScope(share: Share): AuditOwnerScope
    {
        return when (
            val owner = resourceAuthorizationContextRegistry.resolve(
                ResourceRef(share.resourceType, share.resourceId),
            )?.ownerContext
        )
        {
            is OwnerContext.Organization ->
                AuditOwnerScope.Organization(owner.organizationId)

            is OwnerContext.Personal ->
                AuditOwnerScope.Personal(owner.userId)
            else -> AuditOwnerScope.Platform
        }
    }

    private fun recordGrantor(share: Share, grantor: PrincipalRef?)
    {
        share.grantedByPrincipalKind = grantor?.kind
        share.grantedByPrincipalId = grantor?.id
    }

    private fun recordRevoker(share: Share, revoker: PrincipalRef?)
    {
        share.revokedByPrincipalKind = revoker?.kind
        share.revokedByPrincipalId = revoker?.id
    }
}
