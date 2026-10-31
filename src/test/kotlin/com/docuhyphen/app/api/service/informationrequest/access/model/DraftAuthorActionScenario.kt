package com.docuhyphen.app.api.service.informationrequest.access.model

import java.util.*

internal data class DraftAuthorActionScenario(
    val ownerId: UUID,
    val exchangeId: UUID,
    val draftId: UUID,
    val replacementId: UUID,
)
