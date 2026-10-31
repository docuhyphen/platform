package com.docuhyphen.app.api.migration.model

import java.util.*

internal data class NextSubmissionVersion(
    val versionId: UUID,
    val sectionId: UUID,
    val documentBindingId: UUID,
    val attestationBindingId: UUID,
)
