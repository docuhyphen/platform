package com.docuhyphen.app.api.model.notification

import java.util.*

internal data class DomainEventOwnerColumns(
    val ownerKind: String,
    val ownerId: UUID?,
)
