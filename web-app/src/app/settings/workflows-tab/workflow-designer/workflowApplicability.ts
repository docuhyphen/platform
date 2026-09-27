import {WorkflowApplicabilityDraft, WorkflowTriggerEventDto} from "../../../models/models.tsx";

export const isRequestTrigger = (trigger?: WorkflowTriggerEventDto): boolean =>
    trigger?.subjectResourceType === "INFORMATION_REQUEST";

export const normalizedApplicability = (stored?: Partial<WorkflowApplicabilityDraft>): WorkflowApplicabilityDraft | undefined =>
    stored
        ? {fieldConditions: stored.fieldConditions ?? [], requirementConditions: stored.requirementConditions ?? []}
        : undefined;

export const savedApplicability = (
    applicability: WorkflowApplicabilityDraft | undefined,
    requestTrigger: boolean,
): WorkflowApplicabilityDraft | undefined =>
{
    if (!requestTrigger)
    {
        const fieldConditions = applicability?.fieldConditions ?? [];
        return fieldConditions.length > 0 ? {fieldConditions} : undefined;
    }
    const requirementConditions = applicability?.requirementConditions ?? [];
    return requirementConditions.length > 0 ? {fieldConditions: [], requirementConditions} : undefined;
};

const counted = (count: number, noun: string): string => `${count} ${noun}${count === 1 ? "" : "s"} configured`;

export const applicabilitySummaryOf = (
    applicability: WorkflowApplicabilityDraft | undefined,
    scope: string | undefined,
    requestTrigger: boolean,
): string =>
{
    if (scope !== "ORG") return "Only organization workflows support applicability conditions";
    if (requestTrigger)
    {
        const requirementCount = applicability?.requirementConditions?.length ?? 0;
        return requirementCount === 0
            ? "Runs for every matching Information Request event"
            : counted(requirementCount, "requirement condition");
    }
    const fieldCount = applicability?.fieldConditions.length ?? 0;
    return fieldCount === 0 ? "Applies to every matching Exchange" : counted(fieldCount, "condition");
};
