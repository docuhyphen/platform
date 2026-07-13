package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.AuditEngagement
import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import java.time.Instant
import java.util.UUID

/** Audit engagement request/entity <-> DTO mapping, per this codebase's `toDto` convention. */
object AuditEngagementDtoMapper
{
    fun toDto(engagement: AuditEngagement): AuditEngagementDto = AuditEngagementDto(
        engagementId = engagement.id.toString(),
        organizationId = engagement.organizationId?.toString(),
        resourceType = engagement.resourceType,
        resourceId = engagement.resourceId,
        auditorUserId = engagement.auditorUserId?.toString(),
        principalGroupId = engagement.principalGroupId?.toString(),
        categories = parseCategoriesCsv(engagement.categoriesCsv),
        sensitivityLevel = engagement.sensitivityLevel.name,
        startsAt = engagement.startsAt.toInstant().toString(),
        expiresAt = engagement.expiresAt.toInstant().toString(),
        purpose = engagement.purpose,
        caseReference = engagement.caseReference,
        legalBasis = engagement.legalBasis,
        exportPermitted = engagement.exportPermitted,
        maxQueryRangeDays = engagement.maxQueryRangeDays,
        downloadLimit = engagement.downloadLimit,
        status = engagement.status.name,
        requestedByUserId = engagement.requestedByUserId.toString(),
        requestedAt = engagement.requestedAt.toInstant().toString(),
        approvedByUserId = engagement.approvedByUserId?.toString(),
        approvedAt = engagement.approvedAt?.toInstant()?.toString(),
        revokedByUserId = engagement.revokedByUserId?.toString(),
        revokedAt = engagement.revokedAt?.toInstant()?.toString(),
    )

    fun toRequest(
        organizationId: UUID?,
        dto: AuditEngagementCreateRequestDto,
    ): AuditEngagementService.AuditEngagementRequest = AuditEngagementService.AuditEngagementRequest(
        organizationId = organizationId,
        resourceType = dto.resourceType,
        resourceId = dto.resourceId,
        auditorUserId = dto.auditorUserId?.let(::parseUuid),
        principalGroupId = dto.principalGroupId?.let(::parseUuid),
        categories = dto.categories.map(::parseCategory).toSet(),
        sensitivityLevel = parseSensitivity(dto.sensitivityLevel),
        startsAt = parseInstant(dto.startsAt),
        expiresAt = parseInstant(dto.expiresAt),
        purpose = dto.purpose,
        caseReference = dto.caseReference,
        legalBasis = dto.legalBasis,
        exportPermitted = dto.exportPermitted,
        maxQueryRangeDays = dto.maxQueryRangeDays,
        downloadLimit = dto.downloadLimit,
    )

    private fun parseCategoriesCsv(csv: String): List<String> =
        csv.split(",").mapNotNull { it.trim().takeIf(String::isNotBlank) }

    private fun parseCategory(raw: String): AuditCategory = runCatching { AuditCategory.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown audit category: $raw") }

    private fun parseSensitivity(raw: String): AuditEngagementSensitivity =
        runCatching { AuditEngagementSensitivity.valueOf(raw.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown sensitivity level: $raw") }

    private fun parseInstant(raw: String): Instant = runCatching { Instant.parse(raw) }
        .getOrElse { throw IllegalArgumentException("Invalid timestamp: $raw") }

    private fun parseUuid(raw: String): UUID = runCatching { UUID.fromString(raw) }
        .getOrElse { throw IllegalArgumentException("Invalid identifier: $raw") }
}
