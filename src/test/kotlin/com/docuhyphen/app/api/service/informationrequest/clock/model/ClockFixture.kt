package com.docuhyphen.app.api.service.informationrequest.clock.model

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class ClockFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val policyId: UUID,
    val versionId: UUID,
) {
    fun clocks(connection: java.sql.Connection) =
        ClockSqlFixture(connection, runtime, policyId, versionId, seed = false)
}
