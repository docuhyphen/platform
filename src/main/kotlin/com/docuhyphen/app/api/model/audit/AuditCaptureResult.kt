package com.docuhyphen.app.api.model.audit

import java.util.UUID

sealed class AuditCaptureResult
{
    data class Captured(val outboxId: UUID, val eventId: UUID) : AuditCaptureResult()

    data class Degraded(val eventId: UUID, val reason: String) : AuditCaptureResult()
}
