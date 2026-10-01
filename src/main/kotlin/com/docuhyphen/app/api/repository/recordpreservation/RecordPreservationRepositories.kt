package com.docuhyphen.app.api.repository.recordpreservation

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.*

@ApplicationScoped
class RecordPreservationHoldRepository : BaseRepository<RecordPreservationHold>(RecordPreservationHold::class.java)
{
    fun findForOwner(owner: RecordOwnerRef, statuses: Set<RecordPreservationHoldStatus>): List<RecordPreservationHold>
    {
        val ownerClause = if (owner.id == null) "hold.ownerId IS NULL" else "hold.ownerId = :ownerId"
        val query = entityManager.createQuery(
            """
            SELECT hold
            FROM RecordPreservationHold hold
            WHERE hold.ownerKind = :ownerKind
              AND $ownerClause
              AND hold.status IN :statuses
            ORDER BY hold.placedAt DESC, hold.id
            """.trimIndent(),
            RecordPreservationHold::class.java,
        )
            .setParameter("ownerKind", owner.kind)
            .setParameter("statuses", statuses)
        owner.id?.let { query.setParameter("ownerId", it) }
        return query.resultList
    }

    fun findForUpdate(holdId: UUID): RecordPreservationHold? =
        entityManager.find(RecordPreservationHold::class.java, holdId, LockModeType.PESSIMISTIC_WRITE)

    fun findActiveCovering(owner: RecordOwnerRef, keys: Collection<RecordPreservationKey>): List<RecordPreservationHold>
    {
        if (keys.isEmpty()) return emptyList()
        val candidates = entityManager.createQuery(
            """
            SELECT hold
            FROM RecordPreservationHold hold
            WHERE hold.status = :active
              AND hold.resourceType IN :types
              AND hold.resourceId IN :ids
              AND (hold.ownerKind = :platform OR (hold.ownerKind = :ownerKind AND hold.ownerId = :ownerId))
            """.trimIndent(),
            RecordPreservationHold::class.java,
        )
            .setParameter("active", RecordPreservationHoldStatus.ACTIVE)
            .setParameter("types", keys.map { it.resourceType }.toSet())
            .setParameter("ids", keys.map { it.resourceId }.toSet())
            .setParameter("platform", RecordOwnerKind.PLATFORM)
            .setParameter("ownerKind", owner.kind)
            .setParameter("ownerId", owner.id)
            .resultList
        return candidates.filter { hold ->
            keys.any { key ->
                key.resourceType == hold.resourceType && key.resourceId == hold.resourceId &&
                        (key.direct || hold.scope == RecordPreservationScope.DESCENDANTS_AND_REFERENCES)
            }
        }
    }

    fun findActiveForResource(
        owner: RecordOwnerRef,
        resourceType: String,
        resourceId: String
    ): List<RecordPreservationHold> =
        findActiveCovering(owner, listOf(RecordPreservationKey(resourceType, resourceId, direct = true)))
}

@ApplicationScoped
class RecordPreservationHoldEventRepository :
    BaseRepository<RecordPreservationHoldEvent>(RecordPreservationHoldEvent::class.java)
{
    fun findForHold(holdId: UUID): List<RecordPreservationHoldEvent> =
        entityManager.createQuery(
            "SELECT event FROM RecordPreservationHoldEvent event WHERE event.holdId = :holdId ORDER BY event.eventNumber",
            RecordPreservationHoldEvent::class.java,
        )
            .setParameter("holdId", holdId)
            .resultList

    fun nextEventNumber(holdId: UUID): Int = (findForHold(holdId).maxOfOrNull { it.eventNumber } ?: 0) + 1
}

@ApplicationScoped
class RecordRetentionScheduleRepository : BaseRepository<RecordRetentionSchedule>(RecordRetentionSchedule::class.java)
{
    fun findVersions(owner: RecordOwnerRef, resourceType: String): List<RecordRetentionSchedule> =
        entityManager.createQuery(
            """
            SELECT schedule
            FROM RecordRetentionSchedule schedule
            WHERE schedule.ownerKind = :ownerKind
              AND schedule.ownerId = :ownerId
              AND schedule.resourceType = :resourceType
            ORDER BY schedule.versionNumber DESC
            """.trimIndent(),
            RecordRetentionSchedule::class.java,
        )
            .setParameter("ownerKind", owner.kind)
            .setParameter("ownerId", owner.id)
            .setParameter("resourceType", resourceType)
            .resultList

    fun findCurrent(owner: RecordOwnerRef, resourceType: String): RecordRetentionSchedule? =
        findVersions(owner, resourceType).firstOrNull()

    fun findAllCurrent(resourceType: String): List<RecordRetentionSchedule> =
        entityManager.createQuery(
            """
            SELECT schedule
            FROM RecordRetentionSchedule schedule
            WHERE schedule.resourceType = :resourceType
              AND schedule.versionNumber = (
                  SELECT MAX(later.versionNumber)
                  FROM RecordRetentionSchedule later
                  WHERE later.ownerKind = schedule.ownerKind
                    AND later.ownerId = schedule.ownerId
                    AND later.resourceType = schedule.resourceType
              )
            """.trimIndent(),
            RecordRetentionSchedule::class.java,
        )
            .setParameter("resourceType", resourceType)
            .resultList
}

