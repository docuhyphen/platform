package com.docuhyphen.app.api.service.informationrequest.acceptedfact.model

import com.docuhyphen.app.api.migration.FactSqlFixture
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class RecertificationLaterRequest(
    val exchangeId: UUID,
    val requestId: UUID,
    val requirementId: UUID,
    val revisionId: UUID,
)

internal data class RecertificationFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val facts: FactSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val laterRequestId: UUID,
    val laterRequirementId: UUID,
    val submittedEvidenceId: UUID,
) {
    val requestId: UUID get() = runtime.requestId
}
