package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.externalsource.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDecisionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestImportedValueDecisionService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val records: InformationRequestImportedValueRecords,
    private val decisionRepository: InformationRequestImportedValueDecisionRepository,
    private val clock: Clock,
) {
    @Transactional
    fun decide(command: DecideInformationRequestImportedValueCommand): InformationRequestImportedValueView {
        val reasonCode = command.reasonCode.trim()
        val fingerprint =
            CommandRequestFingerprint.of(DECIDE_OPERATION, command.importedValueId, command.decision, reasonCode)
        return records.runOnce(
            command.requestId,
            DECIDE_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint,
            Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES
        ) {
            val locked = gate.lock(command.requestId)
            gate.authorizeRequest(
                command.access,
                listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES),
                command.requestId
            )
            gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
            gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
            records.requireReason(reasonCode, "A decision on an imported value states its reason")
            val value = records.valueOf(command.requestId, command.importedValueId)
            records.requireRecentVerification(command.access, command.requestId, value.informationRequestRequirementId)
            if (decisionRepository.findForRequest(command.requestId).any { it.importedValueId == value.id }) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.IMPORTED_VALUE_ALREADY_DECIDED,
                    "This imported value has already been decided",
                )
            }
            if (value.sourceKind == InformationRequestImportedValueSource.MANUAL &&
                value.recordedByPrincipalKind == command.access.principal.kind &&
                value.recordedByPrincipalId == command.access.principal.id
            ) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES,
                    "A value is decided by someone other than the person who recorded it",
                )
            }
            val now = clock.instant()
            if (command.decision == InformationRequestImportedValueDecisionKind.ACCEPTED && value.isExpiredAt(now)) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.IMPORTED_VALUE_EXPIRED,
                    "An expired value cannot be accepted"
                )
            }
            val decision = decisionRepository.save(
                InformationRequestImportedValueDecision().apply {
                    importedValueId = value.id
                    informationRequestId = command.requestId
                    this.decision = command.decision
                    this.reasonCode = reasonCode
                    decidedByPrincipalKind = command.access.principal.kind
                    decidedByPrincipalId = command.access.principal.id
                    decidedAt = Timestamp.from(now)
                },
            )
            records.recordHistory(
                locked.request,
                InformationRequestAuditActor.of(command.access),
                InformationRequestMutation.DECIDE_EXTERNAL_VALUE,
                "decision|${decision.id}",
                mapOf("importedValueId" to value.id.toString(), "importedValueDecision" to command.decision.name),
            )
            value.id
        }
    }

    private companion object {
        const val DECIDE_OPERATION = "decide-information-request-imported-value"
    }
}
