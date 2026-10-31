package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestImportedValueIntakeService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val records: InformationRequestImportedValueRecords,
    private val valueRepository: InformationRequestImportedValueRepository,
    private val requirementRevisionRepository: InformationRequestRequirementRevisionRepository,
    private val canonicalizer: InformationRequestImportedValueCanonicalizer,
    private val clock: Clock,
) {
    @Transactional
    fun propose(command: ProposeInformationRequestImportedValueCommand): InformationRequestImportedValueView {
        val fingerprint = CommandRequestFingerprint.of(
            PROPOSE_OPERATION,
            command.requestId,
            command.requirementId,
            command.resultKey,
            command.valueType,
            command.value,
            command.sourceReference,
            command.confidence,
            command.verifiedAt,
            command.expiresAt,
            command.provenanceReference,
        )
        return records.runOnce(
            command.requestId,
            PROPOSE_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint,
            Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES
        ) {
            val locked = gate.lock(command.requestId)
            gate.authorizeRequest(
                command.access,
                listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES),
                command.requestId
            )
            gate.requireMutation(locked, InformationRequestMutation.RECORD_EXTERNAL_VALUE)
            gate.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)
            val requirement = records.requirementOf(command.requestId, command.requirementId)
            records.requireRecentVerification(command.access, command.requestId, requirement.id)
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
                InformationRequestAuditActor.of(command.access),
            )
            value.id
        }
    }

    fun acceptsRequirement(exchange: InformationRequestConnectorExchange): Boolean =
        runCatching {
            records.requirementOf(
                exchange.informationRequestId,
                exchange.informationRequestRequirementId
            )
        }.isSuccess

    fun prepareConnectorResult(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        contract: InformationRequestConnectorContract,
        result: InformationRequestConnectorResult,
    ): InformationRequestPreparedConnectorResult {
        val requirement = records.requirementOf(request.id, exchange.informationRequestRequirementId)
        val keys = result.values.map { it.resultKey.trim() }
        if (keys.isEmpty() || keys.toSet().size != keys.size) {
            throw InformationRequestCommandRequestException("A connector result names each of its values once")
        }
        if (keys.any { it !in contract.resultKeys }) {
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
            if (verifiedAt.isBefore(now.minus(age))) {
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
    ): List<InformationRequestImportedValue> {
        val requester = PrincipalRef(exchange.requestedByPrincipalKind, exchange.requestedByPrincipalId)
        return prepared.values.map { value ->
            save(
                request,
                prepared.requirement,
                exchange.id,
                value,
                prepared.provenance,
                InformationRequestAuditActor.system(requester),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun save(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        exchangeId: UUID?,
        value: InformationRequestPreparedValue,
        provenance: InformationRequestImportedValueProvenance,
        actor: InformationRequestAuditActor,
    ): InformationRequestImportedValue {
        val revision = requirementRevisionRepository.findCurrentForRequirement(requirement.id)
            ?: throw IllegalStateException("Requirement ${requirement.id} has no current revision")
        val saved = valueRepository.save(
            InformationRequestImportedValue().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                requirementRevisionId = revision.id
                sourceKind =
                    if (exchangeId == null) InformationRequestImportedValueSource.MANUAL else InformationRequestImportedValueSource.CONNECTOR
                connectorExchangeId = exchangeId
                sourceReference = provenance.source
                resultKey = value.resultKey
                valueType = value.valueType
                canonicalValue = value.canonicalValue
                confidence = provenance.confidence
                verifiedAt = provenance.verifiedAt?.let(Timestamp::from)
                expiresAt = provenance.expiresAt?.let(Timestamp::from)
                provenanceReference = provenance.reference
                recordedByPrincipalKind = actor.principal.kind
                recordedByPrincipalId = actor.principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        records.recordHistory(
            request, actor, InformationRequestMutation.RECORD_EXTERNAL_VALUE, "value|${saved.id}",
            buildMap {
                put("importedValueId", saved.id.toString())
                put("requirementId", requirement.id.toString())
                put("sourceKind", saved.sourceKind.name)
                put("resultKey", saved.resultKey)
                exchangeId?.let { put("connectorExchangeId", it.toString()) }
            },
            requireAuditCapture = saved.sourceKind == InformationRequestImportedValueSource.CONNECTOR,
        )
        return saved
    }

    private fun requireProvenance(
        resultKey: String,
        provenance: InformationRequestImportedValueProvenance,
        now: Instant
    ) {
        if (!MACHINE_KEY.matches(resultKey)) throw InformationRequestCommandRequestException("An imported value names its result with a lowercase key")
        if (provenance.source.isEmpty() || provenance.reference.isEmpty()) {
            throw InformationRequestCommandRequestException("An imported value states its source and provenance reference")
        }
        if (provenance.source.length > TEXT_LENGTH || provenance.reference.length > TEXT_LENGTH) {
            throw InformationRequestCommandRequestException("A source or provenance reference is at most $TEXT_LENGTH characters")
        }
        if (provenance.confidence != InformationRequestSourceConfidence.ASSERTED && provenance.verifiedAt == null) {
            throw InformationRequestCommandRequestException("A matched or verified value states when it was verified")
        }
        if (provenance.verifiedAt?.isAfter(now.plus(CLOCK_TOLERANCE)) == true) {
            throw InformationRequestCommandRequestException("A value cannot have been verified in the future")
        }
        val expiresAt = provenance.expiresAt ?: return
        if (provenance.verifiedAt != null && !expiresAt.isAfter(provenance.verifiedAt)) {
            throw InformationRequestCommandRequestException("An imported value expires after it was verified")
        }
        if (!expiresAt.isAfter(now)) throw InformationRequestCommandRequestException("An imported value that has already expired is not recorded")
    }

    private companion object {
        const val PROPOSE_OPERATION = "propose-information-request-imported-value"
        const val TEXT_LENGTH = 256
        val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(5)
        val MACHINE_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    }
}
