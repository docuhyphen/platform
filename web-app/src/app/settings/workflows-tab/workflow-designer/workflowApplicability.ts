import {WorkflowApplicabilityDraft, WorkflowTriggerEventDto} from "../../../models/models.tsx";

export type ApplicabilityTriggerKind = "EXCHANGE" | "REQUEST" | "SUBMISSION_PACKAGE";

const SUBMISSION_PACKAGE_FIELD = "submissionPackageId";

export const isRequestTrigger = (trigger?: WorkflowTriggerEventDto): boolean =>
    trigger?.subjectResourceType === "INFORMATION_REQUEST";

export const applicabilityTriggerKindOf = (trigger?: WorkflowTriggerEventDto): ApplicabilityTriggerKind | undefined => {
    if (!trigger) return undefined;
    if (!isRequestTrigger(trigger)) return "EXCHANGE";
    return trigger.subjectFields.some(field => field.name === SUBMISSION_PACKAGE_FIELD) ? "SUBMISSION_PACKAGE" : "REQUEST";
};

export const normalizedApplicability = (stored?: Partial<WorkflowApplicabilityDraft>): WorkflowApplicabilityDraft | undefined =>
    stored
        ? {fieldConditions: stored.fieldConditions ?? [], requirementConditions: stored.requirementConditions ?? []}
        : undefined;

export const savedApplicability = (
    applicability: WorkflowApplicabilityDraft | undefined,
    kind: ApplicabilityTriggerKind | undefined,
): WorkflowApplicabilityDraft | undefined =>
{
    const fieldConditions = applicability?.fieldConditions ?? [];
    const requirementConditions = applicability?.requirementConditions ?? [];
    if (kind === undefined) {
        return fieldConditions.length > 0 || requirementConditions.length > 0 ? {
            fieldConditions,
            requirementConditions
        } : undefined;
    }
    if (kind === "EXCHANGE") return fieldConditions.length > 0 ? {fieldConditions} : undefined;
    if (kind === "REQUEST" || requirementConditions.length === 0) return undefined;
    return {fieldConditions: [], requirementConditions};
};

const counted = (count: number, noun: string): string => `${count} ${noun}${count === 1 ? "" : "s"} configured`;

export const applicabilitySummaryOf = (
    applicability: WorkflowApplicabilityDraft | undefined,
    scope: string | undefined,
    kind: ApplicabilityTriggerKind | undefined,
): string =>
{
    if (scope !== "ORG") return "Only organization workflows support applicability conditions";
    if (kind === "REQUEST" || kind === "SUBMISSION_PACKAGE")
    {
        const requirementCount = kind === "SUBMISSION_PACKAGE" ? applicability?.requirementConditions?.length ?? 0 : 0;
        return requirementCount === 0
            ? "Runs for every matching Information Request event"
            : counted(requirementCount, "requirement condition");
    }
    const fieldCount = applicability?.fieldConditions.length ?? 0;
    return fieldCount === 0 ? "Applies to every matching Exchange" : counted(fieldCount, "condition");
};
