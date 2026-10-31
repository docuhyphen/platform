package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import java.util.*

data class ExchangeLifecycleActor(
    val actorId: UUID?,
    val actorKind: AuditActorKind,
) {
    companion object {
        val WORKFLOW = ExchangeLifecycleActor(null, AuditActorKind.WORKFLOW)

        fun person(userId: UUID?) = ExchangeLifecycleActor(userId, AuditActorKind.HUMAN)

        fun accessLinkRecipient(userId: UUID?) = ExchangeLifecycleActor(userId, AuditActorKind.PUBLIC_LINK)
    }
}
