package com.docuhyphen.app.api.resource.informationrequest.externalsource

import com.docuhyphen.app.api.model.dto.InformationRequestConnectorExchangeDto
import com.docuhyphen.app.api.model.dto.InformationRequestGeneratedOutputDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueDiscrepancyDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueDto
import com.docuhyphen.app.api.model.dto.InformationRequestImportedValueReconciliationDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorKind
import com.docuhyphen.app.api.model.entity.InformationRequestDiscrepancyResolution
import com.docuhyphen.app.api.model.entity.InformationRequestGeneratedOutput
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValue
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecision
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueDiscrepancy
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestDiscrepancyView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueReconciliation
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueView
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestReconciliationOutcome
import com.docuhyphen.app.api.model.informationrequest.externalsource.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ReconcileInformationRequestImportedValuesCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ResolveInformationRequestDiscrepancyCommand
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestConnectorExchangeResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestDiscrepancyResolutionResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestImportedValueReconciliationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestImportedValueResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.record.InformationRequestGeneratedOutputResource
import com.docuhyphen.app.api.resource.informationrequest.record.operations.InformationRequestGeneratedOutputResourceOperations
import com.docuhyphen.app.api.resource.model.DecideInformationRequestImportedValueRequest
import com.docuhyphen.app.api.resource.model.ProposeInformationRequestImportedValueRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestGeneratedOutputRequest
import com.docuhyphen.app.api.resource.model.RequestInformationRequestConnectorExchangeRequest
import com.docuhyphen.app.api.resource.model.ResolveInformationRequestDiscrepancyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestGeneratedOutputService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestExternalSourceResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val caller = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(caller, AuthorizationContext(sessionRef = "user-session"))
    private val connectors: InformationRequestConnectorService = mock()
    private val importedValues: InformationRequestImportedValueService = mock()
    private val outputs: InformationRequestGeneratedOutputService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val exchangeResource = InformationRequestConnectorExchangeResource(connectors, accessContextFactory)
    private val valueResource = InformationRequestImportedValueResource(importedValues, accessContextFactory)
    private val reconciliationResource = InformationRequestImportedValueReconciliationResource(importedValues, accessContextFactory)
    private val resolutionResource = InformationRequestDiscrepancyResolutionResource(importedValues, accessContextFactory)
    private val outputResource = InformationRequestGeneratedOutputResource(outputs, accessContextFactory)
    private val verifiedAt = Instant.parse("2026-09-01T10:00:00Z")

    private val exchange = InformationRequestConnectorExchange().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirementId
        connectorKey = "record-verification"
        connectorKind = InformationRequestConnectorKind.EXTERNAL_VERIFICATION
        contractVersion = 1
        lookupReference = "record-1"
        requestedByPrincipalKind = PrincipalKind.USER
        requestedByPrincipalId = caller.id
    }
    private val value = InformationRequestImportedValue().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirementId
        sourceKind = InformationRequestImportedValueSource.MANUAL
        sourceReference = "registry-extract"
        resultKey = "recorded-value"
        valueType = FieldValueType.SHORT_TEXT
        canonicalValue = "\"Recorded answer\""
        confidence = InformationRequestSourceConfidence.VERIFIED
        verifiedAt = Timestamp.from(this@InformationRequestExternalSourceResourceContractTest.verifiedAt)
        provenanceReference = "extract-2026-09"
        recordedByPrincipalKind = PrincipalKind.USER
        recordedByPrincipalId = UUID.randomUUID()
    }
    private val discrepancy = InformationRequestImportedValueDiscrepancy().apply {
        importedValueId = value.id
        informationRequestId = requestId
        responseId = UUID.randomUUID()
        responseRevision = 3
        importedCanonicalValue = "\"Recorded answer\""
        responseCanonicalValue = "\"Another answer\""
        recordedByPrincipalKind = PrincipalKind.USER
        recordedByPrincipalId = caller.id
    }
    private val decision = InformationRequestImportedValueDecision().apply {
        importedValueId = value.id
        informationRequestId = requestId
        this.decision = InformationRequestImportedValueDecisionKind.ACCEPTED
        reasonCode = "matches the record"
        decidedByPrincipalKind = PrincipalKind.USER
        decidedByPrincipalId = caller.id
    }
    private val view = InformationRequestImportedValueView(value, decision, listOf(InformationRequestDiscrepancyView(discrepancy, null)))
    private val output = InformationRequestGeneratedOutput().apply {
        informationRequestId = requestId
        outputKey = "summary-output"
        externalReference = "external://outputs/summary-1"
        contentHashSha256 = "a".repeat(64)
        producedBySource = "summary-service"
        recordedByPrincipalKind = PrincipalKind.USER
        recordedByPrincipalId = caller.id
    }

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(connectors.request(any())).thenReturn(exchange)
        whenever(connectors.exchanges(requestId, access)).thenReturn(listOf(exchange))
        whenever(importedValues.propose(any())).thenReturn(view)
        whenever(importedValues.decide(any())).thenReturn(view)
        whenever(importedValues.values(requestId, access)).thenReturn(listOf(view))
        whenever(importedValues.reconcile(any())).thenReturn(
            listOf(InformationRequestImportedValueReconciliation(value.id, requirementId, InformationRequestReconciliationOutcome.DIFFERS, "\"Another answer\"", discrepancy.id)),
        )
        whenever(importedValues.resolve(any())).thenReturn(InformationRequestDiscrepancyView(discrepancy, null))
        whenever(outputs.record(any())).thenReturn(output)
        whenever(outputs.outputs(requestId, access)).thenReturn(listOf(output))
    }

    @Test
    fun `external source resources are sub-resources of one request`()
    {
        assertEquals("/information-requests/{id}/connector-exchanges", path(InformationRequestConnectorExchangeResourceOperations::class.java))
        assertEquals("/information-requests/{id}/imported-values", path(InformationRequestImportedValueResourceOperations::class.java))
        assertEquals("/{valueId}/decisions", InformationRequestImportedValueResourceOperations::class.java.getMethod("decide", String::class.java, String::class.java, DecideInformationRequestImportedValueRequest::class.java, String::class.java).getAnnotation(Path::class.java).value)
        assertEquals("/information-requests/{id}/imported-value-reconciliations", path(InformationRequestImportedValueReconciliationResourceOperations::class.java))
        assertEquals("/information-requests/{id}/imported-value-discrepancies/{discrepancyId}/resolutions", path(InformationRequestDiscrepancyResolutionResourceOperations::class.java))
        assertEquals("/information-requests/{id}/generated-outputs", path(InformationRequestGeneratedOutputResourceOperations::class.java))
        assertTrue(InformationRequestConnectorExchangeResourceOperations::class.java.getMethod("list", String::class.java).isAnnotationPresent(GET::class.java))
        assertTrue(InformationRequestImportedValueReconciliationResourceOperations::class.java.getMethod("reconcile", String::class.java, String::class.java).isAnnotationPresent(POST::class.java))
    }

    @Test
    fun `requesting a connector exchange delegates the request and answers the exchange`()
    {
        val response = exchangeResource.request(requestId.toString(), RequestInformationRequestConnectorExchangeRequest(requirementId, "record-verification", "record-1"), "key-1")

        val command = argumentCaptor<RequestInformationRequestConnectorExchangeCommand>().also { verify(connectors).request(it.capture()) }.firstValue
        assertEquals(RequestInformationRequestConnectorExchangeCommand(requestId, requirementId, "record-verification", "record-1", access, "key-1"), command)
        assertEquals(201, response.status)
        val dto = response.entity as InformationRequestConnectorExchangeDto
        assertEquals("record-1", dto.lookupReference)
        assertTrue(dto.requestedByCaller)
        assertNoPrincipalIds(Json.encodeToString(InformationRequestConnectorExchangeDto.serializer(), dto))
        val listed = exchangeResource.list(requestId.toString())
        assertEquals(200, listed.status)
        assertEquals(listOf(exchange.id), (listed.entity as Array<*>).map { (it as InformationRequestConnectorExchangeDto).id })
    }

    @Test
    fun `proposing and deciding an imported value delegate their commands and answer the value`()
    {
        val expires = Instant.parse("2026-12-01T10:00:00Z")
        val proposed = valueResource.propose(
            requestId.toString(),
            ProposeInformationRequestImportedValueRequest(
                requirementId, "recorded-value", FieldValueType.SHORT_TEXT, JsonPrimitive("Recorded answer"), "registry-extract",
                InformationRequestSourceConfidence.VERIFIED, Timestamp.from(verifiedAt), Timestamp.from(expires), "extract-2026-09",
            ),
            "key-2",
        )
        val command = argumentCaptor<ProposeInformationRequestImportedValueCommand>().also { verify(importedValues).propose(it.capture()) }.firstValue
        assertEquals(requirementId, command.requirementId)
        assertEquals(JsonPrimitive("Recorded answer"), command.value)
        assertEquals(verifiedAt, command.verifiedAt)
        assertEquals(expires, command.expiresAt)
        assertEquals(access, command.access)
        assertEquals("key-2", command.idempotencyKey)
        assertEquals(201, proposed.status)
        val dto = proposed.entity as InformationRequestImportedValueDto
        assertEquals(JsonPrimitive("Recorded answer"), dto.value)
        assertFalse(dto.recordedByCaller)
        assertEquals(true, dto.decision?.decidedByCaller)
        assertEquals(listOf(JsonPrimitive("Another answer")), dto.discrepancies.map { it.responseValue })
        assertNoPrincipalIds(Json.encodeToString(InformationRequestImportedValueDto.serializer(), dto))

        val decided = valueResource.decide(
            requestId.toString(), value.id.toString(),
            DecideInformationRequestImportedValueRequest(InformationRequestImportedValueDecisionKind.ACCEPTED, "matches the record"), "key-3",
        )
        assertEquals(
            DecideInformationRequestImportedValueCommand(requestId, value.id, InformationRequestImportedValueDecisionKind.ACCEPTED, "matches the record", access, "key-3"),
            argumentCaptor<DecideInformationRequestImportedValueCommand>().also { verify(importedValues).decide(it.capture()) }.firstValue,
        )
        assertEquals(201, decided.status)
        assertEquals(200, valueResource.list(requestId.toString()).status)
    }

    @Test
    fun `reconciling and resolving answer outcomes and the resolved discrepancy`()
    {
        val reconciled = reconciliationResource.reconcile(requestId.toString(), "key-4")
        assertEquals(
            ReconcileInformationRequestImportedValuesCommand(requestId, access, "key-4"),
            argumentCaptor<ReconcileInformationRequestImportedValuesCommand>().also { verify(importedValues).reconcile(it.capture()) }.firstValue,
        )
        assertEquals(200, reconciled.status)
        val outcome = (reconciled.entity as Array<*>).single() as InformationRequestImportedValueReconciliationDto
        assertEquals(InformationRequestReconciliationOutcome.DIFFERS, outcome.outcome)
        assertEquals(JsonPrimitive("Another answer"), outcome.responseValue)

        val resolved = resolutionResource.resolve(
            requestId.toString(), discrepancy.id.toString(),
            ResolveInformationRequestDiscrepancyRequest(InformationRequestDiscrepancyResolution.RESPONSE_STANDS, "answer confirmed"), "key-5",
        )
        assertEquals(
            ResolveInformationRequestDiscrepancyCommand(requestId, discrepancy.id, InformationRequestDiscrepancyResolution.RESPONSE_STANDS, "answer confirmed", access, "key-5"),
            argumentCaptor<ResolveInformationRequestDiscrepancyCommand>().also { verify(importedValues).resolve(it.capture()) }.firstValue,
        )
        assertEquals(201, resolved.status)
        assertEquals(discrepancy.id, (resolved.entity as InformationRequestImportedValueDiscrepancyDto).id)
    }

    @Test
    fun `recording a generated output delegates its reference and answers it`()
    {
        val producedAt = Instant.parse("2026-09-02T08:00:00Z")
        val packageId = UUID.randomUUID()
        val recorded = outputResource.record(
            requestId.toString(),
            RecordInformationRequestGeneratedOutputRequest(packageId, "summary-output", "external://outputs/summary-1", "A".repeat(64), "application/pdf", "summary-service", Timestamp.from(producedAt)),
            "key-6",
        )
        val command = argumentCaptor<RecordInformationRequestGeneratedOutputCommand>().also { verify(outputs).record(it.capture()) }.firstValue
        assertEquals(packageId, command.packageId)
        assertEquals("A".repeat(64), command.contentHashSha256)
        assertEquals(producedAt, command.producedAt)
        assertEquals("key-6", command.idempotencyKey)
        assertEquals(201, recorded.status)
        val dto = recorded.entity as InformationRequestGeneratedOutputDto
        assertTrue(dto.recordedByCaller)
        assertNoPrincipalIds(Json.encodeToString(InformationRequestGeneratedOutputDto.serializer(), dto))
        assertEquals(200, outputResource.list(requestId.toString()).status)
    }

    @Test
    fun `absent bodies, keys, and identifiers are refused before the services, and service refusals keep their meaning`()
    {
        assertEquals(400, exchangeResource.request(requestId.toString(), null, "key").status)
        assertEquals(400, exchangeResource.request(requestId.toString(), RequestInformationRequestConnectorExchangeRequest(requirementId, "record-verification"), null).status)
        assertEquals(400, valueResource.propose("not-a-request", null, "key").status)
        assertEquals(400, valueResource.decide(requestId.toString(), "not-a-value", DecideInformationRequestImportedValueRequest(InformationRequestImportedValueDecisionKind.REJECTED, "r"), "key").status)
        assertEquals(400, reconciliationResource.reconcile(requestId.toString(), " ").status)
        assertEquals(400, resolutionResource.resolve(requestId.toString(), discrepancy.id.toString(), null, "key").status)
        assertEquals(400, outputResource.record(requestId.toString(), null, "key").status)
        verify(connectors, never()).request(any())
        verify(importedValues, never()).propose(any())
        verify(importedValues, never()).reconcile(any())
        verify(outputs, never()).record(any())

        whenever(connectors.request(any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.CONNECTOR_UNAVAILABLE, "No connector with this key is installed"),
        )
        val unavailable = exchangeResource.request(requestId.toString(), RequestInformationRequestConnectorExchangeRequest(requirementId, "other"), "key")
        assertEquals(409, unavailable.status)
        assertEquals(InformationRequestErrorCatalog.CONNECTOR_UNAVAILABLE, (unavailable.entity as ResponseError).reasonCode)
        whenever(importedValues.values(eq(requestId), any())).thenThrow(ForbiddenException("Access denied to external source records"))
        assertEquals(403, valueResource.list(requestId.toString()).status)
        whenever(outputs.outputs(eq(requestId), any())).thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found"))
        assertEquals(404, outputResource.list(requestId.toString()).status)
    }

    private fun path(type: Class<*>): String = type.getAnnotation(Path::class.java).value

    private fun assertNoPrincipalIds(serialized: String)
    {
        assertFalse(serialized.contains(caller.id.toString()), serialized)
        assertFalse(serialized.contains(value.recordedByPrincipalId.toString()), serialized)
        assertFalse(serialized.contains("PrincipalId"), serialized)
    }
}
