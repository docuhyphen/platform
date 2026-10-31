package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class FollowUpPolicyScenario(
    val fixture: SubmissionRuntimeSqlFixture,
    val services: InformationRequestRuntimeServices,
    val manager: PrincipalRef,
    val next: NextSubmissionVersion?,
) {
    val sourceId: UUID get() = fixture.requestId
}
