package com.docuhyphen.app.api.service.audit.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the runtime Information Request event vocabulary added alongside the Template
 * configuration events already in the catalog, and the resulting catalog version.
 */
class AuditEventTypeTest
{
    @Test
    fun `runtime request event types are namespaced under information_request and category INFORMATION_REQUEST`()
    {
        val runtimeEventTypes = listOf(
            AuditEventType.INFORMATION_REQUEST_CREATE to "information_request.request.create",
            AuditEventType.INFORMATION_REQUEST_ISSUE to "information_request.request.issue",
            AuditEventType.INFORMATION_REQUEST_SUBMIT to "information_request.request.submit",
            AuditEventType.INFORMATION_REQUEST_AMEND to "information_request.request.amend",
            AuditEventType.INFORMATION_REQUEST_CANCEL to "information_request.request.cancel",
            AuditEventType.INFORMATION_REQUEST_SUPERSEDE to "information_request.request.supersede",
            AuditEventType.INFORMATION_REQUEST_EXPORT to "information_request.request.export",
            AuditEventType.INFORMATION_REQUEST_PARTY_REASSIGN to "information_request.party.reassign",
            AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND to "information_request.requirement.respond",
            AuditEventType.INFORMATION_REQUEST_REQUIREMENT_ATTEST to "information_request.requirement.attest",
            AuditEventType.INFORMATION_REQUEST_REQUIREMENT_REVIEW to "information_request.requirement.review",
            AuditEventType.INFORMATION_REQUEST_EVIDENCE_ADMINISTER to "information_request.evidence.administer",
        )

        runtimeEventTypes.forEach { (eventType, key) ->
            assertEquals(key, eventType.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType.category)
            assertTrue(key.startsWith("information_request."), "key=$key must share the Information Request namespace")
            assertEquals(eventType, AuditEventType.findByKey(key))
        }
    }

    @Test
    fun `catalog version reflects the added runtime request vocabulary`()
    {
        assertEquals(20, AuditEventType.CATALOG_VERSION)
    }

    @Test
    fun `every event type key is unique`()
    {
        val keys = AuditEventType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate AuditEventType keys detected")
    }
}

