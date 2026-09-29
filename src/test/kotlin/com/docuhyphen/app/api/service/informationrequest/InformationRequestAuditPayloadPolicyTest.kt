package com.docuhyphen.app.api.service.informationrequest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InformationRequestAuditPayloadPolicyTest
{
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

        assertEquals(payload - "canonicalValue" - "lookupReference", InformationRequestAuditPayloadPolicy.allowedPayload(payload))
        assertEquals(2, InformationRequestAuditPayloadPolicy.withheldKeyCount(payload))
    }
}
