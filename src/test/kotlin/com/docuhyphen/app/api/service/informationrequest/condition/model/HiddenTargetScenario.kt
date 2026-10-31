package com.docuhyphen.app.api.service.informationrequest.condition.model

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface

internal data class HiddenTargetScenario(
    val runtime: SubmissionRuntimeSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val access: RequestAccessContext,
    val evidenceSurface: InformationRequestEvidenceSurface,
)
