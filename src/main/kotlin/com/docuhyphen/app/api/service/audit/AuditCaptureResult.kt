package com.docuhyphen.app.api.service.audit

import java.util.UUID

/**
 * Outcome of [AuditRecorder.record].
 */
sealed class AuditCaptureResult
{
    /** The draft was validated and durably written to `audit_outbox` (or already existed). */
    data class Captured(val outboxId: UUID, val eventId: UUID) : AuditCaptureResult()

    /**
     * Capture failed but the event class's [AuditFailurePolicy] is [AuditFailurePolicy.DEGRADED],
     * so the failure was logged and swallowed rather than propagated.
     */
    data class Degraded(val eventId: UUID, val reason: String) : AuditCaptureResult()
}

/**
 * Thrown when [AuditRecorder.record] is given a draft that fails catalog/prohibited-field
 * validation. This is always a programming error at the call site (unknown event type or a
 * prohibited payload key), never a runtime capture failure, so it is never subject to the
 * fail-closed/degraded [AuditFailurePolicy] switch: it always throws.
 */
class AuditDraftInvalidException(errors: List<String>) :
    RuntimeException("Invalid audit event draft: ${errors.joinToString("; ")}")

/**
 * Thrown when [AuditRecorder.record] could not durably persist an outbox row and the resolved
 * [AuditFailurePolicy] for the event's category is [AuditFailurePolicy.FAIL_CLOSED]. Propagating
 * this out of a `@Transactional` business method rolls back the business mutation together with
 * the failed audit write, so no unaudited state change is committed.
 */
class AuditCaptureFailedException(message: String, cause: Throwable?) : RuntimeException(message, cause)
