package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAmendmentChangeDto
import com.docuhyphen.app.api.model.dto.InformationRequestAmendmentDto
import com.docuhyphen.app.api.model.dto.InformationRequestAmendmentResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestNoticeIntentDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReadableAmendment
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

object InformationRequestAmendmentDtoMapper
{
    fun toDto(readable: InformationRequestReadableAmendment, caller: PrincipalRef): InformationRequestAmendmentDto
    {
        val amendment = readable.view.amendment
        val visibleChanges = readable.view.changes.filter { it.templateRequirementId in readable.visibleTemplateRequirementIds }
        return InformationRequestAmendmentDto(
            id = amendment.id,
            amendmentNumber = amendment.amendmentNumber,
            fromTemplateVersionId = amendment.fromTemplateVersionId,
            toTemplateVersionId = amendment.toTemplateVersionId,
            reasonCode = amendment.reasonCode,
            amendedAt = amendment.amendedAt,
            amendedByCaller = amendment.amendedByPrincipalKind == caller.kind && amendment.amendedByPrincipalId == caller.id,
            changes = visibleChanges.map {
                InformationRequestAmendmentChangeDto(it.requirementKey, it.changeKind, it.reconfirmationRequired)
            },
            undisclosedChangeCount = readable.view.changes.size - visibleChanges.size,
            notices = readable.view.notices
                .filter { it.partyId in readable.visibleNoticePartyIds }
                .map { InformationRequestNoticeIntentDto(it.id, it.partyId, it.noticeKind, it.deliveryState, it.createdAt) },
        )
    }

    fun toDto(
        result: InformationRequestAmendmentResult,
        readable: InformationRequestReadableAmendment,
        caller: PrincipalRef,
    ): InformationRequestAmendmentResultDto = InformationRequestAmendmentResultDto(
        requestState = result.request.state,
        requestETag = result.requestETag,
        amendment = toDto(readable, caller),
    )
}
