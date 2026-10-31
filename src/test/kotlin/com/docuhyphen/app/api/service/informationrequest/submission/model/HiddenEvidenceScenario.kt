package com.docuhyphen.app.api.service.informationrequest.submission.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices

internal data class HiddenEvidenceScenario(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val services: InformationRequestRuntimeServices,
)
