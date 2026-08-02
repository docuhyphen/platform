package com.docuhyphen.app.api.resource.audit

import java.time.Instant
import java.time.format.DateTimeParseException

internal fun parseAuditInstant(raw: String, fieldName: String): Instant = try
{
    Instant.parse(raw)
}
catch (_: DateTimeParseException)
{
    throw IllegalArgumentException("$fieldName must be an ISO-8601 instant")
}
