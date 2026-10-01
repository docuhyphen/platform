package com.docuhyphen.app.api.resource.informationrequest.audit

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.audit.DEFAULT_AUDIT_TARGET_LIMIT
import com.docuhyphen.app.api.model.audit.MAXIMUM_AUDIT_TARGET_LIMIT
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditSearch
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

object InformationRequestAuditQuery
{
    @Suppress("LongParameterList")
    fun searchOf(
        requestId: UUID?,
        eventClass: String?,
        eventTypeKey: String?,
        actorId: String?,
        occurredAfter: String?,
        occurredBefore: String?,
        limit: Int?,
        offset: Int?,
    ): InformationRequestAuditSearch
    {
        val resolvedLimit = limit ?: DEFAULT_AUDIT_TARGET_LIMIT
        val resolvedOffset = offset ?: 0
        if (resolvedLimit !in 1..MAXIMUM_AUDIT_TARGET_LIMIT) throw InformationRequestCommandRequestException("The limit is out of range")
        if (resolvedOffset < 0) throw InformationRequestCommandRequestException("The offset is out of range")
        val after = occurredAfter?.let { instantOf(it, "occurredAfter") }
        val before = occurredBefore?.let { instantOf(it, "occurredBefore") }
        if (after != null && before != null && !before.isAfter(after))
        {
            throw InformationRequestCommandRequestException("occurredBefore must be later than occurredAfter")
        }
        return InformationRequestAuditSearch(
            requestId = requestId,
            eventClass = eventClass?.trim()?.lowercase()?.takeIf { EVENT_CLASS.matches(it) }
                ?: eventClass?.let { throw InformationRequestCommandRequestException("Unknown event class: $it") },
            eventTypeKey = eventTypeKey?.trim()?.ifBlank { null },
            actorId = actorId?.takeIf { it.isNotBlank() }?.let { InformationRequestCommandHttp.uuid(it, "actor id") },
            occurredAfter = after,
            occurredBefore = before,
            limit = resolvedLimit,
            offset = resolvedOffset,
        )
    }

    private fun instantOf(raw: String, name: String): Instant =
        try
        {
            Instant.parse(raw.trim())
        }
        catch (_: DateTimeParseException)
        {
            throw InformationRequestCommandRequestException("$name is an ISO instant")
        }

    private val EVENT_CLASS = Regex("^[a-z][a-z_]{0,63}$")
}
