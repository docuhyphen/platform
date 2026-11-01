import {WorkflowStepInstanceDto} from "../../../models/models.tsx";
import {STEP_STATUS_LABELS} from "../../../settings/workflows-tab/workflowUtils.ts";

export const STEP_STATUS_COLORS: Record<string, "success" | "warning" | "danger" | "informative" | "subtle"> = {
    APPROVED: "success",
    COMPLETED: "success",
    PENDING: "informative",
    REJECTED: "danger",
    ESCALATED: "warning",
    SKIPPED: "subtle",
};

export const formatDateTime = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" }) : "";

const formatStepStatus = (status: string) =>
{
    const label = (STEP_STATUS_LABELS[status] ?? status).toLowerCase();
    return label.charAt(0).toUpperCase() + label.slice(1);
};

export const formatEpoch = (ms: number) =>
    new Date(ms).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });

export const principalLabel = (displayName?: string, email?: string, fallback?: string): string =>
{
    if (displayName && email) return `${displayName} (${email})`;
    return displayName || email || fallback || "";
};

export const collapsedBadgeLabel = (step: WorkflowStepInstanceDto, isActivePending: boolean): string =>
{
    if (isActivePending) return "Waiting";
    if (step.decisions.some(d => d.decision === "APPROVE")) return "Approved";
    if (step.decisions.some(d => d.decision === "REJECT")) return "Rejected";
    return formatStepStatus(step.status);
};

export const collapsedSummaryText = (step: WorkflowStepInstanceDto, isActivePending: boolean): string =>
{
    if (isActivePending) return "";
    const latestApprove = step.decisions.find(d => d.decision === "APPROVE");
    if (latestApprove)
        return `by ${principalLabel(latestApprove.displayName, '', latestApprove.principalId)}`;
    const latestReject = step.decisions.find(d => d.decision === "REJECT");
    if (latestReject)
        return `by ${principalLabel(latestReject.displayName, '', latestReject.principalId)} · ${formatEpoch(latestReject.atEpochMillis)}`;
    return "";
};
