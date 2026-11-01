package com.docuhyphen.app.api.model.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditEventType

sealed class AuditDraftValidationResult
{
    data class Valid(val eventType: AuditEventType) : AuditDraftValidationResult()

    data class Invalid(val errors: List<String>) : AuditDraftValidationResult()
}
