package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancy
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancyResolution
import com.docuhyphen.app.api.repository.informationrequest.externalsource.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestExternalSourceRecordAssembler @Inject constructor(
    private val exchangeRepository: InformationRequestConnectorExchangeRepository,
    private val valueRepository: InformationRequestImportedValueRepository,
    private val decisionRepository: InformationRequestImportedValueDecisionRepository,
    private val discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository,
    private val resolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository,
    private val outputRepository: InformationRequestGeneratedOutputRepository,
)
{
    fun assemble(requestId: UUID): JsonObject = buildJsonObject {
        put("connectorExchanges", exchangesOf(requestId))
        put("importedValues", valuesOf(requestId))
        put("generatedOutputs", outputsOf(requestId))
    }

    private fun exchangesOf(requestId: UUID): JsonArray = buildJsonArray {
        exchangeRepository.findForRequest(requestId).forEach { exchange ->
            add(
                buildJsonObject {
                    put("connectorExchangeId", exchange.id.toString())
                    put("requirementId", exchange.informationRequestRequirementId.toString())
                    put("connectorKey", exchange.connectorKey)
                    put("connectorKind", exchange.connectorKind.name)
                    put("contractVersion", exchange.contractVersion)
                    put("state", exchange.state.name)
                    put("lookupReference", exchange.lookupReference)
                    put("externalReference", exchange.externalReference)
                    put("attemptCount", exchange.attemptCount)
                    put("failureCode", exchange.failureCode)
                    put("requestedByPrincipalKind", exchange.requestedByPrincipalKind.name)
                    put("requestedByPrincipalId", exchange.requestedByPrincipalId.toString())
                    put("requestedAt", instant(exchange.requestedAt))
                    put("completedAt", instant(exchange.completedAt))
                },
            )
        }
    }

    private fun valuesOf(requestId: UUID): JsonArray
    {
        val decisions = decisionRepository.findForRequest(requestId).associateBy { it.importedValueId }
        val discrepancies = discrepancyRepository.findForRequest(requestId).groupBy { it.importedValueId }
        val resolutions = resolutionRepository.findForRequest(requestId).associateBy { it.discrepancyId }
        return buildJsonArray {
            valueRepository.findForRequest(requestId).forEach { value ->
                add(
                    buildJsonObject {
                        put("importedValueId", value.id.toString())
                        put("requirementId", value.informationRequestRequirementId.toString())
                        put("sourceKind", value.sourceKind.name)
                        put("connectorExchangeId", value.connectorExchangeId?.toString())
                        put("sourceReference", value.sourceReference)
                        put("resultKey", value.resultKey)
                        put("valueType", value.valueType.name)
                        put("value", Json.parseToJsonElement(value.canonicalValue))
                        put("confidence", value.confidence.name)
                        put("verifiedAt", instant(value.verifiedAt))
                        put("expiresAt", instant(value.expiresAt))
                        put("provenanceReference", value.provenanceReference)
                        put("recordedByPrincipalKind", value.recordedByPrincipalKind.name)
                        put("recordedByPrincipalId", value.recordedByPrincipalId.toString())
                        put("recordedAt", instant(value.recordedAt))
                        put("decision", decisions[value.id]?.let(::decisionOf) ?: JsonNull)
                        put(
                            "discrepancies",
                            buildJsonArray {
                                discrepancies[value.id].orEmpty().forEach { add(discrepancyOf(it, resolutions[it.id])) }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun decisionOf(decision: InformationRequestImportedValueDecision): JsonObject = buildJsonObject {
        put("decision", decision.decision.name)
        put("reasonCode", decision.reasonCode)
        put("decidedByPrincipalKind", decision.decidedByPrincipalKind.name)
        put("decidedByPrincipalId", decision.decidedByPrincipalId.toString())
        put("decidedAt", instant(decision.decidedAt))
    }

    private fun discrepancyOf(
        discrepancy: InformationRequestImportedValueDiscrepancy,
        resolution: InformationRequestImportedValueDiscrepancyResolution?,
    ): JsonObject = buildJsonObject {
        put("discrepancyId", discrepancy.id.toString())
        put("responseId", discrepancy.responseId.toString())
        put("responseRevision", discrepancy.responseRevision)
        put("importedValue", Json.parseToJsonElement(discrepancy.importedCanonicalValue))
        put("responseValue", Json.parseToJsonElement(discrepancy.responseCanonicalValue))
        put("recordedAt", instant(discrepancy.recordedAt))
        put(
            "resolution",
            resolution?.let {
                buildJsonObject {
                    put("resolution", it.resolution.name)
                    put("reasonCode", it.reasonCode)
                    put("resolvedByPrincipalKind", it.resolvedByPrincipalKind.name)
                    put("resolvedByPrincipalId", it.resolvedByPrincipalId.toString())
                    put("resolvedAt", instant(it.resolvedAt))
                }
            } ?: JsonNull,
        )
    }

    private fun outputsOf(requestId: UUID): JsonArray = buildJsonArray {
        outputRepository.findForRequest(requestId).forEach { output ->
            add(
                buildJsonObject {
                    put("generatedOutputId", output.id.toString())
                    put("packageId", output.packageId?.toString())
                    put("outputKey", output.outputKey)
                    put("externalReference", output.externalReference)
                    put("contentHashSha256", output.contentHashSha256)
                    put("mediaType", output.mediaType)
                    put("producedBySource", output.producedBySource)
                    put("producedAt", instant(output.producedAt))
                    put("recordedByPrincipalKind", output.recordedByPrincipalKind.name)
                    put("recordedByPrincipalId", output.recordedByPrincipalId.toString())
                    put("recordedAt", instant(output.recordedAt))
                },
            )
        }
    }

    private fun instant(value: Timestamp?): String? = value?.toInstant()?.toString()
}
