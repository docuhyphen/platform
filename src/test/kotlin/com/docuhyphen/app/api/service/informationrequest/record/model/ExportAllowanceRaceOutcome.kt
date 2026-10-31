package com.docuhyphen.app.api.service.informationrequest.record.model

import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import java.util.*

internal data class ExportAllowanceRaceOutcome(
    val secondWaitedBehindFirst: Boolean,
    val secondCountedWhileFirstHeld: Boolean,
    val first: Result<InformationRequestRecordExportView>,
    val second: Result<InformationRequestRecordExportView>,
) {
    val made: Int get() = listOf(first, second).count { it.isSuccess }
}

internal data class RecordExportTarget(
    val requestId: UUID,
    val ownerId: UUID,
    val actorId: UUID,
)
