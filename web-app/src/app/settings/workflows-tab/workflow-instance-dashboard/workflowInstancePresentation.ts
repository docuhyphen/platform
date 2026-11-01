import {WorkflowInstanceSummaryDto} from "../../../models/models.tsx";

export type InstanceStatus = WorkflowInstanceSummaryDto["status"] | "";
export type InstanceStatusColor = "success" | "warning" | "danger" | "informative" | "subtle";

export const INSTANCE_PAGE_SIZE = 15;

export const INSTANCE_STATUS_COLORS: Record<string, InstanceStatusColor> = {
    RUNNING: "informative",
    COMPLETED: "success",
    REJECTED: "danger",
    CANCELLED: "subtle",
    ESCALATED: "warning",
    FAILED: "danger",
};

export const formatInstanceDate = (iso: string): string => new Date(iso).toLocaleDateString(undefined, {
    year: "numeric", month: "short", day: "numeric",
});
