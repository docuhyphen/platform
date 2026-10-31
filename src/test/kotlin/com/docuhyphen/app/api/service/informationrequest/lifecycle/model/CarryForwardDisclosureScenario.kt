package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class CarryForwardDisclosureScenario(
    val source: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val successorId: UUID,
    val successorFieldRequirementId: UUID,
    val groupId: UUID,
    val groupMemberId: UUID,
    val participantId: UUID,
    val services: InformationRequestRuntimeServices,
) {
    val contributor: PrincipalRef get() = PrincipalRef.user(source.contributorUserId)
    val attestor: PrincipalRef get() = PrincipalRef.user(source.attestorUserId)
    val groupMember: PrincipalRef get() = PrincipalRef.user(groupMemberId)
    val participant: PrincipalRef get() = PrincipalRef.participant(participantId)
    val sourceFieldRequirementId: UUID get() = answers.requirementId
}
