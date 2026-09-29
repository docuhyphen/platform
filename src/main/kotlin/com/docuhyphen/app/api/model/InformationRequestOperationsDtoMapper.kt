package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestOperationsAssigneeDto
import com.docuhyphen.app.api.model.dto.InformationRequestOperationsPageDto
import com.docuhyphen.app.api.model.dto.InformationRequestOperationsRowDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsPage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsRow
import java.sql.Timestamp

object InformationRequestOperationsDtoMapper
{
    fun toDto(page: InformationRequestOperationsPage) = InformationRequestOperationsPageDto(
        items = page.rows.map(::toDto),
        total = page.total,
        limit = page.limit,
        offset = page.offset,
    )

    private fun toDto(row: InformationRequestOperationsRow) = InformationRequestOperationsRowDto(
        requestId = row.request.id,
        exchangeId = row.request.exchangeId,
        title = row.title,
        assignees = row.assignees.map {
            InformationRequestOperationsAssigneeDto(it.roleKey, it.principalKind, it.principalId, it.label)
        },
        templateVersionId = row.request.templateVersionId,
        state = row.request.state,
        gatesExchangeClosure = row.request.gatesExchangeClosure,
        createdAt = row.request.createdAt,
        issuedAt = row.request.issuedAt,
        firstViewedAt = row.request.firstViewedAt,
        startedAt = row.request.startedAt,
        ageSeconds = row.ageSeconds,
        slaStatus = row.standing.status,
        nearestDueAt = row.standing.nearestDueAt?.let(Timestamp::from),
        clockCount = row.clockCount,
        reminderCount = row.standing.reminderCount,
        noticeCounts = row.noticeCounts,
        exceptionCounts = row.exceptionCounts,
    )
}
