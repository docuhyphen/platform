package com.docuhyphen.app.api.model.exchange

import java.util.*

data class ExchangeBlueprintInformationRequestStart(
    val blueprintDefinitionId: UUID,
    val templateVersionId: UUID,
)
