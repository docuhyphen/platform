package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestNoticeAttemptDto
import com.docuhyphen.app.api.model.dto.InformationRequestNoticeHistoryDto
import com.docuhyphen.app.api.model.dto.InformationRequestNoticeSequenceAllocationDto
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSequenceAllocation
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestNoticeView
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeQueryService

object InformationRequestNoticeDtoMapper
{
    fun toDto(view: InformationRequestNoticeView, allocations: List<InformationRequestNoticeSequenceAllocation>) =
        InformationRequestNoticeHistoryDto(
            noticeIntentId = view.intent.id,
            noticeKind = view.intent.noticeKind,
            partyId = view.intent.partyId,
            deliveryState = view.deliveryState,
            owedAt = view.intent.createdAt,
            noticeId = view.notice?.id,
            channel = view.notice?.channel,
            maskedEndpoint = InformationRequestNoticeQueryService.maskedEndpoint(view.notice?.recipientEndpoint),
            endpointState = view.notice?.endpointState,
            renderedSubject = view.notice?.renderedSubject,
            renderedBody = view.notice?.renderedBody,
            renderedContentHash = view.notice?.renderedContentHash,
            sourceKind = view.notice?.sourceKind,
            sourceCommunicationId = view.notice?.sourceCommunicationId,
            sourceContentHash = view.notice?.sourceContentHash,
            renderedAt = view.notice?.renderedAt,
            attempts = view.attempts.map {
                InformationRequestNoticeAttemptDto(
                    it.attemptNumber,
                    it.outcome,
                    it.failureCode,
                    it.attemptedAt
                )
            },
            sequenceAllocations = allocations.map {
                InformationRequestNoticeSequenceAllocationDto(
                    it.sequenceKey,
                    it.renderedValue
                )
            },
        )
}
