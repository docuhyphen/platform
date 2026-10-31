package com.docuhyphen.app.api.service.informationrequest.privacy.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class SubjectExportScenario(
    val runtime: SubmissionRuntimeSqlFixture,
    val subjectId: UUID,
    val siblingRequestId: UUID,
    val siblingManagerId: UUID,
    val privacyRequestId: UUID,
    val exportId: UUID,
) {
    val sourceRequestId: UUID get() = runtime.requestId
    val ownerUserId: UUID get() = runtime.template.userId
}
