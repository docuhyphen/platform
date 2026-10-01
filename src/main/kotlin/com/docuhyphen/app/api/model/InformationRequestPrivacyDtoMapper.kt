package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestItemCorrectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestPrivacyRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestPrivacyTargetDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubjectRestrictionDto
import com.docuhyphen.app.api.model.entity.InformationRequestItemCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestSubjectRestriction
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView

object InformationRequestPrivacyDtoMapper
{
    fun toDto(view: InformationRequestPrivacyRequestView) = InformationRequestPrivacyRequestDto(
        id = view.request.id,
        subjectIdentityRefId = view.request.subjectIdentityRefId,
        requestKind = view.request.requestKind,
        purposeKey = view.request.purposeKey,
        policyBasisKey = view.request.policyBasisKey,
        state = view.request.state,
        refusalCode = view.request.refusalCode,
        refusalDetail = view.request.refusalDetail,
        recordExportId = view.request.recordExportId,
        recordedByPrincipalKind = view.request.recordedByPrincipalKind,
        recordedByPrincipalId = view.request.recordedByPrincipalId,
        recordedAt = view.request.recordedAt,
        completedAt = view.request.completedAt,
        targets = view.targets.map { InformationRequestPrivacyTargetDto(it.requestId, it.outcome, it.reasonCode, it.disposalClaimId) },
        restriction = view.restriction?.let(::toDto),
        correction = view.correction?.let(::toDto),
    )

    fun toDto(restriction: InformationRequestSubjectRestriction) = InformationRequestSubjectRestrictionDto(
        id = restriction.id,
        subjectIdentityRefId = restriction.subjectIdentityRefId,
        privacyRequestId = restriction.privacyRequestId,
        restrictedAt = restriction.restrictedAt,
        liftedAt = restriction.liftedAt,
        liftReasonCode = restriction.liftReasonCode,
    )

    private fun toDto(correction: InformationRequestItemCorrection) = InformationRequestItemCorrectionDto(
        id = correction.id,
        requestId = correction.informationRequestId,
        packageId = correction.packageId,
        submissionItemId = correction.submissionItemId,
        reasonCode = correction.reasonCode,
        recordedAt = correction.recordedAt,
    )
}
