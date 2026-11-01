package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditDraftValidationResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType

object AuditEventDraftValidator
{
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
