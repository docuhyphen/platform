export const auditWorkspaceTabIds = {
    events: "AuditEventsTab",
    integrity: "AuditIntegrityTab",
    exports: "AuditExportsTab",
} as const;

export const auditWorkspaceTabLabels: Record<string, string> = {
    [auditWorkspaceTabIds.events]: "Events",
    [auditWorkspaceTabIds.integrity]: "Integrity",
    [auditWorkspaceTabIds.exports]: "Exports",
};
