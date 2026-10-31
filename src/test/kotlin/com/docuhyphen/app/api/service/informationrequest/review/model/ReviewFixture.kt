package com.docuhyphen.app.api.service.informationrequest.review.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class ReviewFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val reviewerPartyId: UUID,
    val reviewerUserId: UUID,
) {
    val requestId: UUID get() = runtime.requestId
    val documentRequirementId: UUID get() = runtime.documentRequirementId
    val attestationRequirementId: UUID get() = runtime.attestationRequirementId
    val optionalRequirementId: UUID get() = runtime.optionalRequirementId
    val artifactId: UUID get() = runtime.artifactId
}
