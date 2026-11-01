package com.docuhyphen.app.api.service.informationrequest.privacy.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

internal data class SubjectExportSnapshotFixture(val runtime: SubmissionRuntimeSqlFixture, val subjectId: UUID) {
    val principal: PrincipalRef get() = PrincipalRef.user(runtime.template.userId)
}
