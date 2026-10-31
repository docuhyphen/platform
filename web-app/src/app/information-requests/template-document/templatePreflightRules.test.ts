import {describe, expect, it} from "vitest";
import {
    FieldOperator,
    FieldValueType,
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestSubmissionMode,
    InformationRequestTemplateConditionRuleRequest,
    InformationRequestTemplateRequirementRequest,
} from "../../models/models.tsx";
import {emptyDraftDocument, newRequirement, TemplateDraftDocument} from "./templateDraftDocument.ts";
import {templateDraftProblems} from "./templateDraftValidation.ts";

const typed = (key: string, extra: Partial<InformationRequestTemplateRequirementRequest> = {}) => ({
    ...newRequirement(InformationRequestRequirementType.FIELD, key, `Provide ${key}`),
    collectedFieldDefinitionId: `field-${key}`,
    ...extra,
});

const requested = (key: string, extra: Partial<InformationRequestTemplateRequirementRequest> = {}) => ({
    ...newRequirement(InformationRequestRequirementType.DOCUMENT, key, `Upload ${key}`),
    evidencePolicy: {},
    ...extra,
});

const answeredBy = (ruleKey: string, sourceRequirementKey: string): InformationRequestTemplateConditionRuleRequest => ({
    ruleKey,
    predicates: [{
        sourceRequirementKey,
        operator: FieldOperator.EQUALS,
        expectedDisposition: InformationRequestResponseDisposition.PROVIDED,
    }],
});

const comparing = (valueType: FieldValueType, value: unknown, operator = FieldOperator.EQUALS): InformationRequestTemplateConditionRuleRequest => ({
    ruleKey: "when-recorded",
    predicates: [{fieldDefinitionId: "field-recorded-note", operator, valueType, value}],
});

const documentWith = (
    requirements: InformationRequestTemplateRequirementRequest[],
    overrides: Partial<TemplateDraftDocument> = {},
): TemplateDraftDocument => ({
    ...emptyDraftDocument(),
    schemaVersionId: "schema-version-1",
    sections: [{sectionKey: "collected-data", title: "Collected data", requirements}],
    ...overrides,
});

const messages = (document: TemplateDraftDocument, optionCodes = new Map<string, ReadonlySet<string>>(), retired = false) =>
    templateDraftProblems(document, undefined, undefined, {retired, optionCodes}).map(problem => problem.message);

const conditional = (ruleKey: string) => ({
    requiredness: InformationRequestRequiredness.CONDITIONAL,
    conditionalRuleKey: ruleKey
});

