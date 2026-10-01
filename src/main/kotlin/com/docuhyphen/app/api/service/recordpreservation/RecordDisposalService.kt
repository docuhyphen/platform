package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.model.entity.RecordDisposalClaim
import com.docuhyphen.app.api.model.entity.RecordDisposalDeletionOutcome
import com.docuhyphen.app.api.model.entity.RecordDisposalObject
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.recordpreservation.OpenRecordDisposalClaimCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalClaimRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalObjectRepository
import com.docuhyphen.app.api.repository.recordpreservation.RecordDisposalTombstoneRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class RecordDisposalService @Inject constructor(
    private val claimRepository: RecordDisposalClaimRepository,
    private val objectRepository: RecordDisposalObjectRepository,
    private val tombstoneRepository: RecordDisposalTombstoneRepository,
    private val clock: Clock,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun open(command: OpenRecordDisposalClaimCommand): RecordDisposalView
    {
        if (claimRepository.findForResource(command.resourceType, command.resourceId) != null)
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS,
                "This record is already claimed for disposal"
            )
        }
        val claim = claimRepository.save(
            RecordDisposalClaim().apply {
                resourceType = command.resourceType
                resourceId = command.resourceId
                ownerKind = command.owner.kind
                ownerId = requireNotNull(command.owner.id)
                basis = command.basis
                retentionScheduleId = command.retentionScheduleId
                privacyRequestId = command.privacyRequestId
                state = RecordDisposalState.CLAIMED
                claimedByPrincipalKind = command.principal.kind
                claimedByPrincipalId = command.principal.id
                claimedAt = Timestamp.from(clock.instant())
            },
        )
        claimRepository.entityManager.flush()
        command.scopeKeys.distinct().forEach { claimRepository.insertScope(claim.id, it) }
        command.objects.distinctBy { it.documentVersionId }.forEach { candidate ->
            objectRepository.save(
                RecordDisposalObject().apply {
                    claimId = claim.id
                    documentId = candidate.documentId
                    documentVersionId = candidate.documentVersionId
                    storageProvider = candidate.storageProvider
                    storageLocatorKind = candidate.storageLocatorKind
                    storageLocator = candidate.storageLocator
                    retained = candidate.retainedReason != null
                    retainedReason = candidate.retainedReason
                },
            )
        }
        return view(claim)
    }

    fun view(claimId: UUID): RecordDisposalView =
        view(claimRepository.findById(claimId) ?: throw RecordPreservationNotFoundException("Disposal claim not found"))

    fun viewFor(resourceType: String, resourceId: UUID): RecordDisposalView? =
        claimRepository.findForResource(resourceType, resourceId)?.let(::view)

    fun viewsFor(owner: RecordOwnerRef): List<RecordDisposalView> = claimRepository.findForOwner(owner).map(::view)

    fun openClaimIds(limit: Int): List<UUID> = claimRepository.findOpenIds(limit)

    fun scopeOf(claimId: UUID): List<RecordPreservationKey> = claimRepository.scopeOf(claimId)

    @Transactional(Transactional.TxType.MANDATORY)
    fun recordObjectDeleted(objectId: UUID, outcome: RecordDisposalDeletionOutcome): RecordDisposalObject
    {
        val stored = objectRepository.findById(objectId)
            ?: throw RecordPreservationNotFoundException("Disposal object not found")
        if (stored.deletedAt != null || stored.retained) return stored
        stored.deletionOutcome = outcome
        stored.deletedAt = Timestamp.from(clock.instant())
        return objectRepository.update(stored)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun recordAttemptFailure(claimId: UUID, errorCode: String): RecordDisposalClaim
    {
        val claim = lockedOpen(claimId)
        claim.attemptCount += 1
        claim.lastErrorCode = errorCode.take(ERROR_CODE_LENGTH)
        claim.claimRevision += 1
        return claimRepository.update(claim)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun markObjectsDeleted(claimId: UUID): RecordDisposalClaim
    {
        val claim = lockedOpen(claimId)
        if (claim.state != RecordDisposalState.CLAIMED) return claim
        if (objectRepository.findForClaim(claimId).any { !it.retained && it.deletedAt == null })
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_STATE_INVALID,
                "Every claimed object is deleted before the record"
            )
        }
        claim.state = RecordDisposalState.OBJECTS_DELETED
        claim.objectsDeletedAt = Timestamp.from(clock.instant())
        claim.attemptCount += 1
        claim.lastErrorCode = null
        claim.claimRevision += 1
        return claimRepository.update(claim)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun finalize(claimId: UUID): RecordDisposalView
    {
        val claim = lockedOpen(claimId)
        if (claim.state != RecordDisposalState.OBJECTS_DELETED)
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_STATE_INVALID,
                "A record is finalized after its objects are deleted"
            )
        }
        when (claim.resourceType)
        {
            INFORMATION_REQUEST -> claimRepository.disposeInformationRequest(claimId)
            else -> throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_STATE_INVALID,
                "This record type has no disposal"
            )
        }
        claimRepository.entityManager.refresh(claim)
        return view(claim)
    }

    private fun lockedOpen(claimId: UUID): RecordDisposalClaim
    {
        val claim = claimRepository.findForUpdate(claimId)
            ?: throw RecordPreservationNotFoundException("Disposal claim not found")
        if (claim.state == RecordDisposalState.FINALIZED)
        {
            throw RecordPreservationException(
                RecordPreservationErrorCatalog.DISPOSAL_STATE_INVALID,
                "A finalized disposal does not change"
            )
        }
        return claim
    }

    private fun view(claim: RecordDisposalClaim) =
        RecordDisposalView(claim, objectRepository.findForClaim(claim.id), tombstoneRepository.findForClaim(claim.id))

    companion object
    {
        const val INFORMATION_REQUEST = "INFORMATION_REQUEST"
        private const val ERROR_CODE_LENGTH = 128
    }
}
