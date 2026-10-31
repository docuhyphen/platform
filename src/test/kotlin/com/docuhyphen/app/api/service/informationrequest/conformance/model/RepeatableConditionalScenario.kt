package com.docuhyphen.app.api.service.informationrequest.conformance.model

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import java.util.*

internal data class RepeatableGroupOccurrence(val id: UUID, val path: String)

internal data class RepeatableConditionalScenario(
    val requestId: UUID,
    val definitionId: UUID,
    val contributorId: UUID,
    val contributor: RequestAccessContext,
    val label: WalkingSkeletonField,
    val published: PublishedRequest,
    val seen: MutableSet<UUID> = mutableSetOf(),
)