describe("template preflight rules the server also applies", () => {
    it("names conditional requirements whose conditions read each other", () => {
        expect(messages(documentWith(
            [typed("first-note", conditional("when-second")), typed("second-note", conditional("when-first"))],
            {conditionRules: [answeredBy("when-first", "first-note"), answeredBy("when-second", "second-note")]},
        ))).toContain("Conditional requirements first-note then second-note then first-note depend on each other.");
    });

    it("refuses a repeatable group answered in two submission stages", () => {
        expect(messages({
            ...emptyDraftDocument(),
            schemaVersionId: "schema-version-1",
            submissionMode: InformationRequestSubmissionMode.STAGED,
            groups: [{groupKey: "per-item"}],
            sections: [
                {
                    sectionKey: "first",
                    title: "First",
                    submissionStageKey: "one",
                    requirements: [typed("item-name", {occurrenceAnchorKey: "per-item"})]
                },
                {
                    sectionKey: "second",
                    title: "Second",
                    submissionStageKey: "two",
                    requirements: [typed("item-size", {occurrenceAnchorKey: "per-item"})]
                },
            ],
        })).toContain('Group "per-item" and its nested groups are answered in stages one and two, so adding or removing one of its occurrences would change more than one stage.');
    });

    it("refuses an alternative document that names alternatives of its own", () => {
        expect(messages(documentWith([
            requested("primary-record", {substituteRequirementKeys: ["alternate-record"]}),
            requested("alternate-record", {substituteRequirementKeys: ["fallback-record"]}),
            requested("fallback-record"),
        ]))).toContain('Requirement "Upload alternate-record" stands in for another document, so it cannot name alternatives of its own.');
    });

    it("refuses an alternative the document's own occurrence could never find", () => {
        expect(messages(documentWith([
            requested("primary-record", {substituteRequirementKeys: ["alternate-record"]}),
            requested("alternate-record", {occurrenceAnchorKey: "per-item"}),
        ], {groups: [{groupKey: "per-item"}]}))).toContain(
            'Requirement "Upload primary-record" names "alternate-record" as its alternative, but "alternate-record" repeats per "per-item"; an alternative is found only at the top level or in the document\'s own group.',
        );
    });

    it("refuses a condition that reads a confirmation", () => {
        const confirmation = newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "confirm-record", "Confirm the record");

        expect(messages(documentWith(
            [confirmation, typed("summary", conditional("when-confirmed"))],
            {conditionRules: [answeredBy("when-confirmed", "confirm-record")]},
        ))).toContain('Condition "when-confirmed" reads "confirm-record", a confirmation; a condition reads an answer or a document.');
    });

    it("accepts a condition that reads a requested document", () => {
        const problems = messages(documentWith(
            [requested("supporting-record"), typed("summary", conditional("when-filed"))],
            {conditionRules: [answeredBy("when-filed", "supporting-record")]},
        ));

        expect(problems.filter(problem => problem.includes("when-filed"))).toEqual([]);
    });

    it("refuses a condition source the governed requirement could never read", () => {
        expect(messages(documentWith(
            [typed("item-name", {occurrenceAnchorKey: "per-item"}), typed("summary", conditional("when-named"))],
            {groups: [{groupKey: "per-item"}], conditionRules: [answeredBy("when-named", "item-name")]},
        ))).toContain(
            'Condition "when-named" reads "item-name", which repeats per "per-item", but decides "Provide summary", which can read only answers at the top level or in its own or an enclosing group.',
        );
    });

    it("refuses accepted values for an attribute the document never asks for", () => {
        expect(messages(documentWith([requested("supporting-record", {
            evidencePolicy: {
                issuerRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                acceptedValues: [{
                    attribute: InformationRequestEvidenceAttribute.ISSUER,
                    acceptedValue: "Records office"
                }],
            },
        })]))).toContain('Requirement "Upload supporting-record" never asks for the issuer, so it cannot restrict which issuers are accepted.');
    });

    it("refuses a confidentiality compartment or compared value longer than the server keeps", () => {
        const problems = messages(documentWith(
            [typed("recorded-note", {confidentialityCompartmentKey: "c".repeat(65)})],
            {conditionRules: [comparing(FieldValueType.SHORT_TEXT, "v".repeat(257))]},
        ));

        expect(problems).toContain('Requirement "recorded-note" has a confidentiality compartment longer than 64 characters.');
        expect(problems).toContain('Condition "when-recorded" compares a value longer than 256 characters.');
    });

    it.each([
        [FieldValueType.INTEGER, "2.5", 'Condition "when-recorded" compares a whole number Field with "2.5", which is not a whole number.'],
        [FieldValueType.INTEGER, "abc", 'Condition "when-recorded" compares a whole number Field with "abc", which is not a whole number.'],
        [FieldValueType.DECIMAL, "4,50", 'Condition "when-recorded" compares a number Field with "4,50", which is not a number.'],
        [FieldValueType.DATE, "2026/01/31", 'Condition "when-recorded" compares a date Field with "2026/01/31", which is not a date such as 2026-01-31.'],
        [FieldValueType.BOOLEAN, "yes", 'Condition "when-recorded" compares a yes or no Field with "yes", which is not true or false.'],
    ])("refuses a %s condition value of %s", (valueType, value, expected) => {
        expect(messages(documentWith([typed("recorded-note")], {conditionRules: [comparing(valueType, value)]}))).toContain(expected);
    });

    it("refuses an option code the compared Field does not offer", () => {
        const options = new Map<string, ReadonlySet<string>>([["field-recorded-note", new Set(["routine", "priority"])]]);

        expect(messages(documentWith([typed("recorded-note")], {
            conditionRules: [comparing(FieldValueType.MULTI_SELECT, ["routine", "archived"], FieldOperator.IN)],
        }), options)).toContain('Condition "when-recorded" compares with "archived", which is not an option of its Field.');
        expect(messages(documentWith([typed("recorded-note")], {
            conditionRules: [comparing(FieldValueType.SINGLE_SELECT, "priority")],
        }), options)).not.toContain('Condition "when-recorded" compares with "priority", which is not an option of its Field.');
    });

    it("says the chosen Request Schema was retired", () => {
        expect(messages(documentWith([typed("recorded-note")]), undefined, true))
            .toContain("The Request Schema this draft names was retired, so saving is refused until you choose a published one.");
    });

    it("refuses groups nested deeper or repeating more than the server allows", () => {
        const chain = ["g1", "g2", "g3", "g4", "g5", "g6"].map((groupKey, index, keys) => ({
            groupKey,
            parentGroupKey: index > 0 ? keys[index - 1] : undefined,
        }));
        const problems = messages(documentWith(
            [typed("deep-note", {occurrenceAnchorKey: "g6"}), typed("wide-note", {occurrenceAnchorKey: "wide"})],
            {groups: [...chain, {groupKey: "wide", minOccurrences: 101, maxOccurrences: 1001}]},
        ));

        expect(problems).toContain('Group "g6" is nested more than 5 groups deep.');
        expect(problems).toContain('Group "wide" requires at most 100 occurrences.');
        expect(problems).toContain('Group "wide" allows at most 1000 occurrences.');
    });
});
