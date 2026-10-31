package com.docuhyphen.app.api.model.document

data class DocumentVersionStorageCleanupRun(
    val removed: Int,
    val kept: Int,
    val failed: Int,
)

enum class DocumentVersionStorageCleanupOutcome {
    REMOVED,
    KEPT,
    FAILED,
}
