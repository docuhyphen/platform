package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import java.util.*

internal data class RecurringSupplementalScenario(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val subjectId: UUID,
    val next: NextSubmissionVersion,
)
