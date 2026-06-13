/**
 * Formats a workflow trigger event name into a human-readable label.
 * e.g. "exchange.acceptance_pending" -> "Exchange: Acceptance Pending"
 */
export const formatTriggerName = (eventName: string): string =>
{
    if (!eventName) return "";
    return eventName
        .split('.')
        .map(part =>
            part
                .split('_')
                .map(w => w.charAt(0).toUpperCase() + w.slice(1))
                .join(' '),
        )
        .join(': ');
};

export const INSTANCE_STATUS_LABELS: Record<string, string> = {
    RUNNING: "In Progress",
    COMPLETED: "Completed",
    REJECTED: "Rejected",
    CANCELLED: "Cancelled",
    ESCALATED: "Escalated",
};

export const STEP_TYPE_LABELS: Record<string, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
};

export const STEP_STATUS_LABELS: Record<string, string> = {
    PENDING: "Waiting",
    COMPLETED: "Completed",
    REJECTED: "Rejected",
    ESCALATED: "Escalated",
    SKIPPED: "Skipped",
};

export const DECISION_LABELS: Record<string, string> = {
    APPROVE: "Approved",
    REJECT: "Rejected",
};

export const PRINCIPAL_KIND_LABELS: Record<string, string> = {
    APP_USER: "User",
    PRINCIPAL: "User",
    ROLE: "Role",
    GROUP_ROLE: "Group member",
};

/** Returns a human-readable label for a raw enum value, falling back to title-casing. */
export const humanize = (raw: string): string =>
{
    if (!raw) return "";
    return raw
        .split('_')
        .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
        .join(' ');
};
