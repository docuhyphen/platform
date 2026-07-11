package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that the catalog rejects unknown event types and prohibited payload keys, and accepts
 * known event types with a clean payload.
 */
class AuditEventDraftValidatorTest
{
    @Test
    fun `accepts a draft with a known event type and clean payload`()
    {
        val draft = AuditEventDraft(
            eventTypeKey = AuditEventType.DOCUMENT_DOWNLOAD.key,
            outcome = AuditOutcome.SUCCESS,
            payload = mapOf("documentId" to "abc-123", "versionLabel" to "v3"),
        )

        val result = AuditEventDraftValidator.validate(draft)

        assertTrue(result is AuditDraftValidationResult.Valid)
        assertEquals(AuditEventType.DOCUMENT_DOWNLOAD, (result as AuditDraftValidationResult.Valid).eventType)
    }

    @Test
    fun `rejects a draft referencing an unknown event type key`()
    {
        val draft = AuditEventDraft(
            eventTypeKey = "does.not.exist",
            outcome = AuditOutcome.SUCCESS,
        )

        val result = AuditEventDraftValidator.validate(draft)

        assertTrue(result is AuditDraftValidationResult.Invalid)
        val errors = (result as AuditDraftValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("does.not.exist") })
    }

    @Test
    fun `rejects a draft whose payload contains a prohibited key`()
    {
        val draft = AuditEventDraft(
            eventTypeKey = AuditEventType.SIGN_IN_COMPLETION.key,
            outcome = AuditOutcome.SUCCESS,
            payload = mapOf("password" to "hunter2"),
        )

        val result = AuditEventDraftValidator.validate(draft)

        assertTrue(result is AuditDraftValidationResult.Invalid)
        val errors = (result as AuditDraftValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("password") })
    }

    @Test
    fun `rejects a draft whose payload contains a prohibited key regardless of casing or separators`()
    {
        val draft = AuditEventDraft(
            eventTypeKey = AuditEventType.OAUTH_CALLBACK.key,
            outcome = AuditOutcome.SUCCESS,
            payload = mapOf("Access-Token" to "abc.def.ghi"),
        )

        val result = AuditEventDraftValidator.validate(draft)

        assertTrue(result is AuditDraftValidationResult.Invalid)
    }

    @Test
    fun `every catalog entry has a unique namespaced key`()
    {
        val keys = AuditEventType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.contains(".") })
    }
}
