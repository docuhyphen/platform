package com.docuhyphen.app.api.service.informationrequest.notice.model

import java.time.Instant
import java.util.*

internal data class AttributedNotice(
    val actorKind: String,
    val actorLabel: String?,
    val sessionId: String?,
    val actorId: UUID?,
)

internal data class NoticeFixture(
    val requestId: UUID,
    val organizationId: UUID,
    val ownerUserId: UUID,
    val clockId: UUID,
    val start: Instant,
)
