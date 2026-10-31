package com.docuhyphen.app.api.service.informationrequest.externalsource.model

internal data class ConnectorAuditEvent(
    val transitionId: String,
    val mutation: String,
    val transitionActorKind: String,
    val eventTypeKey: String?,
    val outcome: String?,
    val actorKind: String?,
    val actorId: String?,
    val idempotencyKey: String?,
    val payload: Map<String, String>,
)
