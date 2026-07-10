package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditEventType

/**
 * Result of validating an [AuditEventDraft] against the catalog and the prohibited-field list.
 */
sealed class AuditDraftValidationResult
{
    data class Valid(val eventType: AuditEventType) : AuditDraftValidationResult()

    data class Invalid(val errors: List<String>) : AuditDraftValidationResult()
}

/**
 * Validates [AuditEventDraft] instances before they are allowed to become a durable audit intent.
 *
 * Two rejection classes, per Phase 0 of `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`:
 *  - The draft references an [AuditEventType.key] that is not in the catalog.
 *  - The draft's payload contains a prohibited key (passwords, OTPs, tokens, secrets, keys, raw
 *    content, or unrestricted Field Values).
 *
 * This is a pure, stateless validator (no persistence, no capture). Phase 1 wires it into
 * `AuditRecorder` before anything is written to the outbox.
 */
object AuditEventDraftValidator
{
    /**
     * Payload keys (case-insensitive, underscores/dashes ignored) that must never appear in an
     * audit event payload, regardless of event type. This is a denylist of field *names*, not a
     * content scanner: services must classify and shape their payload before handing it to the
     * recorder, they must not rely on this list to redact free-form text.
     */
    val PROHIBITED_PAYLOAD_KEYS: Set<String> = setOf(
        "password",
        "passwordhash",
        "otp",
        "otpcode",
        "token",
        "accesstoken",
        "refreshtoken",
        "idtoken",
        "secret",
        "clientsecret",
        "apikey",
        "privatekey",
        "signingkey",
        "encryptionkey",
        "ssn",
        "creditcardnumber",
        "cvv",
        "rawcontent",
        "documentcontent",
        "fieldvalue",
        "fieldvalues",
    )

    private fun normalize(key: String): String = key.lowercase().replace(Regex("[_-]"), "")

    private val normalizedProhibitedKeys: Set<String> = PROHIBITED_PAYLOAD_KEYS.map(::normalize).toSet()

    fun validate(draft: AuditEventDraft): AuditDraftValidationResult
    {
        val errors = mutableListOf<String>()

        val eventType = AuditEventType.findByKey(draft.eventTypeKey)
        if (eventType == null)
        {
            errors.add("Unknown audit event type key: ${draft.eventTypeKey}")
        }

        val prohibitedKeysFound = draft.payload.keys.filter { normalize(it) in normalizedProhibitedKeys }
        if (prohibitedKeysFound.isNotEmpty())
        {
            errors.add("Payload contains prohibited key(s): ${prohibitedKeysFound.sorted().joinToString(", ")}")
        }

        if (errors.isNotEmpty())
        {
            return AuditDraftValidationResult.Invalid(errors)
        }

        return AuditDraftValidationResult.Valid(eventType!!)
    }
}
