package com.docuhyphen.app.api.service.informationrequest.acceptedfact.model

import com.docuhyphen.app.api.migration.FactSqlFixture
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.time.Duration
import java.time.Instant
import java.util.*

internal data class DisclosureFactFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val facts: FactSqlFixture,
    val answers: FieldAnswerSqlFixture,
    val laterRequestId: UUID,
    val laterRequirementId: UUID,
    val validTo: Instant = Instant.now().plus(Duration.ofDays(365)),
) {
    val requestId: UUID get() = runtime.requestId
}
