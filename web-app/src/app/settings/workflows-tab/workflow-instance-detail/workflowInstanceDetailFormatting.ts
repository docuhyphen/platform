export type StepStatusColor = "success" | "warning" | "danger" | "informative" | "subtle";

export const STEP_STATUS_COLORS: Record<string, StepStatusColor> = {
    APPROVED: "success",
    COMPLETED: "success",
    PENDING: "informative",
    REJECTED: "danger",
    ESCALATED: "warning",
    SKIPPED: "subtle",
};

export const formatDateTime = (iso?: string): string =>
    iso ? new Date(iso).toLocaleString(undefined, {dateStyle: "short", timeStyle: "short"}) : "";

export const formatEpoch = (ms: number): string =>
    new Date(ms).toLocaleString(undefined, {dateStyle: "short", timeStyle: "short"});