@ApplicationScoped
class RecordDisposalClaimRepository : BaseRepository<RecordDisposalClaim>(RecordDisposalClaim::class.java)
{
    fun findForResource(resourceType: String, resourceId: UUID): RecordDisposalClaim? =
        entityManager.createQuery(
            "SELECT claim FROM RecordDisposalClaim claim WHERE claim.resourceType = :resourceType AND claim.resourceId = :resourceId",
            RecordDisposalClaim::class.java,
        )
            .setParameter("resourceType", resourceType)
            .setParameter("resourceId", resourceId)
            .resultList
            .firstOrNull()

    fun findForUpdate(claimId: UUID): RecordDisposalClaim? =
        entityManager.find(RecordDisposalClaim::class.java, claimId, LockModeType.PESSIMISTIC_WRITE)

    fun findOpenIds(limit: Int): List<UUID> =
        entityManager.createQuery(
            "SELECT claim.id FROM RecordDisposalClaim claim WHERE claim.state <> :finalized ORDER BY claim.claimedAt, claim.id",
            UUID::class.java,
        )
            .setParameter("finalized", RecordDisposalState.FINALIZED)
            .setMaxResults(limit)
            .resultList

    fun findForOwner(owner: RecordOwnerRef): List<RecordDisposalClaim> =
        entityManager.createQuery(
            "SELECT claim FROM RecordDisposalClaim claim WHERE claim.ownerKind = :ownerKind AND claim.ownerId = :ownerId ORDER BY claim.claimedAt DESC",
            RecordDisposalClaim::class.java,
        )
            .setParameter("ownerKind", owner.kind)
            .setParameter("ownerId", owner.id)
            .resultList

    fun hasOpenClaimCovering(hold: RecordPreservationHold): Boolean =
        entityManager.createNativeQuery(
            """
            SELECT COUNT(*)
            FROM record_disposal_claim_scope scope_key
                     JOIN record_disposal_claim claim ON claim.id = scope_key.claim_id
            WHERE claim.state <> 'FINALIZED'
              AND scope_key.resource_type = :resourceType
              AND scope_key.resource_id = :resourceId
              AND (scope_key.direct OR :descendants)
              AND (:platform OR (claim.owner_kind = :ownerKind AND claim.owner_id = :ownerId))
            """.trimIndent(),
        )
            .setParameter("resourceType", hold.resourceType)
            .setParameter("resourceId", hold.resourceId)
            .setParameter("descendants", hold.scope == RecordPreservationScope.DESCENDANTS_AND_REFERENCES)
            .setParameter("platform", hold.ownerKind == RecordOwnerKind.PLATFORM)
            .setParameter("ownerKind", hold.ownerKind.name)
            .setParameter("ownerId", hold.ownerId ?: UUID(0, 0))
            .singleResult
            .let { (it as Number).toLong() > 0 }

    fun insertScope(claimId: UUID, key: RecordPreservationKey)
    {
        entityManager.createNativeQuery(
            "INSERT INTO record_disposal_claim_scope (claim_id, resource_type, resource_id, direct) VALUES (:claim, :type, :id, :direct)",
        )
            .setParameter("claim", claimId)
            .setParameter("type", key.resourceType)
            .setParameter("id", key.resourceId)
            .setParameter("direct", key.direct)
            .executeUpdate()
    }

    fun scopeOf(claimId: UUID): List<RecordPreservationKey> =
        entityManager.createNativeQuery(
            "SELECT resource_type, resource_id, direct FROM record_disposal_claim_scope WHERE claim_id = :claim ORDER BY resource_type, resource_id",
        )
            .setParameter("claim", claimId)
            .resultList
            .map { row ->
                val columns = row as Array<*>
                RecordPreservationKey(columns[0] as String, columns[1] as String, columns[2] as Boolean)
            }

    fun disposeInformationRequest(claimId: UUID): String =
        entityManager.createNativeQuery("SELECT record_dispose_information_request(:claim)")
            .setParameter("claim", claimId)
            .singleResult as String
}

@ApplicationScoped
class RecordDisposalObjectRepository : BaseRepository<RecordDisposalObject>(RecordDisposalObject::class.java)
{
    fun findForClaim(claimId: UUID): List<RecordDisposalObject> =
        entityManager.createQuery(
            "SELECT object FROM RecordDisposalObject object WHERE object.claimId = :claimId ORDER BY object.documentVersionId",
            RecordDisposalObject::class.java,
        )
            .setParameter("claimId", claimId)
            .resultList
}

@ApplicationScoped
class RecordDisposalTombstoneRepository : BaseRepository<RecordDisposalTombstone>(RecordDisposalTombstone::class.java)
{
    fun findForClaim(claimId: UUID): RecordDisposalTombstone? =
        entityManager.createQuery(
            "SELECT tombstone FROM RecordDisposalTombstone tombstone WHERE tombstone.claimId = :claimId",
            RecordDisposalTombstone::class.java,
        )
            .setParameter("claimId", claimId)
            .resultList
            .firstOrNull()

    fun findForResource(resourceType: String, resourceId: UUID): RecordDisposalTombstone? =
        entityManager.createQuery(
            "SELECT tombstone FROM RecordDisposalTombstone tombstone WHERE tombstone.resourceType = :resourceType AND tombstone.resourceId = :resourceId",
            RecordDisposalTombstone::class.java,
        )
            .setParameter("resourceType", resourceType)
            .setParameter("resourceId", resourceId)
            .resultList
            .firstOrNull()
}
