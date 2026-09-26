package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestAcceptedFactDto
import com.docuhyphen.app.api.model.dto.InformationRequestAcceptedFactOfferDto
import com.docuhyphen.app.api.model.dto.InformationRequestBusinessDecisionDto
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecision
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactOffer
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import kotlinx.serialization.json.Json

object InformationRequestAcceptedFactDtoMapper
{
    fun toDto(view: InformationRequestAcceptedFactView): InformationRequestAcceptedFactDto
    {
        val fact = view.fact
        return InformationRequestAcceptedFactDto(
            id = fact.id,
            subjectIdentityRefId = fact.subjectIdentityRefId,
            purposeKey = fact.purposeKey,
            fieldDefinitionId = fact.fieldDefinitionId,
            valueType = fact.valueType,
            value = Json.parseToJsonElement(fact.canonicalValue),
            sourceInformationRequestId = fact.sourceInformationRequestId,
            sourcePackageId = fact.sourcePackageId,
            sourceSubmissionItemId = fact.sourceSubmissionItemId,
            sourceRequirementId = fact.sourceRequirementId,
            sourceReviewId = fact.sourceReviewId,
            visibility = fact.visibility,
            confidence = fact.confidence,
            validFrom = fact.validFrom,
            validTo = fact.validTo,
            expiresAt = fact.expiresAt,
            supersedesFactId = fact.supersedesFactId,
            supersededByFactId = view.supersededByFactId,
            conflictState = fact.conflictState,
            conflictingFactId = fact.conflictingFactId,
            promotedAt = fact.promotedAt,
            revoked = view.revocation != null,
            revokedAt = view.revocation?.revokedAt,
            revocationReasonCode = view.revocation?.reasonCode,
            freshness = view.freshness,
        )
    }

    fun toDto(offer: InformationRequestAcceptedFactOffer): InformationRequestAcceptedFactOfferDto =
        InformationRequestAcceptedFactOfferDto(
            requirementId = offer.requirementId,
            requirementKey = offer.requirementKey,
            fact = toDto(offer.fact),
            reconfirmationRequired = offer.reconfirmationRequired,
        )

    fun toDto(decision: InformationRequestBusinessDecision, caller: PrincipalRef): InformationRequestBusinessDecisionDto =
        InformationRequestBusinessDecisionDto(
            id = decision.id,
            informationRequestId = decision.informationRequestId,
            owningProcessKey = decision.owningProcessKey,
            outcomeCode = decision.outcomeCode,
            reasonReference = decision.reasonReference,
            externalReference = decision.externalReference,
            kind = decision.kind,
            priorDecisionId = decision.priorDecisionId,
            decisionRevision = decision.decisionRevision,
            decidedAt = decision.decidedAt,
            recordedAt = decision.recordedAt,
            recordedByCaller = decision.recordedByPrincipalKind == caller.kind && decision.recordedByPrincipalId == caller.id,
        )
}
