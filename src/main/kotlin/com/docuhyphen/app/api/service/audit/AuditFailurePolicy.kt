package com.docuhyphen.app.api.service.audit

/**
 * How [AuditRecorder] must react when it cannot durably persist an audit outbox row.
 *
 * - [FAIL_CLOSED]: the capture failure is rethrown so it propagates out of the caller's
 *   `@Transactional` method, rolling back the business mutation together with the failed audit
 *   write. Use for regulated writes and sensitive reads where an unaudited state change is worse
 *   than a rejected request.
 * - [DEGRADED]: the capture failure is logged (with a machine-readable health signal) and
 *   swallowed; the caller's business mutation commits without a durable audit record. Use only
 *   for event classes explicitly classified as low-risk.
 *
 * See `AUDIT-ARCHITECTURE-IMPLEMENTATION.md` "Prerequisite Decisions": until compliance/legal
 * confirms the per-event-class failure policy, [AuditFailurePolicyResolver] defaults every
 * [com.docuhyphen.app.api.service.audit.catalog.AuditCategory] to [DEGRADED].
 */
enum class AuditFailurePolicy
{
    FAIL_CLOSED,
    DEGRADED,
}
