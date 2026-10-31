package com.docuhyphen.app.api.resource.audit.handler

import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import jakarta.ws.rs.core.Response
import java.util.*

class AuditLegalHoldRequestHandler(private val holds: AuditLegalHoldService) {
    fun place(organizationId: UUID?, principal: UUID, body: AuditLegalHoldCreateRequestDto): Response {
        val hold = holds.placeHold(
            organizationId,
            body.resourceType,
            body.resourceId,
            body.reason,
            body.caseReference,
            principal
        )
        return Response.ok(AuditGovernanceDtoMapper.toDto(hold)).build()
    }

    fun list(organizationId: UUID?): Response =
        Response.ok(holds.listActiveHolds(organizationId).map(AuditGovernanceDtoMapper::toDto)).build()

    fun release(organizationId: UUID?, holdId: UUID, principal: UUID): Response =
        Response.ok(AuditGovernanceDtoMapper.toDto(holds.releaseHold(holdId, organizationId, principal))).build()
}
