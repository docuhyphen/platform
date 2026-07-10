/**
 * Resolved audit scope for the Auditor Portal: either one organization (ORG_AUDIT_READ) or the
 * platform-wide surfaces (APP_AUDIT_READ). Shared by AuditWorkspace and its three sections so
 * each section can pick the matching organization/platform service function without repeating
 * the capability check.
 */
export type AuditScope =
    | {kind: "organization"; organizationId: string}
    | {kind: "platform"};
