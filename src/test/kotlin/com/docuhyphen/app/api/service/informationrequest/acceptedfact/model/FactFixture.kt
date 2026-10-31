package com.docuhyphen.app.api.service.informationrequest.acceptedfact.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class FactFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val facts: com.docuhyphen.app.api.migration.FactSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val laterRequestId: UUID,
    val laterFieldRequirementId: UUID,
) {
    val requestId: UUID get() = runtime.requestId
}
