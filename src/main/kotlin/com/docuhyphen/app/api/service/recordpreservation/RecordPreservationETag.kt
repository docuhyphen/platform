package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.command.RevisionETag
import java.nio.charset.StandardCharsets
import java.util.*

object RecordPreservationETag {
    fun holdOf(hold: RecordPreservationHold): String = RevisionETag.of(hold.id, hold.holdRevision)

    fun scheduleOf(owner: RecordOwnerRef, resourceType: String, versionNumber: Int): String =
        RevisionETag.of(scheduleSeriesId(owner, resourceType), versionNumber.toLong())

    private fun scheduleSeriesId(owner: RecordOwnerRef, resourceType: String): UUID =
        UUID.nameUUIDFromBytes(
            "record_retention_schedule|${owner.kind}|${owner.id}|$resourceType".toByteArray(StandardCharsets.UTF_8),
        )
}
