package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import java.util.UUID

/**
 * Typed input for a prospective audit event, prior to catalog/prohibited-field validation.
 *
 * [eventTypeKey] is a plain string (the [com.docuhyphen.app.api.service.audit.catalog.AuditEventType.key])
 * rather than the enum itself: callers assemble a draft from request/service context first, and
 * [AuditEventDraftValidator] is the single place that resolves it against the catalog and rejects
 * anything unknown. This keeps validation centralized instead of trusting every call site to only
 * ever construct a draft from a valid enum reference.
 *
 * [payload] is a flat map of structured, already-classified fields (e.g. changed-field names,
 * resource labels). It must never contain secrets, tokens, credentials, raw document/content
 * bytes, or unrestricted Field Values; see [AuditEventDraftValidator.PROHIBITED_PAYLOAD_KEYS].
 *
 * [eventId] stably identifies this prospective event across retries of the same logical
 * occurrence; defaults to a fresh random ID when the caller has no natural retry key. Callers
 * that want the outbox write itself to be retry-safe (e.g. a business operation that may be
 * re-invoked with the same effect) should pass the same [eventId] and/or [idempotencyKey] on
 * every retry so [com.docuhyphen.app.api.service.audit.AuditRecorder] persists at most one
 * outbox row for the occurrence. [idempotencyKey] defaults to [eventId] when not supplied.
 *
 * [actorKind] is the explicit actor classification: pass it whenever the call site knows whether
 * the actor is a human, an application credential, a public-link recipient, or the workflow
 * engine. Left `null`, [LedgerProcessor.resolveActorKind] falls back to guessing `HUMAN`/`SYSTEM`
 * from [actorId] presence when a caller does not provide an explicit classification.
 *
 * [targetLabel]/[organizationLabel] are the human-readable counterparts to [targetId]/
 * [organizationId] - a document name, an Exchange name, a workflow definition name, a person's
 * name/email, etc, captured once at event time. The architecture has always called for
 * "denormalized IDs+labels" (never a foreign key to the mutable row being described), but only
 * the ID half made it into the original schema; these fields close that gap. Pass whatever
 * human-meaningful string the call site already has in hand - there is no lookup service that
 * resolves these after the fact, both to avoid a circular dependency back into every business
 * service from the audit layer and because a label resolved later would show the entity's
 * *current* name/state rather than what it was at the time of the event. [actorLabel] is normally
 * left `null` and resolved by [AuditRecorder] itself from the authenticated caller (name/email);
 * only set it explicitly for a non-standard actor the recorder cannot see (for example a
 * no-auth/public-link caller identified by something other than an `AppUser` row).
 */
sealed interface AuditOwnerScope
{
    data object Platform : AuditOwnerScope
    data class Organization(val organizationId: UUID) : AuditOwnerScope
}

data class AuditEventDraft(
    val owner: AuditOwnerScope,
    val eventTypeKey: String,
    val outcome: AuditOutcome,
    val actorId: UUID? = null,
    val actorRole: String? = null,
    val actorKind: AuditActorKind? = null,
    val actorLabel: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val organizationLabel: String? = null,
    val sessionId: String? = null,
    val reason: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val eventId: UUID = UUID.randomUUID(),
    val idempotencyKey: String? = null,
    val businessTransactionId: String? = null,
)
