package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.audit.AuditProjectionCursor
import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

internal fun parseAuditInstant(raw: String, fieldName: String): Instant = try
{
    Instant.parse(raw)
}
catch (_: DateTimeParseException)
{
    throw IllegalArgumentException("$fieldName must be an ISO-8601 instant")
}

internal fun parseAuditCursor(cursorOccurredAt: String?, cursorEventId: String?): AuditProjectionCursor?
{
    if (cursorOccurredAt.isNullOrBlank() || cursorEventId.isNullOrBlank())
    {
        return null
    }
    return AuditProjectionCursor(
        occurredAt = parseAuditInstant(cursorOccurredAt, "cursorOccurredAt"),
        eventId = UUID.fromString(cursorEventId),
    )
}

internal fun parseAuditCategories(raw: String?): Set<AuditCategory>
{
    if (raw.isNullOrBlank())
    {
        return emptySet()
    }
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { AuditCategory.valueOf(it.uppercase()) }
        .toSet()
}

internal fun parseAuditCategory(raw: String): AuditCategory =
    runCatching { AuditCategory.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown audit category: $raw") }

internal fun parseAuditIdentityTreatment(raw: String): AuditIdentityTreatment =
    runCatching { AuditIdentityTreatment.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown identity treatment: $raw") }

internal fun clampAuditPageLimit(limit: Int): Int = limit.coerceIn(1, 200)
