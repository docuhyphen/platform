package com.docuhyphen.app.api.service.informationrequest.capability.model

import java.util.*

data class WalkingSkeletonDraft(
    val definitionId: UUID,
    val versionId: UUID,
    val actorId: UUID,
    val templateKey: String,
)

data class WalkingSkeletonField(
    val fieldDefinitionId: UUID,
    val fieldContractId: UUID,
)
