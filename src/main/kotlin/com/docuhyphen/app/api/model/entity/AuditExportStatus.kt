package com.docuhyphen.app.api.model.entity

enum class AuditExportStatus
{
    REQUESTED,
    APPROVAL_PENDING,
    BUILDING,
    READY,
    EXPIRED,
    FAILED,
    REVOKED,
}
