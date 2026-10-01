package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValue
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancy
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorContract
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorResult
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestDiscrepancyView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueProvenance
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueReconciliation
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestPreparedConnectorResult
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestPreparedValue
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestReconciliationOutcome
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestValueComparison
import com.docuhyphen.app.api.model.informationrequest.externalsource.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ResolveInformationRequestDiscrepancyCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyResolutionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestImportedValueService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val queryService: InformationRequestQueryService,
    private val valueRepository: InformationRequestImportedValueRepository,
    private val decisionRepository: InformationRequestImportedValueDecisionRepository,
    private val discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository,
    private val resolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val canonicalizer: InformationRequestImportedValueCanonicalizer,
    private val responseStore: InformationRequestResponseStore,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun propose(command: ProposeInformationRequestImportedValueCommand): InformationRequestImportedValueView
    {
        val fingerprint = listOf(
            PROPOSE_OPERATION, command.requestId, command.requirementId, command.resultKey, command.valueType, command.value,
            command.sourceReference, command.confidence, command.verifiedAt ?: "", command.expiresAt ?: "", command.provenanceReference,
        ).joinToString("|")
        return once(command.requestId, PROPOSE_OPERATION, command.access, command.idempotencyKey, fingerprint, Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES) {
            val locked = gate.lock(command.requestId)
            gate.requireMutation(locked, InformationRequestMutation.RECORD_EXTERNAL_VALUE)
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES), command.requestId)
            val requirement = requirementOf(command.requestId, command.requirementId)
            val provenance = InformationRequestImportedValueProvenance(
                command.sourceReference.trim(),
                command.confidence,
                command.verifiedAt,
                command.expiresAt,
                command.provenanceReference.trim(),
            )
            val resultKey = command.resultKey.trim()
            requireProvenance(resultKey, provenance, clock.instant())
            val canonicalValue = canonicalizer.canonicalize(requirement, command.valueType, command.value)
            val value = save(
                locked.request,
                requirement,
                null,
                InformationRequestPreparedValue(resultKey, command.valueType, canonicalValue),
                provenance,
                command.access.principal,
            )
            value.id
        }
    }

    fun prepareConnectorResult(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        contract: InformationRequestConnectorContract,
        result: InformationRequestConnectorResult,
    ): InformationRequestPreparedConnectorResult
    {
        val requirement = requirementOf(request.id, exchange.informationRequestRequirementId)
        val keys = result.values.map { it.resultKey.trim() }
        if (keys.isEmpty() || keys.toSet().size != keys.size)
        {
            throw InformationRequestCommandRequestException("A connector result names each of its values once")
        }
        if (keys.any { it !in contract.resultKeys })
        {
            throw InformationRequestCommandRequestException("A connector returns only the values its contract declares")
        }
        val provenance = InformationRequestImportedValueProvenance(
            result.source.trim(),
            result.confidence,
            result.verifiedAt,
            result.expiresAt,
            result.provenanceReference.trim(),
        )
        val now = clock.instant()
        keys.forEach { requireProvenance(it, provenance, now) }
        contract.maximumResultAge?.let { age ->
            val verifiedAt = result.verifiedAt
                ?: throw InformationRequestCommandRequestException("A connector with a result age limit states when its result was verified")
            if (verifiedAt.isBefore(now.minus(age)))
            {
                throw InformationRequestCommandRequestException("A connector result is older than its contract allows")
            }
        }
        val values = result.values.map { value ->
            InformationRequestPreparedValue(
                value.resultKey.trim(),
                value.valueType,
                canonicalizer.canonicalize(requirement, value.valueType, value.value),
            )
        }
        return InformationRequestPreparedConnectorResult(requirement, provenance, values)
    }

    fun recordConnectorResult(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        prepared: InformationRequestPreparedConnectorResult,
    ): List<InformationRequestImportedValue>
    {
        val requester = PrincipalRef(exchange.requestedByPrincipalKind, exchange.requestedByPrincipalId)
        return prepared.values.map { value -> save(request, prepared.requirement, exchange.id, value, prepared.provenance, requester) }
    }

    @Transactional
    fun decide(command: DecideInformationRequestImportedValueCommand): InformationRequestImportedValueView
    {
        val reasonCode = command.reasonCode.trim()
        val fingerprint = "$DECIDE_OPERATION|${command.importedValueId}|${command.decision}|$reasonCode"
        return once(command.requestId, DECIDE_OPERATION, command.access, command.idempotencyKey, fingerprint, Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES) {
            val locked = gate.lock(command.requestId)
            gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES), command.requestId)
            requireReason(reasonCode, "A decision on an imported value states its reason")
            val value = valueOf(command.requestId, command.importedValueId)
            if (decisionRepository.findForRequest(command.requestId).any { it.importedValueId == value.id })
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.IMPORTED_VALUE_ALREADY_DECIDED,
                    "This imported value has already been decided",
                )
            }
            if (value.sourceKind == InformationRequestImportedValueSource.MANUAL &&
                value.recordedByPrincipalKind == command.access.principal.kind &&
                value.recordedByPrincipalId == command.access.principal.id
            )
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES,
                    "A value is decided by someone other than the person who recorded it",
                )
            }
            val now = clock.instant()
            if (command.decision == InformationRequestImportedValueDecisionKind.ACCEPTED && value.isExpiredAt(now))
            {
                throw InformationRequestLifecycleException(InformationRequestErrorCatalog.IMPORTED_VALUE_EXPIRED, "An expired value cannot be accepted")
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
            history(
                locked.request, command.access.principal, InformationRequestMutation.DECIDE_EXTERNAL_VALUE, "decision|${decision.id}",
                mapOf("importedValueId" to value.id.toString(), "importedValueDecision" to command.decision.name),
            )
            value.id
        }
    }

    @Transactional
    fun reconcile(command: ReconcileInformationRequestImportedValuesCommand): List<InformationRequestImportedValueReconciliation>
    {
        val locked = gate.lock(command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES), command.requestId)
        val rejected = decisionRepository.findForRequest(command.requestId)
            .filter { it.decision == InformationRequestImportedValueDecisionKind.REJECTED }
            .map { it.importedValueId }
            .toSet()
        val recorded = discrepancyRepository.findForRequest(command.requestId)
        val responses = responseStore.findCurrentForRequest(command.requestId).associateBy { it.informationRequestRequirementId }
        val requirements = requirementRepository.findForRequest(command.requestId).associateBy { it.id }
        val now = clock.instant()
        val created = mutableListOf<InformationRequestImportedValueDiscrepancy>()
        val results = valueRepository.findForRequest(command.requestId).filter { it.id !in rejected }.map { value ->
            val response = responses[value.informationRequestRequirementId]
            val comparison = if (value.isExpiredAt(now)) null
            else canonicalizer.compare(requireNotNull(requirements[value.informationRequestRequirementId]), value, response)
            when (comparison)
            {
                null -> reconciliation(value, InformationRequestReconciliationOutcome.EXPIRED)
                InformationRequestValueComparison.NotComparable -> reconciliation(value, InformationRequestReconciliationOutcome.NOT_COMPARABLE)
                InformationRequestValueComparison.NoAnswer -> reconciliation(value, InformationRequestReconciliationOutcome.NO_ANSWER)
                InformationRequestValueComparison.Matches -> reconciliation(value, InformationRequestReconciliationOutcome.MATCHES)
                is InformationRequestValueComparison.Differs ->
                {
                    val answered = requireNotNull(response)
                    val discrepancy = recorded.firstOrNull { it.importedValueId == value.id && it.responseRevision == answered.responseRevision }
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
        created.firstOrNull()?.let { first ->
            history(
                locked.request, command.access.principal, InformationRequestMutation.DECIDE_EXTERNAL_VALUE, "reconciliation|${first.id}",
                mapOf("discrepancyId" to first.id.toString(), "discrepancyCount" to created.size.toString()),
            )
        }
        return results
    }

    @Transactional
    fun resolve(command: ResolveInformationRequestDiscrepancyCommand): InformationRequestDiscrepancyView
    {
        val reasonCode = command.reasonCode.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = RESOLVE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex("$RESOLVE_OPERATION|${command.discrepancyId}|${command.resolution}|$reasonCode"),
        )
        val discrepancyId = when (
            val decision = commandReceiptService.runOnce(receipt) {
                val id = resolveOnce(command, reasonCode)
                CommandMutationResult(id, CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES), command.requestId)
                decision.result.resourceId
            }
        }
        val discrepancy = requireNotNull(discrepancyRepository.findById(discrepancyId))
        return InformationRequestDiscrepancyView(discrepancy, resolutionRepository.findForRequest(command.requestId).firstOrNull { it.discrepancyId == discrepancyId })
    }

    fun values(requestId: UUID, access: RequestAccessContext): List<InformationRequestImportedValueView>
    {
        queryService.findById(requestId, access)
        InformationRequestConnectorService.requireRequestingSide(gate, access, requestId)
        return views(requestId, valueRepository.findForRequest(requestId))
    }

    private fun resolveOnce(command: ResolveInformationRequestDiscrepancyCommand, reasonCode: String): UUID
    {
        val locked = gate.lock(command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.DECIDE_EXTERNAL_VALUE)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES), command.requestId)
        requireReason(reasonCode, "A resolution states its reason")
        val discrepancy = discrepancyRepository.findById(command.discrepancyId)?.takeIf { it.informationRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Discrepancy not found")
        if (resolutionRepository.findForRequest(command.requestId).any { it.discrepancyId == discrepancy.id })
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.DISCREPANCY_ALREADY_RESOLVED, "This discrepancy is already resolved")
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
        history(
            locked.request, command.access.principal, InformationRequestMutation.DECIDE_EXTERNAL_VALUE, "resolution|${resolution.id}",
            mapOf("discrepancyId" to discrepancy.id.toString(), "discrepancyResolution" to command.resolution.name),
        )
        return discrepancy.id
    }

    @Suppress("LongParameterList")
    private fun once(
        requestId: UUID,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        replayAction: Action,
        mutation: () -> UUID,
    ): InformationRequestImportedValueView
    {
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(access.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(fingerprint),
        )
        val valueId = when (
            val decision = commandReceiptService.runOnce(receipt) {
                val id = mutation()
                CommandMutationResult(id, CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(access, listOf(replayAction), requestId)
                decision.result.resourceId
            }
        }
        return views(requestId, listOf(valueOf(requestId, valueId))).single()
    }

    @Suppress("LongParameterList")
    private fun save(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        exchangeId: UUID?,
        value: InformationRequestPreparedValue,
        provenance: InformationRequestImportedValueProvenance,
        principal: PrincipalRef,
    ): InformationRequestImportedValue
    {
        val saved = valueRepository.save(
            InformationRequestImportedValue().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                sourceKind = if (exchangeId == null) InformationRequestImportedValueSource.MANUAL else InformationRequestImportedValueSource.CONNECTOR
                connectorExchangeId = exchangeId
                sourceReference = provenance.source
                resultKey = value.resultKey
                valueType = value.valueType
                canonicalValue = value.canonicalValue
                confidence = provenance.confidence
                verifiedAt = provenance.verifiedAt?.let(Timestamp::from)
                expiresAt = provenance.expiresAt?.let(Timestamp::from)
                provenanceReference = provenance.reference
                recordedByPrincipalKind = principal.kind
                recordedByPrincipalId = principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        history(
            request, principal, InformationRequestMutation.RECORD_EXTERNAL_VALUE, "value|${saved.id}",
            buildMap {
                put("importedValueId", saved.id.toString())
                put("requirementId", requirement.id.toString())
                put("sourceKind", saved.sourceKind.name)
                put("resultKey", saved.resultKey)
                exchangeId?.let { put("connectorExchangeId", it.toString()) }
            },
        )
        return saved
    }

    private fun requireProvenance(resultKey: String, provenance: InformationRequestImportedValueProvenance, now: Instant)
    {
        if (!MACHINE_KEY.matches(resultKey)) throw InformationRequestCommandRequestException("An imported value names its result with a lowercase key")
        if (provenance.source.isEmpty() || provenance.reference.isEmpty())
        {
            throw InformationRequestCommandRequestException("An imported value states its source and provenance reference")
        }
        if (provenance.source.length > TEXT_LENGTH || provenance.reference.length > TEXT_LENGTH)
        {
            throw InformationRequestCommandRequestException("A source or provenance reference is at most $TEXT_LENGTH characters")
        }
        if (provenance.confidence != InformationRequestSourceConfidence.ASSERTED && provenance.verifiedAt == null)
        {
            throw InformationRequestCommandRequestException("A matched or verified value states when it was verified")
        }
        if (provenance.verifiedAt?.isAfter(now.plus(CLOCK_TOLERANCE)) == true)
        {
            throw InformationRequestCommandRequestException("A value cannot have been verified in the future")
        }
        val expiresAt = provenance.expiresAt ?: return
        if (provenance.verifiedAt != null && !expiresAt.isAfter(provenance.verifiedAt))
        {
            throw InformationRequestCommandRequestException("An imported value expires after it was verified")
        }
        if (!expiresAt.isAfter(now)) throw InformationRequestCommandRequestException("An imported value that has already expired is not recorded")
    }

    private fun requireReason(reasonCode: String, message: String)
    {
        if (reasonCode.isEmpty()) throw InformationRequestCommandRequestException(message)
        if (reasonCode.length > REASON_LENGTH) throw InformationRequestCommandRequestException("A reason is at most $REASON_LENGTH characters")
    }

    private fun history(
        request: InformationRequest,
        actor: PrincipalRef,
        mutation: InformationRequestMutation,
        scope: String,
        details: Map<String, String>,
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                idempotencyKey = "information_request.external|$scope",
                details = details,
            ),
        )
    }

    private fun reconciliation(value: InformationRequestImportedValue, outcome: InformationRequestReconciliationOutcome) =
        InformationRequestImportedValueReconciliation(value.id, value.informationRequestRequirementId, outcome, null, null)

    private fun InformationRequestImportedValue.isExpiredAt(now: Instant): Boolean =
        expiresAt?.toInstant()?.isAfter(now) == false

    private fun requirementOf(requestId: UUID, requirementId: UUID): InformationRequestRequirement =
        requirementRepository.findForRequest(requestId).firstOrNull { it.id == requirementId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request Requirement not found")

    private fun valueOf(requestId: UUID, valueId: UUID): InformationRequestImportedValue =
        valueRepository.findById(valueId)?.takeIf { it.informationRequestId == requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Imported value not found")

    private fun views(requestId: UUID, values: List<InformationRequestImportedValue>): List<InformationRequestImportedValueView>
    {
        val decisions = decisionRepository.findForRequest(requestId).associateBy { it.importedValueId }
        val resolutions = resolutionRepository.findForRequest(requestId).associateBy { it.discrepancyId }
        val discrepancies = discrepancyRepository.findForRequest(requestId).groupBy { it.importedValueId }
        return values.map { value ->
            InformationRequestImportedValueView(
                value = value,
                decision = decisions[value.id],
                discrepancies = discrepancies[value.id].orEmpty().map { InformationRequestDiscrepancyView(it, resolutions[it.id]) },
            )
        }
    }

    private companion object
    {
        const val PROPOSE_OPERATION = "propose-information-request-imported-value"
        const val DECIDE_OPERATION = "decide-information-request-imported-value"
        const val RESOLVE_OPERATION = "resolve-information-request-imported-value-discrepancy"
        const val TEXT_LENGTH = 256
        const val REASON_LENGTH = 128
        val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(5)
        val MACHINE_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    }
}
