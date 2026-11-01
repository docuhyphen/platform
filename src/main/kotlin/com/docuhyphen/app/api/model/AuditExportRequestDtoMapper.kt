package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.audit.export.AuditExportRequest
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

object AuditExportRequestDtoMapper
{
    fun toRequest(organizationId: UUID?, body: AuditExportCreateRequestDto): AuditExportRequest =
        AuditExportRequest(
            organizationId = organizationId,
            categories = body.categories.map { AuditCategory.valueOf(it.uppercase()) }.toSet(),
            occurredAfter = parseInstant(body.occurredAfter, "occurredAfter"),
            occurredBefore = parseInstant(body.occurredBefore, "occurredBefore"),
            purpose = body.purpose,
            caseReference = body.caseReference,
            legalBasis = body.legalBasis,
            downloadLimit = body.downloadLimit,
        )

    private fun parseInstant(raw: String, fieldName: String): Instant = try
    {
        Instant.parse(raw)
    }
    catch (_: DateTimeParseException)
    {
        throw IllegalArgumentException("$fieldName must be an ISO-8601 instant")
    }
}
