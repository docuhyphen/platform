package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class ConformanceRequest(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
) {
    val requestId: UUID get() = runtime.requestId
}
