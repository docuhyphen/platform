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
    fun `evidence content access is recorded as its own Information Request event`()
    {
        listOf("information_request.evidence.download", "information_request.evidence.preview").forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `an evidence malware scan result is recorded as its own Information Request event`()
    {
        val eventType = AuditEventType.findByKey("information_request.evidence.scan")
        assertEquals("information_request.evidence.scan", eventType?.key)
        assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
    }

    @Test
    fun `closing, withdrawing a submission, creating a successor, and scheduling a follow-up are their own events`()
    {
        listOf(
            "information_request.request.close",
            "information_request.submission.withdraw",
            "information_request.request.successor",
            "information_request.request.follow_up",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `review, correction, accepted fact, and business decision records are their own events`()
    {
        listOf(
            "information_request.review.start",
            "information_request.review.assign",
            "information_request.review.draft",
            "information_request.review.finding",
            "information_request.review.comment",
            "information_request.review.settle",
            "information_request.request.correction",
            "information_request.fact.promote",
            "information_request.fact.revoke",
            "information_request.fact.recertify",
            "information_request.decision.record",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `first view, start, expiry, overdue, completion gate, clock, notice, and privacy records are their own events`()
    {
        listOf(
            "information_request.request.view",
            "information_request.request.start",
            "information_request.request.expire",
            "information_request.request.overdue",
            "information_request.request.completion_gate",
            "information_request.clock.start",
            "information_request.clock.pause",
            "information_request.clock.resume",
            "information_request.clock.extend",
            "information_request.clock.remind",
            "information_request.clock.escalate",
            "information_request.clock_policy.publish",
            "information_request.notice.render",
            "information_request.notice.deliver",
            "information_request.privacy.record",
            "information_request.privacy.complete",
            "information_request.privacy.refuse",
            "information_request.item.correct",
            "information_request.request.export_read",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `preservation hold scope, retention schedule, and disposal records are governance events`()
    {
        listOf(
            "audit.legal_hold.scope_changed",
            "record.retention_schedule.updated",
            "record.disposal.claimed",
            "record.disposal.denied",
            "record.disposal.object_deleted",
            "record.disposal.finalized",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.AUDIT_GOVERNANCE, eventType?.category)
        }
    }

    @Test
    fun `assigning and revoking a party and sending a reminder are their own events`()
    {
        listOf(
            "information_request.party.assign",
            "information_request.party.revoke",
            "information_request.request.remind",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `requesting an external source, recording and deciding an imported value, and recording a generated output are their own events`()
    {
        listOf(
            "information_request.external.request",
            "information_request.external.record",
            "information_request.external.decide",
            "information_request.output.record",
        ).forEach { key ->
            val eventType = AuditEventType.findByKey(key)
            assertEquals(key, eventType?.key)
            assertEquals(AuditCategory.INFORMATION_REQUEST, eventType?.category)
        }
    }

    @Test
    fun `reading the Information Request health report is a platform event`()
    {
        val eventType = AuditEventType.findByKey("platform.information_request_health.view")
        assertEquals(AuditEventType.PLATFORM_INFORMATION_REQUEST_HEALTH_VIEW, eventType)
        assertEquals(AuditCategory.PLATFORM, eventType?.category)
    }

    @Test
    fun `catalog version reflects the added runtime request vocabulary`()
    {
        assertEquals(29, AuditEventType.CATALOG_VERSION)
    }

    @Test
    fun `every event type key is unique`()
    {
        val keys = AuditEventType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate AuditEventType keys detected")
    }
}

