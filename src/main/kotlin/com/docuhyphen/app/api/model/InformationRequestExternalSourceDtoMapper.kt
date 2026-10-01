package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestConnectorExchangeDto
import com.docuhyphen.app.api.model.dto.InformationRequestDiscrepancyResolutionDto
import com.docuhyphen.app.api.model.dto.InformationRequestGeneratedOutputDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueDecisionDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueDiscrepancyDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueReconciliationDto
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestGeneratedOutput
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestDiscrepancyView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueReconciliation
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueView
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import kotlinx.serialization.json.Json
import java.util.UUID

object InformationRequestExternalSourceDtoMapper
{
    fun toDto(exchange: InformationRequestConnectorExchange, caller: PrincipalRef): InformationRequestConnectorExchangeDto =
        InformationRequestConnectorExchangeDto(
            id = exchange.id,
            requirementId = exchange.informationRequestRequirementId,
            connectorKey = exchange.connectorKey,
            connectorKind = exchange.connectorKind,
            contractVersion = exchange.contractVersion,
            state = exchange.state,
            lookupReference = exchange.lookupReference,
            externalReference = exchange.externalReference,
            attemptCount = exchange.attemptCount,
            nextAttemptAt = exchange.nextAttemptAt,
            failureCode = exchange.failureCode,
            requestedAt = exchange.requestedAt,
            completedAt = exchange.completedAt,
            requestedByCaller = caller.isPrincipal(exchange.requestedByPrincipalKind, exchange.requestedByPrincipalId),
        )

    fun toDto(view: InformationRequestImportedValueView, caller: PrincipalRef): InformationRequestImportedValueDto
    {
        val value = view.value
        return InformationRequestImportedValueDto(
            id = value.id,
            requirementId = value.informationRequestRequirementId,
            sourceKind = value.sourceKind,
            connectorExchangeId = value.connectorExchangeId,
            sourceReference = value.sourceReference,
            resultKey = value.resultKey,
            valueType = value.valueType,
            value = Json.parseToJsonElement(value.canonicalValue),
            confidence = value.confidence,
            verifiedAt = value.verifiedAt,
            expiresAt = value.expiresAt,
            provenanceReference = value.provenanceReference,
            recordedAt = value.recordedAt,
            recordedByCaller = caller.isPrincipal(value.recordedByPrincipalKind, value.recordedByPrincipalId),
            decision = view.decision?.let { decision ->
                InformationRequestImportedValueDecisionDto(
                    id = decision.id,
                    decision = decision.decision,
                    reasonCode = decision.reasonCode,
                    decidedAt = decision.decidedAt,
                    decidedByCaller = caller.isPrincipal(decision.decidedByPrincipalKind, decision.decidedByPrincipalId),
                )
            },
            discrepancies = view.discrepancies.map { toDto(it, caller) },
        )
    }

    fun toDto(view: InformationRequestDiscrepancyView, caller: PrincipalRef): InformationRequestImportedValueDiscrepancyDto
    {
        val discrepancy = view.discrepancy
        return InformationRequestImportedValueDiscrepancyDto(
            id = discrepancy.id,
            importedValueId = discrepancy.importedValueId,
            responseRevision = discrepancy.responseRevision,
            importedValue = Json.parseToJsonElement(discrepancy.importedCanonicalValue),
            responseValue = Json.parseToJsonElement(discrepancy.responseCanonicalValue),
            recordedAt = discrepancy.recordedAt,
            resolution = view.resolution?.let { resolution ->
                InformationRequestDiscrepancyResolutionDto(
                    id = resolution.id,
                    resolution = resolution.resolution,
                    reasonCode = resolution.reasonCode,
                    resolvedAt = resolution.resolvedAt,
                    resolvedByCaller = caller.isPrincipal(resolution.resolvedByPrincipalKind, resolution.resolvedByPrincipalId),
                )
            },
        )
    }

    fun toDto(reconciliation: InformationRequestImportedValueReconciliation): InformationRequestImportedValueReconciliationDto =
        InformationRequestImportedValueReconciliationDto(
            importedValueId = reconciliation.importedValueId,
            requirementId = reconciliation.requirementId,
            outcome = reconciliation.outcome,
            responseValue = reconciliation.responseCanonicalValue?.let(Json::parseToJsonElement),
            discrepancyId = reconciliation.discrepancyId,
        )

    fun toDto(output: InformationRequestGeneratedOutput, caller: PrincipalRef): InformationRequestGeneratedOutputDto =
        InformationRequestGeneratedOutputDto(
            id = output.id,
            packageId = output.packageId,
            outputKey = output.outputKey,
            externalReference = output.externalReference,
            contentHashSha256 = output.contentHashSha256,
            mediaType = output.mediaType,
            producedBySource = output.producedBySource,
            producedAt = output.producedAt,
            recordedAt = output.recordedAt,
            recordedByCaller = caller.isPrincipal(output.recordedByPrincipalKind, output.recordedByPrincipalId),
        )

    private fun PrincipalRef.isPrincipal(principalKind: PrincipalKind, principalId: UUID): Boolean =
        kind == principalKind && id == principalId
}
