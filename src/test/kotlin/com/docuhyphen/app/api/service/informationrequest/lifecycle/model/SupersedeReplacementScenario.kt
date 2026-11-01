package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class SupersedeReplacementScenario(
    val fixture: SubmissionRuntimeSqlFixture,
    val services: InformationRequestRuntimeServices,
    val manager: PrincipalRef,
) {
    val sourceId: UUID get() = fixture.requestId
    val owner: PrincipalRef get() = PrincipalRef.user(fixture.template.userId)
}

internal data class SupersedeSourceWrites(val aggregateRevision: Int, val receipts: Int)
