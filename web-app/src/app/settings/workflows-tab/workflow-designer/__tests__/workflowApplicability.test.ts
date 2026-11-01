import {describe, expect, it} from "vitest";
import {
    FieldOperator,
    FieldValueType,
    WorkflowFieldConditionDraft,
    WorkflowRequirementConditionDraft,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import {
    applicabilitySummaryOf,
    applicabilityTriggerKindOf,
    isRequestTrigger,
    normalizedApplicability,
    savedApplicability,
} from "../workflowApplicability.ts";

const fieldCondition: WorkflowFieldConditionDraft = {
    fieldDefinitionId: "field-a",
    valueType: FieldValueType.SHORT_TEXT,
    operator: FieldOperator.EQUALS,
    value: "alpha",
};

const requirementCondition: WorkflowRequirementConditionDraft = {
    templateRequirementId: "requirement-a",
    occurrencePath: "root",
    valueType: FieldValueType.BOOLEAN,
    operator: FieldOperator.EQUALS,
    value: true,
};

const trigger = (
    subjectResourceType: "EXCHANGE" | "INFORMATION_REQUEST",
    subjectFieldNames: string[] = [],
): WorkflowTriggerEventDto => ({
    eventName: "subject.changed",
    subjectFields: subjectFieldNames.map(name => ({name, type: "STRING"})),
    isActive: true,
    subjectResourceType,
    subjectSchemaVersion: 1,
});

describe("workflowApplicability", () =>
{
    it("recognizes request triggers by their subject resource type", () =>
    {
        expect(isRequestTrigger(trigger("INFORMATION_REQUEST"))).toBe(true);
        expect(isRequestTrigger(trigger("EXCHANGE"))).toBe(false);
        expect(isRequestTrigger(undefined)).toBe(false);
    });

    it("tells Exchange triggers, request triggers, and triggers that name a Submission Package apart", () => {
        expect(applicabilityTriggerKindOf(trigger("EXCHANGE"))).toBe("EXCHANGE");
        expect(applicabilityTriggerKindOf(trigger("INFORMATION_REQUEST", ["requestId"]))).toBe("REQUEST");
        expect(applicabilityTriggerKindOf(trigger("INFORMATION_REQUEST", ["requestId", "submissionPackageId"])))
            .toBe("SUBMISSION_PACKAGE");
        expect(applicabilityTriggerKindOf(undefined)).toBeUndefined();
    });

    it("fills the condition lists a stored definition left out", () =>
    {
        expect(normalizedApplicability({requirementConditions: [requirementCondition]}))
            .toEqual({fieldConditions: [], requirementConditions: [requirementCondition]});
        expect(normalizedApplicability(undefined)).toBeUndefined();
    });

    it("saves Exchange Field conditions only for Exchange triggers", () =>
    {
        const applicability = {fieldConditions: [fieldCondition], requirementConditions: [] as WorkflowRequirementConditionDraft[]};

        expect(savedApplicability(applicability, "EXCHANGE")).toEqual({fieldConditions: [fieldCondition]});
        expect(savedApplicability(applicability, "SUBMISSION_PACKAGE")).toBeUndefined();
    });

    it("keeps requirement conditions only for a trigger that names a Submission Package", () =>
    {
        const applicability = {fieldConditions: [fieldCondition], requirementConditions: [requirementCondition]};

        expect(savedApplicability(applicability, "SUBMISSION_PACKAGE"))
            .toEqual({fieldConditions: [], requirementConditions: [requirementCondition]});
        expect(savedApplicability(applicability, "REQUEST")).toBeUndefined();
    });

    it("keeps what was loaded when the trigger could not be read", () => {
        const applicability = {fieldConditions: [] as WorkflowFieldConditionDraft[], requirementConditions: [requirementCondition]};

        expect(savedApplicability(applicability, undefined)).toEqual(applicability);
        expect(savedApplicability({fieldConditions: [], requirementConditions: []}, undefined)).toBeUndefined();
    });

    it("describes what limits the workflow for each trigger kind and scope", () =>
    {
        expect(applicabilitySummaryOf({fieldConditions: [fieldCondition]}, "ORG", "EXCHANGE")).toBe("1 condition configured");
        expect(applicabilitySummaryOf(undefined, "ORG", "EXCHANGE")).toBe("Applies to every matching Exchange");
        expect(applicabilitySummaryOf(undefined, "ORG", "REQUEST")).toBe("Runs for every matching Information Request event");
        expect(applicabilitySummaryOf({
            fieldConditions: [],
            requirementConditions: [requirementCondition]
        }, "ORG", "SUBMISSION_PACKAGE"))
            .toBe("1 requirement condition configured");
        expect(applicabilitySummaryOf(undefined, "APP", "EXCHANGE")).toBe("Only organization workflows support applicability conditions");
    });
});
