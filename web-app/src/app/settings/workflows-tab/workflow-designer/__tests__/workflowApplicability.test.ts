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

const trigger = (subjectResourceType: "EXCHANGE" | "INFORMATION_REQUEST"): WorkflowTriggerEventDto => ({
    eventName: "subject.changed",
    subjectFields: [],
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

    it("fills the condition lists a stored definition left out", () =>
    {
        expect(normalizedApplicability({requirementConditions: [requirementCondition]}))
            .toEqual({fieldConditions: [], requirementConditions: [requirementCondition]});
        expect(normalizedApplicability(undefined)).toBeUndefined();
    });

    it("saves Exchange Field conditions only for Exchange triggers", () =>
    {
        const applicability = {fieldConditions: [fieldCondition], requirementConditions: []};

        expect(savedApplicability(applicability, false)).toEqual({fieldConditions: [fieldCondition]});
        expect(savedApplicability(applicability, true)).toBeUndefined();
    });

    it("keeps the requirement conditions of a request trigger", () =>
    {
        const applicability = {fieldConditions: [fieldCondition], requirementConditions: [requirementCondition]};

        expect(savedApplicability(applicability, true))
            .toEqual({fieldConditions: [], requirementConditions: [requirementCondition]});
    });

    it("describes what limits the workflow for each trigger kind and scope", () =>
    {
        expect(applicabilitySummaryOf({fieldConditions: [fieldCondition]}, "ORG", false)).toBe("1 condition configured");
        expect(applicabilitySummaryOf(undefined, "ORG", false)).toBe("Applies to every matching Exchange");
        expect(applicabilitySummaryOf(undefined, "ORG", true)).toBe("Runs for every matching Information Request event");
        expect(applicabilitySummaryOf({fieldConditions: [], requirementConditions: [requirementCondition]}, "ORG", true))
            .toBe("1 requirement condition configured");
        expect(applicabilitySummaryOf(undefined, "APP", false)).toBe("Only organization workflows support applicability conditions");
    });
});
