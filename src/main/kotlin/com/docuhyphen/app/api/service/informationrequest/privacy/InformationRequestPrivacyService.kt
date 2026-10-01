package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestItemCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalEligibility
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestPrivacyService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val privacyRepository: InformationRequestPrivacyRequestRepository,
    private val restrictionRepository: InformationRequestSubjectRestrictionRepository,
    private val correctionRepository: InformationRequestItemCorrectionRepository,
    private val requestRepository: InformationRequestRepository,
    private val exports: InformationRequestRecordExportService,
    private val restrictions: InformationRequestSubjectRestrictionService,
    private val corrections: InformationRequestItemCorrectionService,
    private val eligibility: InformationRequestDisposalEligibility,
    private val disposals: InformationRequestDisposalService,
    private val auditRecorder: AuditRecorder,
    private val clock: Clock,
)
{
    fun submit(command: RecordInformationRequestPrivacyRequestCommand): InformationRequestPrivacyRequestView
    {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        val principal = ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val recorded = QuarkusTransaction.requiringNew().call { record(owner, principal, command) }
        return when (command.requestKind)
        {
            InformationRequestPrivacyRequestKind.ACCESS, InformationRequestPrivacyRequestKind.EXPORT ->
                QuarkusTransaction.requiringNew().call {
                    val export = exports.createSubjectExport(
                        owner = owner,
                        subjectIdentityRefId = command.subjectIdentityRefId,
                        requestIds = privacyRepository.subjectRequestIds(
                            owner.kind,
                            requireNotNull(owner.id),
                            command.subjectIdentityRefId
                        ),
                        principal = principal,
                        transferRegion = command.transferRegion.takeIf { command.requestKind == InformationRequestPrivacyRequestKind.EXPORT },
                    )
                    export.sourceRequestIds.forEach {
                        privacyRepository.insertTarget(
                            recorded.id,
                            it,
                            InformationRequestPrivacyTargetOutcome.EXPORTED,
                            null,
                            null
                        )
                    }
                    complete(recorded.id, principal) { it.recordExportId = export.export.id }
                }

            InformationRequestPrivacyRequestKind.CORRECTION ->
                QuarkusTransaction.requiringNew().call {
                    val input = command.correction
                        ?: throw InformationRequestCommandRequestException("A correction names the item and its corrected content")
                    val correction =
                        corrections.correct(owner, command.subjectIdentityRefId, recorded.id, input, principal)
                    privacyRepository.insertTarget(
                        recorded.id,
                        correction.informationRequestId,
                        InformationRequestPrivacyTargetOutcome.CORRECTED,
                        null,
                        null
                    )
                    complete(recorded.id, principal)
                }

            InformationRequestPrivacyRequestKind.RESTRICTION ->
                QuarkusTransaction.requiringNew().call {
                    restrictions.restrict(owner, command.subjectIdentityRefId, recorded.id)
                    complete(recorded.id, principal)
                }

            InformationRequestPrivacyRequestKind.DELETION -> delete(owner, recorded, principal)
        }
    }

    fun privacyRequests(subjectIdentityRefId: UUID?): List<InformationRequestPrivacyRequestView>
    {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        return privacyRepository.findForOwner(owner.kind, requireNotNull(owner.id), subjectIdentityRefId).map(::view)
    }

    fun privacyRequest(id: UUID): InformationRequestPrivacyRequestView
    {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val request = privacyRepository.findById(id)?.takeIf { it.ownerKind == owner.kind && it.ownerId == owner.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Privacy request not found"
            )
        return view(request)
    }

    private fun record(
        owner: RecordOwnerRef,
        principal: PrincipalRef,
        command: RecordInformationRequestPrivacyRequestCommand
    ): InformationRequestPrivacyRequest
    {
        if (!privacyRepository.subjectOwnedBy(command.subjectIdentityRefId, owner.kind, requireNotNull(owner.id)))
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Subject not found")
        }
        val request = privacyRepository.save(
            InformationRequestPrivacyRequest().apply {
                ownerKind = owner.kind
                ownerId = requireNotNull(owner.id)
                subjectIdentityRefId = command.subjectIdentityRefId
                requestKind = command.requestKind
                purposeKey = keyOf(command.purposeKey, "purpose")
                policyBasisKey = keyOf(command.policyBasisKey, "policy basis")
                recordedByPrincipalKind = principal.kind
                recordedByPrincipalId = principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_RECORD, request, principal)
        return request
    }

    private fun delete(
        owner: RecordOwnerRef,
        recorded: InformationRequestPrivacyRequest,
        principal: PrincipalRef
    ): InformationRequestPrivacyRequestView
    {
        val requestIds = QuarkusTransaction.requiringNew().call {
            privacyRepository.subjectRequestIds(owner.kind, requireNotNull(owner.id), recorded.subjectIdentityRefId)
        }
        val refusals = QuarkusTransaction.requiringNew().call {
            requestIds.mapNotNull { requestId ->
                val request = requestRepository.findById(requestId) ?: return@mapNotNull null
                (eligibility.assess(
                    request,
                    RecordDisposalBasis.PRIVACY_DELETION,
                    clock.instant()
                ) as? InformationRequestDisposalAssessment.Refused)
                    ?.let { requestId to it }
            }
        }
        if (refusals.isNotEmpty())
        {
            return QuarkusTransaction.requiringNew().call {
                refusals.forEach { (requestId, refusal) ->
                    privacyRepository.insertTarget(
                        recorded.id,
                        requestId,
                        InformationRequestPrivacyTargetOutcome.REFUSED,
                        refusal.reasonCode,
                        null
                    )
                }
                refuse(recorded.id, principal, refusals.first().second.reasonCode, refusals.first().second.detail)
            }
        }
        val outcomes = requestIds.associateWith {
            disposals.claimAndProcess(
                it,
                RecordDisposalBasis.PRIVACY_DELETION,
                recorded.id,
                principal
            )
        }
        return QuarkusTransaction.requiringNew().call {
            outcomes.forEach { (requestId, outcome) ->
                when (outcome)
                {
                    is InformationRequestDisposalOutcome.Claimed -> privacyRepository.insertTarget(
                        recorded.id,
                        requestId,
                        InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED,
                        null,
                        outcome.view.claim.id,
                    )

                    is InformationRequestDisposalOutcome.Refused -> privacyRepository.insertTarget(
                        recorded.id,
                        requestId,
                        InformationRequestPrivacyTargetOutcome.REFUSED,
                        outcome.reasonCode,
                        null,
                    )
                }
            }
            val refused = outcomes.values.filterIsInstance<InformationRequestDisposalOutcome.Refused>().firstOrNull()
            if (refused != null) refuse(recorded.id, principal, refused.reasonCode, refused.detail)
            else complete(
                recorded.id,
                principal
            )
        }
    }

    private fun complete(
        privacyRequestId: UUID,
        principal: PrincipalRef,
        adjust: (InformationRequestPrivacyRequest) -> Unit = {},
    ): InformationRequestPrivacyRequestView
    {
        val request = requireNotNull(privacyRepository.findForUpdate(privacyRequestId))
        adjust(request)
        request.state = InformationRequestPrivacyRequestState.COMPLETED
        request.completedAt = Timestamp.from(clock.instant())
        request.requestRevision += 1
        privacyRepository.update(request)
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_COMPLETE, request, principal)
        return view(request)
    }

    private fun refuse(
        privacyRequestId: UUID,
        principal: PrincipalRef,
        reasonCode: String,
        detail: String
    ): InformationRequestPrivacyRequestView
    {
        val request = requireNotNull(privacyRepository.findForUpdate(privacyRequestId))
        request.state = InformationRequestPrivacyRequestState.REFUSED
        request.refusalCode = reasonCode
        request.refusalDetail = detail.take(DETAIL_LENGTH)
        request.completedAt = Timestamp.from(clock.instant())
        request.requestRevision += 1
        privacyRepository.update(request)
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_REFUSE, request, principal, AuditOutcome.DENIED)
        return view(request)
    }

    private fun view(request: InformationRequestPrivacyRequest): InformationRequestPrivacyRequestView
    {
        val targets = privacyRepository.targetsOf(request.id)
        return InformationRequestPrivacyRequestView(
            request = request,
            targets = targets,
            restriction = restrictionRepository.findForOwner(request.ownerKind, request.ownerId)
                .firstOrNull { it.privacyRequestId == request.id },
            correction = targets.firstOrNull { it.outcome == InformationRequestPrivacyTargetOutcome.CORRECTED }
                ?.let { target ->
                    correctionRepository.findForRequest(target.requestId)
                        .firstOrNull { it.privacyRequestId == request.id }
                },
        )
    }

    private fun audit(
        eventType: AuditEventType,
        request: InformationRequestPrivacyRequest,
        principal: PrincipalRef,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = if (request.ownerKind == RecordOwnerKind.USER) AuditOwnerScope.Personal(request.ownerId)
                else AuditOwnerScope.Organization(request.ownerId),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                targetType = "INFORMATION_REQUEST_PRIVACY_REQUEST",
                targetId = request.id.toString(),
                payload = buildMap {
                    put("privacyRequestId", request.id.toString())
                    put("requestKind", request.requestKind.name)
                    put("subjectIdentityRefId", request.subjectIdentityRefId.toString())
                    put("purposeKey", request.purposeKey)
                    put("policyBasisKey", request.policyBasisKey)
                    put("state", request.state.name)
                    request.refusalCode?.let { put("reasonCode", it) }
                },
                idempotencyKey = "${eventType.key}|${request.id}|${request.requestRevision}",
            ),
        )
    }

    private fun keyOf(raw: String, name: String): String
    {
        val key = raw.trim().lowercase()
        if (!KEY.matches(key)) throw InformationRequestCommandRequestException("A $name is a lowercase machine key")
        return key
    }

    private companion object
    {
        const val DETAIL_LENGTH = 512
        val KEY = Regex("^[a-z][a-z0-9_.-]{0,127}$")
    }
}
