package com.docuhyphen.app.api.model.auth

import java.util.*

data class RealtimeTicketIdentity(
    val sessionId: UUID,
    val appUserId: UUID,
)
