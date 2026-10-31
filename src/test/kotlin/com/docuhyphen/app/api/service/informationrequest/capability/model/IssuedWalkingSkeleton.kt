package com.docuhyphen.app.api.service.informationrequest.capability.model

import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import java.util.*

internal data class IssuedWalkingSkeleton(
    val fixture: InformationRequestTemplateWalkingSkeletonFixture,
    val request: PublishedRequest,
) {
    val requestId: UUID get() = request.requestId

    val anchoredRequirementKeys: Set<String>
        get() = fixture.configuration.sections
            .flatMap { it.requirements }
            .filter { it.occurrenceAnchorKey != null }
            .map { it.requirementKey }
            .toSet()
}
