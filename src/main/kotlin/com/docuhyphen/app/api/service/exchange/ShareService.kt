package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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
    private val exchangeAuthorizationContextProvider: ExchangeAuthorizationContextProvider,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ShareService::class.java)
    }
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
        grantedByAppUserId: UUID? = null,
        source: ShareSource = ShareSource.DIRECT,
        constraintsJson: String? = null,
        expiresAt: Timestamp? = null,
        status: ShareStatus = ShareStatus.ACTIVE,
        resourceLabel: String? = null,
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
            this.roleName = roleName
            this.source = source
            this.sourceShareId = null
            this.status = status
            this.grantedByAppUserId = grantedByAppUserId
            this.constraintsJson = constraintsJson
            this.expiresAt = expiresAt
            if (existingDirect == null)
            {
                this.grantedAt = Timestamp.from(Instant.now())
            }
            this.revokedAt = null
            this.revokedByAppUserId = null
        }
        val persisted = if (existingDirect == null && previouslyDirect == null) shareRepository.save(share) else shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_GRANT,
            share = persisted,
            actorId = grantedByAppUserId,
            extraPayload = mapOf("role_name" to roleName.name, "principal_kind" to principalKind.name),
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
            .forEach { synchronizeInheritedShare(it, principalKind, principalId, active) }
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
            grantedByAppUserId = parentShare.grantedByAppUserId
            constraintsJson = parentShare.constraintsJson
            expiresAt = parentShare.expiresAt
            revokedAt = null
            revokedByAppUserId = null
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
        share.status = ShareStatus.ACTIVE
        val activated = shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_ACTIVATE,
            share = activated,
            actorId = null,
            extraPayload = mapOf("role_name" to activated.roleName.name, "principal_kind" to activated.principalKind.name),
        )

        if (activated.principalKind == PrincipalKind.PRINCIPAL_GROUP)
        {
            materialiseGroupInheritance(activated)
        }
        return activated
    }

    /** Promote every PENDING_APPROVAL share on a resource to ACTIVE; returns the count activated. */
    fun activatePendingForResource(resourceType: ResourceType, resourceId: UUID): Int =
        shareRepository.findByResourceAndStatus(resourceType, resourceId, ShareStatus.PENDING_APPROVAL)
            .count { activate(it.id)?.status == ShareStatus.ACTIVE }

    /** Revoke every PENDING_APPROVAL share on a resource (e.g. when an approval is rejected). */
    fun revokePendingForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        revokedByAppUserId: UUID? = null,
        resourceLabel: String? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findByResourceAndStatus(resourceType, resourceId, ShareStatus.PENDING_APPROVAL)
            .forEach { markRevoked(it, revokedByAppUserId, now, resourceLabel) }
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
        share.roleName = roleName
        if (applyConstraints)
        {
            share.constraintsJson = constraintsJson
        }
        val updated = shareRepository.update(share)

        shareRepository.findBySourceShareId(shareId).forEach { child ->
            child.roleName = roleName
            if (applyConstraints)
            {
                child.constraintsJson = constraintsJson
            }
            shareRepository.update(child)
        }

        recordShareEvent(
            eventType = AuditEventType.SHARE_ROLE_CHANGE,
            share = updated,
            actorId = null,
            extraPayload = mapOf(
                "previous_role" to previousRole.name,
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
                    it.roleName != ExchangeShareRoleName.OWNER
            }

    /**
     * All non-owner, non-participant shares on a session, includes both USER and
     * PRINCIPAL_GROUP recipients. Used for constraint reads/writes so that group-recipient
     * sessions are handled correctly.
     */
    private fun allRecipientShares(exchangeId: UUID): List<Share> =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.roleName != ExchangeShareRoleName.OWNER
            }

    fun recipientUserIds(exchangeId: UUID): List<UUID> =
        recipientShares(exchangeId).map { it.principalId }.distinct()

    /** Returns the user ID of the primary direct (non-inherited) recipient, or null when the
     *  exchange is shared with a group rather than a user directly.  Using DIRECT-only here
     *  prevents an initiator's own INHERITED_FROM_GROUP share (created when they are also a
     *  member of the recipient group) from being mistaken for the "primary recipient". */
    fun primaryRecipientUserId(exchangeId: UUID): UUID? =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .firstOrNull {
                it.principalKind == PrincipalKind.USER &&
                    it.roleName != ExchangeShareRoleName.OWNER &&
                    it.source == ShareSource.DIRECT
            }?.principalId

    /** Returns the group ID of the primary PRINCIPAL_GROUP recipient share, or null if none. */
    fun primaryRecipientGroupId(exchangeId: UUID): UUID? =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.principalKind == PrincipalKind.PRINCIPAL_GROUP &&
                    it.roleName != ExchangeShareRoleName.OWNER
            }
            .firstOrNull()?.principalId

    /**
     * Display-only variant of [primaryRecipientUserId]: searches ALL share rows regardless of
     * status so that ended/archived exchanges (whose shares are revoked) still resolve a
     * recipient for display purposes. Must not be used for authorization checks.
     */
    fun primaryRecipientUserIdForDisplay(exchangeId: UUID): UUID? =
        shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .firstOrNull {
                it.principalKind == PrincipalKind.USER &&
                    it.roleName != ExchangeShareRoleName.OWNER &&
                    it.source == ShareSource.DIRECT
            }?.principalId

    /**
     * Display-only variant of [primaryRecipientGroupId]: searches ALL share rows regardless of
     * status. Must not be used for authorization checks.
     */
    fun primaryRecipientGroupIdForDisplay(exchangeId: UUID): UUID? =
        shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .firstOrNull {
                it.principalKind == PrincipalKind.PRINCIPAL_GROUP &&
                    it.roleName != ExchangeShareRoleName.OWNER
            }?.principalId

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
    fun revoke(shareId: UUID, revokedByAppUserId: UUID? = null, resourceLabel: String? = null)
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findById(shareId)?.let { share ->
            markRevoked(share, revokedByAppUserId, now, resourceLabel)
            shareRepository.findBySourceShareId(shareId).forEach { child ->
                markRevoked(child, revokedByAppUserId, now, resourceLabel)
            }
        }
    }

    /** Revoke every active share on a resource (e.g. when a session is ended/deleted). */
    fun revokeAllForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        revokedByAppUserId: UUID? = null,
        resourceLabel: String? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findActiveByResource(resourceType, resourceId).forEach {
            markRevoked(it, revokedByAppUserId, now, resourceLabel)
        }
    }

    /**
     * The single choke point for every revoke variant (direct [revoke],
     * [revokePendingForResource], [revokeAllForResource]) so SHARE_REVOKE is captured exactly
     * once per share regardless of which caller triggered it. The `status == REVOKED` guard
     * above (unchanged) also prevents a duplicate event for an already-revoked share.
     */
    private fun markRevoked(share: Share, revokedByAppUserId: UUID?, now: Timestamp, resourceLabel: String? = null)
    {
        if (share.status == ShareStatus.REVOKED) return
        share.status = ShareStatus.REVOKED
        share.revokedAt = now
        share.revokedByAppUserId = revokedByAppUserId
        shareRepository.update(share)

        recordShareEvent(
            eventType = AuditEventType.SHARE_REVOKE,
            share = share,
            actorId = revokedByAppUserId,
            extraPayload = mapOf("role_name" to share.roleName.name, "principal_kind" to share.principalKind.name),
            resourceLabel = resourceLabel,
        )
    }

    /**
     * Captures Share
     * grant/activation/role-constraint-change/revocation through [AuditRecorder].
     * is superseded as the write path (its table/repository are left untouched, out of scope to
     * remove). Target is the resource being shared (denormalized `resourceType.name`/
     * `resourceId`), not the Share row itself, so the event is discoverable alongside every other
     * event about that resource. Actor kind is HUMAN when an acting app user id is known (a
     * human-initiated grant/role-change/revoke); grants/activations with no acting app user id
     * (e.g. system/workflow-driven activation after an approval step completes) are recorded as
     * SYSTEM, since ShareService has no per-request context of its own to distinguish a workflow
     * actor from another background process, so background operations are attributed to the system.
     * Failures are caught and logged, never propagated, so audit plumbing can never break a
     * Share mutation.
     */
    private fun recordShareEvent(
        eventType: AuditEventType,
        share: Share,
        actorId: UUID?,
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
                    actorId = actorId,
                    actorKind = if (actorId != null) AuditActorKind.HUMAN else AuditActorKind.SYSTEM,
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
        if (share.resourceType != ResourceType.EXCHANGE) return AuditOwnerScope.Platform

        return when (val owner = exchangeAuthorizationContextProvider.resolve(share.resourceId)?.ownerContext)
        {
            is com.docuhyphen.app.api.service.auth.authz.OwnerContext.Organization ->
                AuditOwnerScope.Organization(owner.organizationId)
            else -> AuditOwnerScope.Platform
        }
    }
}
