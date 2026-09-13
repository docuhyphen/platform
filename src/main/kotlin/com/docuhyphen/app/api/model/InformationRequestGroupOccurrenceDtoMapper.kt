package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestGroupOccurrenceDto
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence

object InformationRequestGroupOccurrenceDtoMapper
{
    fun toDto(occurrence: InformationRequestGroupOccurrence): InformationRequestGroupOccurrenceDto =
        InformationRequestGroupOccurrenceDto(
            id = occurrence.id,
            informationRequestId = occurrence.informationRequestId,
            sourceTemplateGroupId = occurrence.sourceTemplateGroupId,
            parentOccurrenceId = occurrence.parentOccurrenceId,
            occurrenceIndex = occurrence.occurrenceIndex,
            occurrencePath = occurrence.occurrencePath,
            createdAt = occurrence.createdAt,
            removedAt = occurrence.removedAt,
        )
}
