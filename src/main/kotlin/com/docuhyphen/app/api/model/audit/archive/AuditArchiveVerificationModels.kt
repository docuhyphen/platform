package com.docuhyphen.app.api.model.audit.archive

data class SegmentVerificationResult(val valid: Boolean, val note: String)

data class StreamChainVerificationResult(val valid: Boolean, val note: String, val segmentsChecked: Int)

enum class ArchiveCoverageState
{ COMPLETE, PARTIAL, FAILED }

data class StreamCoverageReport(val streamId: String, val state: ArchiveCoverageState, val note: String)
