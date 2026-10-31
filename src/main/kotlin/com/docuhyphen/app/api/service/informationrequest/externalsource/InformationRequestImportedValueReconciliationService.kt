package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.*
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestImportedValueReconciliationService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val records: InformationRequestImportedValueRecords,
    private val valueRepository: InformationRequestImportedValueRepository,
    private val decisionRepository: InformationRequestImportedValueDecisionRepository,
    private val discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository,
    private val resolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository,
    private val reconciliationRunRepository: InformationRequestImportedValueReconciliationRunRepository,
    private val reconciliationRunItemRepository: InformationRequestImportedValueReconciliationRunItemRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val canonicalizer: InformationRequestImportedValueCanonicalizer,
    private val responseStore: InformationRequestResponseStore,
    private val commandReceiptService: CommandReceiptService,
    private val clock: Clock,
) {
    @Transactional
    fun reconcile(command: ReconcileInformationRequestImportedValuesCommand): List<InformationRequestImportedValueReconciliation> {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = RECONCILE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(RECONCILE_OPERATION, command.requestId),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val run = reconcileOnce(locked, command)
                CommandMutationResult(
                    run.second,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, run.first, 1, null),
                )
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> {
                gate.authorizeRequest(
                    command.access,
                    listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES),
                    command.requestId
                )
                val readable = records.contentReadable(command.access)
                val verified = records.verifiedRequirements(command.requestId, command.access)
                reconciliationRunItemRepository.findForRun(decision.result.resourceId)
                    .filter {
                        verified(it.informationRequestRequirementId) &&
                                (readable(it.informationRequestRequirementId) || it.outcome in ANSWER_INDEPENDENT_OUTCOMES)
                    }
                    .map { item ->
                        InformationRequestImportedValueReconciliation(
                            item.importedValueId,
                            item.informationRequestRequirementId,
                            item.outcome,
                            item.responseCanonicalValue,
                            item.discrepancyId,
                        )
                    }
            }
        }
    }

    @Transactional
    fun resolve(command: ResolveInformationRequestDiscrepancyCommand): InformationRequestDiscrepancyView {
        gate.requireVisibleTarget(command.access, command.requestId)
        val reasonCode = command.reasonCode.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = RESOLVE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                RESOLVE_OPERATION,
                command.discrepancyId,
                command.resolution,
                reasonCode,
            ),
        )
        val discrepancyId = when (
            val decision = commandReceiptService.runOnce(receipt) {
                val id = resolveOnce(command, reasonCode)
                CommandMutationResult(
                    id,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, id, 1, null)
                )
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> {
                gate.authorizeRequest(
                    command.access,
                    listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES),
                    command.requestId
                )
                requireDiscrepancyContent(
                    command,
                    requireNotNull(discrepancyRepository.findById(decision.result.resourceId))
                )
                decision.result.resourceId
            }
        }
        val discrepancy = requireNotNull(discrepancyRepository.findById(discrepancyId))
        return InformationRequestDiscrepancyView(
            discrepancy,
            resolutionRepository.findForRequest(command.requestId).firstOrNull { it.discrepancyId == discrepancyId })
    }

    private fun reconcileOnce(
        locked: LockedInformationRequest,
        command: ReconcileInformationRequestImportedValuesCommand,
    ): Pair<UUID, List<InformationRequestImportedValueReconciliation>> {
        gate.authorizeRequest(
            command.access,
            listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES),
            command.requestId
        )
        gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
        gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
        val rejected = decisionRepository.findForRequest(command.requestId)
            .filter { it.decision == InformationRequestImportedValueDecisionKind.REJECTED }
            .map { it.importedValueId }
            .toSet()
        val recorded = discrepancyRepository.findForRequest(command.requestId)
        val responses =
            responseStore.findCurrentForRequest(command.requestId).associateBy { it.informationRequestRequirementId }
        val inactive = requirementRepository.inactiveRequirementIds(command.requestId)
        val requirements =
            requirementRepository.findForRequest(command.requestId).filter { it.id !in inactive }.associateBy { it.id }
        val now = clock.instant()
        val created = mutableListOf<InformationRequestImportedValueDiscrepancy>()
        val readable = records.contentReadable(command.access)
        val verified = records.verifiedRequirements(command.requestId, command.access)
        val compared = valueRepository.findForRequest(command.requestId)
            .filter { it.id !in rejected && verified(it.informationRequestRequirementId) }
            .mapNotNull { value -> requirements[value.informationRequestRequirementId]?.let { value to it } }
            .filter { (value, requirement) -> readable(requirement.id) || answerIndependent(value, requirement, now) }
        val results = compared.map { (value, requirement) ->
            val response = responses[requirement.id]
            val comparison = if (value.isExpiredAt(now)) null
            else canonicalizer.compare(requirement, value, response)
            when (comparison) {
                null -> reconciliation(value, InformationRequestReconciliationOutcome.EXPIRED)
                InformationRequestValueComparison.NotComparable -> reconciliation(
                    value,
                    InformationRequestReconciliationOutcome.NOT_COMPARABLE
                )

                InformationRequestValueComparison.FieldChanged -> reconciliation(
                    value,
                    InformationRequestReconciliationOutcome.FIELD_CHANGED
                )

                InformationRequestValueComparison.NoAnswer -> reconciliation(
                    value,
                    InformationRequestReconciliationOutcome.NO_ANSWER
                )

                InformationRequestValueComparison.Matches -> reconciliation(
                    value,
                    InformationRequestReconciliationOutcome.MATCHES
                )

                is InformationRequestValueComparison.Differs -> {
                    val answered = requireNotNull(response)
                    val discrepancy =
                        recorded.firstOrNull { it.importedValueId == value.id && it.responseRevision == answered.responseRevision }
                            ?: discrepancyRepository.save(
                                InformationRequestImportedValueDiscrepancy().apply {
                                    importedValueId = value.id
                                    informationRequestId = command.requestId
                                    responseId = answered.id
                                    responseRevision = answered.responseRevision
                                    importedCanonicalValue = value.canonicalValue
                                    responseCanonicalValue = comparison.responseCanonicalValue
                                    recordedByPrincipalKind = command.access.principal.kind
                                    recordedByPrincipalId = command.access.principal.id
                                    recordedAt = Timestamp.from(now)
                                },
                            ).also(created::add)
                    InformationRequestImportedValueReconciliation(
                        value.id,
                        value.informationRequestRequirementId,
                        InformationRequestReconciliationOutcome.DIFFERS,
                        comparison.responseCanonicalValue,
                        discrepancy.id,
                    )
                }
            }
        }
        val run = reconciliationRunRepository.save(
            InformationRequestImportedValueReconciliationRun().apply {
                informationRequestId = command.requestId
                recordedByPrincipalKind = command.access.principal.kind
                recordedByPrincipalId = command.access.principal.id
                recordedAt = Timestamp.from(now)
            },
        )
        results.forEachIndexed { index, result ->
            reconciliationRunItemRepository.save(
                InformationRequestImportedValueReconciliationRunItem().apply {
                    reconciliationId = run.id
                    informationRequestId = command.requestId
                    itemNumber = index + 1
                    importedValueId = result.importedValueId
                    informationRequestRequirementId = result.requirementId
                    outcome = result.outcome
                    responseCanonicalValue = result.responseCanonicalValue
                    discrepancyId = result.discrepancyId
                },
            )
        }
        records.recordHistory(
            locked.request,
            InformationRequestAuditActor.of(command.access),
            InformationRequestMutation.RECONCILE_IMPORTED_VALUES,
            "reconciliation|${run.id}",
            mapOf(
                "reconciliationId" to run.id.toString(),
                "comparisonCount" to results.size.toString(),
                "discrepancyCount" to created.size.toString(),
            ),
        )
        return run.id to results
    }

    private fun answerIndependent(
        value: InformationRequestImportedValue,
        requirement: InformationRequestRequirement,
        now: Instant
    ) =
        value.isExpiredAt(now) || !canonicalizer.comparesWithAnswer(requirement, value)

    private fun resolveOnce(command: ResolveInformationRequestDiscrepancyCommand, reasonCode: String): UUID {
        val locked = gate.lock(command.requestId)
        gate.authorizeRequest(
            command.access,
            listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES),
            command.requestId
        )
        gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
        gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
        records.requireReason(reasonCode, "A resolution states its reason")
        val discrepancy = discrepancyRepository.findById(command.discrepancyId)
            ?.takeIf { it.informationRequestId == command.requestId }
            ?: throw io.quarkus.security.ForbiddenException("Access denied to this Information Request Requirement")
        requireDiscrepancyContent(command, discrepancy)
        if (resolutionRepository.findForRequest(command.requestId).any { it.discrepancyId == discrepancy.id }) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.DISCREPANCY_ALREADY_RESOLVED,
                "This discrepancy is already resolved"
            )
        }
        val resolution = resolutionRepository.save(
            InformationRequestImportedValueDiscrepancyResolution().apply {
                this.discrepancyId = discrepancy.id
                informationRequestId = command.requestId
                this.resolution = command.resolution
                this.reasonCode = reasonCode
                resolvedByPrincipalKind = command.access.principal.kind
                resolvedByPrincipalId = command.access.principal.id
                resolvedAt = Timestamp.from(clock.instant())
            },
        )
        records.recordHistory(
            locked.request,
            InformationRequestAuditActor.of(command.access),
            InformationRequestMutation.DECIDE_EXTERNAL_VALUE,
            "resolution|${resolution.id}",
            mapOf("discrepancyId" to discrepancy.id.toString(), "discrepancyResolution" to command.resolution.name),
        )
        return discrepancy.id
    }

    private fun requireDiscrepancyContent(
        command: ResolveInformationRequestDiscrepancyCommand,
        discrepancy: InformationRequestImportedValueDiscrepancy,
    ) {
        records.requireContent(
            command.access,
            command.requestId,
            records.valueOf(command.requestId, discrepancy.importedValueId).informationRequestRequirementId,
        )
    }

    private fun reconciliation(
        value: InformationRequestImportedValue,
        outcome: InformationRequestReconciliationOutcome
    ) =
        InformationRequestImportedValueReconciliation(
            value.id,
            value.informationRequestRequirementId,
            outcome,
            null,
            null
        )

    private companion object {
        const val RESOLVE_OPERATION = "resolve-information-request-imported-value-discrepancy"
        const val RECONCILE_OPERATION = "reconcile-information-request-imported-values"
        val ANSWER_INDEPENDENT_OUTCOMES = setOf(
            InformationRequestReconciliationOutcome.EXPIRED,
            InformationRequestReconciliationOutcome.NOT_COMPARABLE,
            InformationRequestReconciliationOutcome.FIELD_CHANGED,
        )
    }
}
