package com.docuhyphen.app.api.service.informationrequest.privacy.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class PrivacyDeletionReminderScenario(
    val fixture: SubmissionRuntimeSqlFixture,
    val subjectId: UUID,
    val finishedSubjectRequestId: UUID,
    val openSubjectRequestId: UUID,
    val remindedRequestIds: List<UUID>,
)
