package com.docuhyphen.app.api.service.informationrequest.template.model

import java.util.*

internal data class PublicationStoredDraft(
    val definitionId: UUID,
    val versionId: UUID,
    val actorId: UUID,
)
