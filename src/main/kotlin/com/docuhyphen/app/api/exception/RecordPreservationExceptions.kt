package com.docuhyphen.app.api.exception

class RecordPreservationException(
    val reasonCode: String,
    message: String,
) : RuntimeException(message)

class RecordPreservationNotFoundException(message: String) : RuntimeException(message)

class RecordPreservationRequestException(message: String) : RuntimeException(message)

object RecordPreservationErrorCatalog
{
    const val HOLD_RELEASED = "RECORD_PRESERVATION_HOLD_RELEASED"

    const val HOLD_SCOPE_UNCHANGED = "RECORD_PRESERVATION_HOLD_SCOPE_UNCHANGED"

    const val DISPOSAL_IN_PROGRESS = "RECORD_PRESERVATION_DISPOSAL_IN_PROGRESS"

    const val DISPOSAL_STATE_INVALID = "RECORD_PRESERVATION_DISPOSAL_STATE_INVALID"
}
