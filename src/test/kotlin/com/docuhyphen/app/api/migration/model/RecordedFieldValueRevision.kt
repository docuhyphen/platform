package com.docuhyphen.app.api.migration.model

import java.sql.Timestamp
import java.util.*

internal data class RecordedFieldValueRevision(
    val id: UUID,
    val valueSetId: UUID,
    val fieldContractId: UUID,
    val revisionNumber: Int,
    val textValue: String?,
    val isCleared: Boolean,
    val principalKind: String?,
    val principalId: UUID?,
    val recordedAt: Timestamp,
)
