package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestSubjectRestriction
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.privacy.LiftInformationRequestSubjectRestrictionCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
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
    private val commandReceiptService: CommandReceiptService,
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val clock: Clock,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun restrict(
        owner: RecordOwnerRef,
        subjectIdentityRefId: UUID,
        privacyRequestId: UUID
    ): InformationRequestSubjectRestriction {
        val ownerId = requireNotNull(owner.id)
        restrictionRepository.lockSubject(owner.kind, ownerId, subjectIdentityRefId)
        return restrictionRepository.findActiveForUpdate(owner.kind, ownerId, subjectIdentityRefId)
            ?: restrictionRepository.save(
                InformationRequestSubjectRestriction().apply {
                    ownerKind = owner.kind
                    this.ownerId = ownerId
                    this.subjectIdentityRefId = subjectIdentityRefId
                    this.privacyRequestId = privacyRequestId
                    restrictedAt = Timestamp.from(clock.instant())
                },
            )
    }

    @Transactional
    fun lift(command: LiftInformationRequestSubjectRestrictionCommand): InformationRequestSubjectRestriction
    {
        val reasonCode = liftReasonOf(command.reasonCode)
        val idempotencyKey = command.idempotencyKey.trim()
        if (idempotencyKey.isEmpty()) throw InformationRequestCommandRequestException("Idempotency-Key is required")
        val owner = ownerAccess.currentOwner()
        val access = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val record = InformationRequestOwnerScopeAccess.recordOwnerOf(owner)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_SUBJECT_RESTRICTION, command.restrictionId),
            operation = LIFT_OPERATION,
            actor = CommandActorRef.principal(access.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(LIFT_OPERATION, command.restrictionId, reasonCode),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val lifted =
                    liftLocked(command.restrictionId, record, InformationRequestAuditActor.of(access), reasonCode)
                CommandMutationResult(
                    lifted,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_SUBJECT_RESTRICTION, lifted.id),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> owned(
                restrictionRepository.findById(decision.result.resourceId),
                record
            )
        }
    }

    fun restrictions(): List<InformationRequestSubjectRestriction>
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val record = InformationRequestOwnerScopeAccess.recordOwnerOf(owner)
        return restrictionRepository.findForOwner(record.kind, requireNotNull(record.id))
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun lockForProcessing(ownerType: InformationRequestOwnerType, ownerId: UUID, subjectIdentityRefId: UUID) {
        restrictionRepository.lockSubject(
            if (ownerType == InformationRequestOwnerType.ORGANIZATION) RecordOwnerKind.ORGANIZATION else RecordOwnerKind.USER,
            ownerId,
            subjectIdentityRefId,
        )
    }

    fun recordedFor(request: com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequest): InformationRequestSubjectRestriction? =
        request.restrictionId?.let(restrictionRepository::findById)
            ?: restrictionRepository.findForPrivacyRequest(request.id)

    fun isRestricted(ownerType: InformationRequestOwnerType, ownerId: UUID, subjectIdentityRefId: UUID): Boolean =
        restrictionRepository.findActive(
            if (ownerType == InformationRequestOwnerType.ORGANIZATION) RecordOwnerKind.ORGANIZATION else RecordOwnerKind.USER,
            ownerId,
            subjectIdentityRefId,
        ) != null

    private fun liftLocked(
        restrictionId: UUID,
        record: RecordOwnerRef,
        actor: InformationRequestAuditActor,
        reasonCode: String,
    ): InformationRequestSubjectRestriction {
        val restriction = owned(restrictionRepository.findByIdForUpdate(restrictionId), record)
        if (restriction.liftedAt != null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PRIVACY_REQUEST_STATE_INVALID,
                "This restriction is already lifted"
            )
        }
        restriction.liftedAt = Timestamp.from(clock.instant())
        restriction.liftedByPrincipalKind = actor.principal.kind
        restriction.liftedByPrincipalId = actor.principal.id
        restriction.liftReasonCode = reasonCode
        val lifted = restrictionRepository.update(restriction)
        audit(lifted, actor)
        return lifted
    }

    private fun owned(
        restriction: InformationRequestSubjectRestriction?,
        record: RecordOwnerRef,
    ): InformationRequestSubjectRestriction =
        restriction?.takeIf { it.ownerKind == record.kind && it.ownerId == record.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Subject restriction not found"
            )

    private fun audit(restriction: InformationRequestSubjectRestriction, actor: InformationRequestAuditActor) {
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = if (restriction.ownerKind == RecordOwnerKind.USER) AuditOwnerScope.Personal(restriction.ownerId)
                else AuditOwnerScope.Organization(restriction.ownerId),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_PRIVACY_RESTRICTION_LIFT.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.PRIVACY_REQUEST,
                targetId = restriction.privacyRequestId.toString(),
                payload = mapOf(
                    "restrictionId" to restriction.id.toString(),
                    "privacyRequestId" to restriction.privacyRequestId.toString(),
                    "subjectIdentityRefId" to restriction.subjectIdentityRefId.toString(),
                    "reasonCode" to restriction.liftReasonCode.orEmpty(),
                ),
                idempotencyKey = "${AuditEventType.INFORMATION_REQUEST_PRIVACY_RESTRICTION_LIFT.key}|${restriction.id}",
                businessTransactionId = restriction.id.toString(),
            ),
        )
    }

    private fun liftReasonOf(raw: String): String {
        val reasonCode = raw.trim()
        if (!LIFT_REASON.matches(reasonCode)) {
            throw InformationRequestCommandRequestException(
                "A lift reason is a lowercase machine code of at most $LIFT_REASON_LENGTH characters",
            )
        }
        return reasonCode
    }

    private companion object
    {
        const val LIFT_OPERATION = "information_request.subject_restriction.lift"
        const val LIFT_REASON_LENGTH = 128
        val LIFT_REASON = Regex("^[a-z0-9][a-z0-9._-]{0,${LIFT_REASON_LENGTH - 1}}$")
    }
}
