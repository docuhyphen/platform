package com.docuhyphen.app.api.model.auth

import java.util.UUID

data class RealtimeTicketIdentity(
    val sessionId: UUID,
    val appUserId: UUID,
)
