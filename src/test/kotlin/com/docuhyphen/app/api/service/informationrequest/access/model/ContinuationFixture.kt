package com.docuhyphen.app.api.service.informationrequest.access.model

import com.docuhyphen.app.api.migration.FactSqlFixture
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class ContinuationFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val facts: FactSqlFixture,
    val clockPolicyVersionId: UUID,
) {
    val requestId: UUID get() = runtime.requestId
}
