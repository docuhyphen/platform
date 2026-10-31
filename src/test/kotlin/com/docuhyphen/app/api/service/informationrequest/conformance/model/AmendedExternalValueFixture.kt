package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import java.util.*

internal data class AmendedExternalValueFixture(
    val request: PublishedRequest,
    val schemaVersionId: UUID,
    val summary: WalkingSkeletonField
)
