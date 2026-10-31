package com.docuhyphen.app.api.model.audit.export

import java.util.*

data class StreamIntegrityReport(
    val streamId: String,
    val chainValid: Boolean,
    val chainNote: String,
    val segmentsChecked: Int,
    val segmentsValid: Int,
    val segmentFailureNotes: List<String>,
)

data class OrganizationIntegrityReport(
    val organizationId: UUID?,
    val platformOnly: Boolean,
    val streams: List<StreamIntegrityReport>,
)
{
    val allValid: Boolean get() = streams.all { it.chainValid && it.segmentFailureNotes.isEmpty() }
}
