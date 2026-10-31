package com.docuhyphen.app.api.service.informationrequest.notice.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class ReminderBatchScenario(
    val fixture: SubmissionRuntimeSqlFixture,
    val requestIds: List<UUID>,
    val firstBatch: List<UUID>,
    val secondBatch: List<UUID>,
)
