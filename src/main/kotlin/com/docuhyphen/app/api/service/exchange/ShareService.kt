package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.ShareRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Write-side service for the unified [Share] model. Replaces the legacy three-way recipient
 * columns on `exchange` (recipient_id / recipient_type / group_id) and the per-session
 * `allow_document_*` flags.
 *
 * During the dual-write window the legacy columns stay authoritative for reads; this service
 * mirrors each recipient into a `share` row so [com.docuhyphen.app.api.service.auth.authz.AuthorizationService]
 * can resolve access from the new model once the read path flips.
 *
 * Group inheritance is *materialised*: sharing with a `PRINCIPAL_GROUP` creates the DIRECT
 * grant for the group plus `INHERITED_FROM_GROUP` rows for each active member (pointing back
 * via `sourceShareId`), keeping authorization a flat query rather than a recursive walk.
 */
@ApplicationScoped
class ShareService @Inject constructor(
    private val shareRepository: ShareRepository,
    private val groupMemberRepository: PrincipalGroupMemberRepository,
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
        roleName: RoleName,
        grantedByAppUserId: UUID? = null,
        source: ShareSource = ShareSource.DIRECT,
        constraintsJson: String? = null,
        expiresAt: Timestamp? = null,
        status: ShareStatus = ShareStatus.ACTIVE,
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
            this.roleName = roleName.name
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

        if (principalKind == PrincipalKind.PRINCIPAL_GROUP && status == ShareStatus.ACTIVE)
        {
            materialiseGroupInheritance(persisted)
        }
        return persisted
    }

    /**
     * Create `INHERITED_FROM_GROUP` shares for each active member of the group named by
     * [parentShare]. Re-runnable: members already holding the inherited share are left as-is.
     */
    private fun materialiseGroupInheritance(parentShare: Share)
    {
        val members = groupMemberRepository.findActiveMembers(parentShare.principalId)
        val alreadyInherited = shareRepository.findBySourceShareId(parentShare.id)
            .associateBy { it.principalKind to it.principalId }

        for (member in members)
        {
            if (member.principalKind != PrincipalKind.USER && member.principalKind != PrincipalKind.PARTICIPANT)
            {
                continue
            }
            val key = member.principalKind to member.principalId
            if (key in alreadyInherited) continue

            val inherited = Share().apply {
                this.resourceType = parentShare.resourceType
                this.resourceId = parentShare.resourceId
                this.principalKind = member.principalKind
                this.principalId = member.principalId
                this.roleName = parentShare.roleName
                this.source = ShareSource.INHERITED_FROM_GROUP
                this.sourceShareId = parentShare.id
                this.status = ShareStatus.ACTIVE
                this.grantedByAppUserId = parentShare.grantedByAppUserId
                this.constraintsJson = parentShare.constraintsJson
                this.expiresAt = parentShare.expiresAt
            }
            shareRepository.save(inherited)
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
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findByResourceAndStatus(resourceType, resourceId, ShareStatus.PENDING_APPROVAL)
            .forEach { markRevoked(it, revokedByAppUserId, now) }
    }

    /**
     * Change the role on a share. If the share is a group's DIRECT grant, the new role is
     * propagated to its inherited member shares so the group stays internally consistent.
     * Returns the updated share, or null if it doesn't exist.
     */
    fun updateRole(shareId: UUID, roleName: RoleName): Share?
    {
        return updateRoleAndConstraints(shareId, roleName, constraintsJson = null, applyConstraints = false)
    }

    fun updateRoleAndConstraints(
        shareId: UUID,
        roleName: RoleName,
        constraintsJson: String?,
        applyConstraints: Boolean,
    ): Share?
    {
        val share = shareRepository.findById(shareId) ?: return null
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
                    it.roleName != RoleName.OWNER.name
            }

    /**
     * All non-owner, non-participant shares on a session, includes both USER and
     * PRINCIPAL_GROUP recipients. Used for constraint reads/writes so that group-recipient
     * sessions are handled correctly.
     */
    private fun allRecipientShares(exchangeId: UUID): List<Share> =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.roleName != RoleName.OWNER.name
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
                    it.roleName != RoleName.OWNER.name &&
                    it.source == ShareSource.DIRECT
            }?.principalId

    /** Returns the group ID of the primary PRINCIPAL_GROUP recipient share, or null if none. */
    fun primaryRecipientGroupId(exchangeId: UUID): UUID? =
        shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)
            .filter {
                it.principalKind == PrincipalKind.PRINCIPAL_GROUP &&
                    it.roleName != RoleName.OWNER.name
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
                    it.roleName != RoleName.OWNER.name &&
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
                    it.roleName != RoleName.OWNER.name
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
    fun revoke(shareId: UUID, revokedByAppUserId: UUID? = null)
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findById(shareId)?.let { share ->
            markRevoked(share, revokedByAppUserId, now)
            shareRepository.findBySourceShareId(shareId).forEach { child ->
                markRevoked(child, revokedByAppUserId, now)
            }
        }
    }

    /** Revoke every active share on a resource (e.g. when a session is ended/deleted). */
    fun revokeAllForResource(
        resourceType: ResourceType,
        resourceId: UUID,
        revokedByAppUserId: UUID? = null,
    )
    {
        val now = Timestamp.from(Instant.now())
        shareRepository.findActiveByResource(resourceType, resourceId).forEach {
            markRevoked(it, revokedByAppUserId, now)
        }
    }

    private fun markRevoked(share: Share, revokedByAppUserId: UUID?, now: Timestamp)
    {
        if (share.status == ShareStatus.REVOKED) return
        share.status = ShareStatus.REVOKED
        share.revokedAt = now
        share.revokedByAppUserId = revokedByAppUserId
        shareRepository.update(share)
    }
}
