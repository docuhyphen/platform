package com.docuhyphen.app.api.service.informationrequest.response.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture

internal data class GivenOnceCorrectionScenario(
    val fixture: ReviewFixture,
    val answers: FieldAnswerSqlFixture,
    val services: InformationRequestRuntimeServices,
)
