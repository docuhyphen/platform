package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestSubjectRestriction
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestSubjectRestrictionService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val restrictionRepository: InformationRequestSubjectRestrictionRepository,
    private val clock: Clock,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun restrict(
        owner: RecordOwnerRef,
        subjectIdentityRefId: UUID,
        privacyRequestId: UUID
    ): InformationRequestSubjectRestriction =
        restrictionRepository.findActive(owner.kind, requireNotNull(owner.id), subjectIdentityRefId)
            ?: restrictionRepository.save(
                InformationRequestSubjectRestriction().apply {
                    ownerKind = owner.kind
                    ownerId = requireNotNull(owner.id)
                    this.subjectIdentityRefId = subjectIdentityRefId
                    this.privacyRequestId = privacyRequestId
                    restrictedAt = Timestamp.from(clock.instant())
                },
            )

    @Transactional
    fun lift(restrictionId: UUID, reasonCode: String): InformationRequestSubjectRestriction
    {
        val owner = ownerAccess.currentOwner()
        val principal = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val record = InformationRequestOwnerScopeAccess.recordOwnerOf(owner)
        val restriction = restrictionRepository.findById(restrictionId)
            ?.takeIf { it.ownerKind == record.kind && it.ownerId == record.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Subject restriction not found"
            )
        if (restriction.liftedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PRIVACY_REQUEST_STATE_INVALID,
                "This restriction is already lifted"
            )
        }
        restriction.liftedAt = Timestamp.from(clock.instant())
        restriction.liftedByPrincipalKind = principal.kind
        restriction.liftedByPrincipalId = principal.id
        restriction.liftReasonCode = reasonCode.trim().ifBlank { LIFTED }
        return restrictionRepository.update(restriction)
    }

    fun restrictions(): List<InformationRequestSubjectRestriction>
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val record = InformationRequestOwnerScopeAccess.recordOwnerOf(owner)
        return restrictionRepository.findForOwner(record.kind, requireNotNull(record.id))
    }

    fun isRestricted(ownerType: InformationRequestOwnerType, ownerId: UUID, subjectIdentityRefId: UUID): Boolean =
        restrictionRepository.findActive(
            if (ownerType == InformationRequestOwnerType.ORGANIZATION) RecordOwnerKind.ORGANIZATION else RecordOwnerKind.USER,
            ownerId,
            subjectIdentityRefId,
        ) != null

    private companion object
    {
        const val LIFTED = "LIFTED"
    }
}
