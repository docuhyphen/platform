package com.docuhyphen.app.api.model.entity

/**
 * State machine for [AuditExport].
 *
 * `REQUESTED` -> (`APPROVAL_PENDING` if dual control is required) -> `BUILDING` -> `READY`, with
 * `FAILED`/`EXPIRED`/`REVOKED` as terminal off-ramps. `READY` is itself terminal for evidence
 * purposes once `EXPIRED`/`REVOKED` - the bundle object is never deleted (WORM), only access is
 * withdrawn by the service layer.
 */
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
