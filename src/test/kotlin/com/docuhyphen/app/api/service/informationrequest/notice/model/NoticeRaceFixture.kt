package com.docuhyphen.app.api.service.informationrequest.notice.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.time.Instant
import java.util.*

internal data class NoticeRaceFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val clockId: UUID,
    val start: Instant,
    val ownerAccess: RequestAccessContext,
) {
    val requestId: UUID get() = runtime.requestId
    val owner: PrincipalRef get() = ownerAccess.principal
}
