package com.docuhyphen.app.api.model.realtime

import jakarta.websocket.Session
import java.util.concurrent.ScheduledFuture

internal data class ScheduledDeadline(
    val socket: Session,
    val future: ScheduledFuture<*>,
)
