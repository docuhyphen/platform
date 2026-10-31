package com.docuhyphen.app.api.service.informationrequest.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestAuditPayloadPolicyTest
{
    @Test
    fun `export integrity verification is a visible boolean audit fact`() {
        val projection = InformationRequestAuditPayloadPolicy.projectionOf(mapOf("verified" to "true")) { true }
        assertEquals(mapOf("verified" to "true"), projection.payload)
        assertEquals(0, projection.withheldKeyCount)
    }
    @Test
    fun `external source records show their identifiers and keys but never a value or lookup reference`()
    {
        val payload = mapOf(
            "connectorExchangeId" to "exchange",
            "connectorKey" to "record-verification",
            "importedValueId" to "value",
            "resultKey" to "recorded-status",
            "sourceKind" to "MANUAL",
            "importedValueDecision" to "ACCEPTED",
            "discrepancyId" to "discrepancy",
            "discrepancyCount" to "2",
            "discrepancyResolution" to "RESPONSE_STANDS",
            "generatedOutputId" to "output",
            "outputKey" to "summary-output",
            "canonicalValue" to "\"a recorded value\"",
            "lookupReference" to "record-1",
        )

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(payload) { true }

        assertEquals(payload - "canonicalValue" - "lookupReference", projection.payload)
        assertEquals(2, projection.withheldKeyCount)
    }

    @Test
    fun `a connector attempt or outcome shows its attempt count, state, next attempt and failure code but never its references`() {
        val payload = mapOf(
            "connectorExchangeId" to "exchange",
            "connectorKey" to "record-verification",
            "requirementId" to "requirement",
            "state" to "FAILED",
            "attemptCount" to "3",
            "nextAttemptAt" to "2026-10-25T10:15:30Z",
            "failureCode" to "record.not_found",
            "externalReference" to "external-1",
            "lookupReference" to "record-1",
        )

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(payload) { true }

        assertEquals(payload - "externalReference" - "lookupReference", projection.payload)
        assertEquals(2, projection.withheldKeyCount)
    }

    @Test
    fun `condition effects on a saved response are shown in the audit trail`() {
        val payload = mapOf(
            "transitionId" to "transition",
            "conditionEffectCount" to "1",
            "conditionEffects" to """[{"ruleKey":"when-detail-applies","effect":"ARCHIVED"}]""",
        )

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(payload) { true }

        assertEquals(payload, projection.payload)
        assertEquals(0, projection.withheldKeyCount)
    }

    @Test
    fun `a follow-up or replacement is named only when its request may be viewed and is otherwise withheld and counted`() {
        val visible = UUID.randomUUID()
        val hidden = UUID.randomUUID()
        val payload = mapOf(
            "transitionId" to "transition",
            "lineageKind" to "SUPERSEDING",
            "successorRequestId" to visible.toString(),
            "supersededByRequestId" to hidden.toString(),
            "lookupReference" to "record-1",
        )
        val asked = mutableListOf<UUID>()

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(payload) { requestId ->
            asked += requestId
            requestId == visible
        }

        assertEquals(
            mapOf(
                "transitionId" to "transition",
                "lineageKind" to "SUPERSEDING",
                "successorRequestId" to visible.toString(),
            ),
            projection.payload,
        )
        assertEquals(2, projection.withheldKeyCount)
        assertEquals(setOf(visible, hidden), asked.toSet())
    }

    @Test
    fun `a related request reference that is not a request id is withheld without asking about it`() {
        val asked = mutableListOf<UUID>()

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(
            mapOf("mutation" to "CREATE_SUCCESSOR", "successorRequestId" to "not-a-request-id"),
        ) { requestId ->
            asked += requestId
            true
        }

        assertEquals(mapOf("mutation" to "CREATE_SUCCESSOR"), projection.payload)
        assertEquals(1, projection.withheldKeyCount)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a payload naming no related request asks about none`() {
        val asked = mutableListOf<UUID>()

        val projection = InformationRequestAuditPayloadPolicy.projectionOf(
            mapOf("requirementId" to UUID.randomUUID().toString(), "partyId" to UUID.randomUUID().toString()),
        ) { requestId ->
            asked += requestId
            false
        }

        assertEquals(setOf("requirementId", "partyId"), projection.payload.keys)
        assertEquals(0, projection.withheldKeyCount)
        assertTrue(asked.isEmpty())
    }
}
