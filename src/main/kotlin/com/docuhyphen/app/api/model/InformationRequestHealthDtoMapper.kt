package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestHealthIndicatorDto
import com.docuhyphen.app.api.model.dto.InformationRequestHealthReportDto
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthReport
import java.sql.Timestamp

object InformationRequestHealthDtoMapper
{
    fun toDto(report: InformationRequestHealthReport): InformationRequestHealthReportDto =
        InformationRequestHealthReportDto(
            checkedAt = Timestamp.from(report.checkedAt),
            healthy = report.healthy,
            indicators = report.indicators.map { indicator ->
                InformationRequestHealthIndicatorDto(
                    key = indicator.key,
                    count = indicator.count,
                    threshold = indicator.threshold,
                    breached = indicator.breached,
                )
            },
        )
}
