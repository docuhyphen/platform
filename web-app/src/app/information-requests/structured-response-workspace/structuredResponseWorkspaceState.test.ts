import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueDto,
    FieldValueType,
    InformationRequestConditionEvaluationState,
    InformationRequestContributorRole,
    InformationRequestGroupOccurrenceDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseEditLock,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {
    buildResponsePatches,
    conditionScopeMap,
    explanationsAwaited,
    isRequirementActive,
    lockedResponseKeys,
    occurrencePresentation,
    responseKey,
} from "./structuredResponseWorkspaceState.ts";
import {ResponseAnswerEdits} from "./responseAnswerState.ts";

interface RuntimeRequirementCorrelation
{
    sourceTemplateRequirementId: string;
    sourceTemplateBindingId: string;
}

const occurrence: InformationRequestGroupOccurrenceDto = {
    id: "occurrence-1",
    informationRequestId: "request-1",
    sourceTemplateGroupId: "group-1",
    occurrenceIndex: 0,
    occurrencePath: "items[0]",
    createdAt: Date.parse("2026-09-11T00:00:00Z"),
};

const group: InformationRequestTemplateGroupDto = {
    id: "group-1",
    groupKey: "items",
    minOccurrences: 0,
    maxOccurrences: 3,
};

const binding = (
    id: string,
    fieldContractId: string,
    fieldDefinitionId: string,
    fieldKey: string,
): SchemaFieldBindingDto => ({
    id,
    fieldContractId,
    fieldDefinitionId,
    namespace: "process",
    fieldKey,
    label: fieldKey,
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: "Data",
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.PUBLIC,
    constraints: {},
    options: [],
});

const requirement = (
    sourceTemplateBindingId: string,
    sourceTemplateRequirementId: string,
    fieldDefinitionId: string,
): InformationRequestTemplateRequirementDto => ({
    id: sourceTemplateBindingId,
    templateRequirementId: sourceTemplateRequirementId,
    requirementKey: sourceTemplateBindingId,
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: sourceTemplateBindingId,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    occurrenceAnchorKey: "items",
    collectedFieldDefinitionId: fieldDefinitionId,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (
    runtimeRequirementId: string,
    sourceTemplateBindingId: string,
    sourceTemplateRequirementId: string,
): InformationRequestResponseDto & RuntimeRequirementCorrelation => ({
    informationRequestRequirementId: runtimeRequirementId,
    sourceTemplateBindingId,
    sourceTemplateRequirementId,
    occurrencePath: "items[0]",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetId: "value-set-1",
    fieldValueSetETag: "\"field-set:1\"",
    fieldValues: [
        {
            fieldContractId: "field-contract-a",
            schemaFieldBindingId: "binding-a",
            namespace: "process",
            fieldKey: "first",
            label: "First",
            valueType: FieldValueType.SHORT_TEXT,
            isEmpty: false,
            value: "stored first",
        },
        {
            fieldContractId: "field-contract-b",
            schemaFieldBindingId: "binding-b",
            namespace: "process",
            fieldKey: "second",
            label: "Second",
            valueType: FieldValueType.SHORT_TEXT,
            isEmpty: false,
            value: "stored second",
        },
    ],
    responseRevision: 1,
    updatedAt: Date.parse("2026-09-11T00:00:00Z"),
});

describe("structuredResponseWorkspaceState", () =>
{
    it("uses runtime to Template correlation when saving the second Field in a shared occurrence", () =>
    {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const secondBinding = binding("binding-b", "field-contract-b", "field-definition-b", "second");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const secondRequirement = requirement("template-binding-b", "template-requirement-b", "field-definition-b");

        const patches = buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement, secondRequirement],
            [firstBinding, secondBinding],
            [
                response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
                response("runtime-requirement-b", "template-binding-b", "template-requirement-b"),
            ],
            new Map(),
            {
                [responseKey(secondRequirement.id, occurrence.occurrencePath)]: {
                    [secondBinding.fieldContractId]: "updated second",
                },
            },
        );

        expect(patches).toEqual([
            {
                requirementId: "runtime-requirement-b",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:1\"",
                    values: [
                        {
                            fieldContractId: "field-contract-b",
                            value: "updated second",
                        },
                    ],
                },
            },
        ]);
    });

    it("names distinct runtime Requirements when saving both Fields in a shared occurrence", () =>
    {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const secondBinding = binding("binding-b", "field-contract-b", "field-definition-b", "second");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const secondRequirement = requirement("template-binding-b", "template-requirement-b", "field-definition-b");

        const patches = buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement, secondRequirement],
            [firstBinding, secondBinding],
            [
                response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
                response("runtime-requirement-b", "template-binding-b", "template-requirement-b"),
            ],
            new Map(),
            {
                [responseKey(firstRequirement.id, occurrence.occurrencePath)]: {
                    [firstBinding.fieldContractId]: "updated first",
                },
                [responseKey(secondRequirement.id, occurrence.occurrencePath)]: {
                    [secondBinding.fieldContractId]: "updated second",
                },
            },
        );

        expect(patches.map(patch => patch?.requirementId)).toEqual([
            "runtime-requirement-a",
            "runtime-requirement-b",
        ]);
    });

    it("presents an occurrence by the Template group it names and leaves out one whose group the Template does not name", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const childGroup: InformationRequestTemplateGroupDto = {
            id: "group-2",
            groupKey: "parts",
            parentGroupKey: "items",
            minOccurrences: 0,
            maxOccurrences: 2
        };
        const unnamed: InformationRequestGroupOccurrenceDto = {...occurrence, sourceTemplateGroupId: "group-unknown"};

        expect(occurrencePresentation(occurrence, [group, childGroup]))
            .toEqual({groupKey: "items", isRootOccurrence: false, childGroups: [childGroup]});
        expect(occurrencePresentation(unnamed, [group, childGroup])).toBeUndefined();
        expect(buildResponsePatches(
            [unnamed],
            [group],
            [firstRequirement],
            [firstBinding],
            [response("runtime-requirement-a", "template-binding-a", "template-requirement-a")],
            new Map(),
            {[responseKey(firstRequirement.id, unnamed.occurrencePath)]: {[firstBinding.fieldContractId]: "updated first"}},
        )).toEqual([]);
    });

    it("leaves an answer its submission locks out of the batch even while a change to it is kept", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const secondBinding = binding("binding-b", "field-contract-b", "field-definition-b", "second");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const secondRequirement = requirement("template-binding-b", "template-requirement-b", "field-definition-b");
        const responses = [
            {
                ...response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
                editLock: InformationRequestResponseEditLock.SUBMITTED,
            },
            response("runtime-requirement-b", "template-binding-b", "template-requirement-b"),
        ];

        const patches = buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement, secondRequirement],
            [firstBinding, secondBinding],
            responses,
            new Map(),
            {
                [responseKey(firstRequirement.id, occurrence.occurrencePath)]: {[firstBinding.fieldContractId]: "kept first"},
                [responseKey(secondRequirement.id, occurrence.occurrencePath)]: {[secondBinding.fieldContractId]: "updated second"},
            },
        );

        expect(patches.map(patch => patch?.requirementId)).toEqual(["runtime-requirement-b"]);
        expect(lockedResponseKeys([occurrence], [group], [firstRequirement, secondRequirement], responses, new Map()))
            .toEqual([responseKey(firstRequirement.id, occurrence.occurrencePath)]);
    });

    it("sends nothing for a choice of answer that equals the stored one", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const firstRequirement = {
            ...requirement("template-binding-a", "template-requirement-a", "field-definition-a"),
            permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
        };
        const stored = {
            ...response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
            disposition: InformationRequestResponseDisposition.PROVIDED,
        };
        const key = responseKey(firstRequirement.id, occurrence.occurrencePath);
        const patchesFor = (disposition: InformationRequestResponseDisposition, narrative?: string) => buildResponsePatches(
            [occurrence], [group], [firstRequirement], [firstBinding], [stored], new Map(), {}, {
                [key]: {
                    disposition,
                    narrative
                }
            },
        );

        expect(patchesFor(InformationRequestResponseDisposition.PROVIDED)).toEqual([]);
        expect(patchesFor(InformationRequestResponseDisposition.NOT_APPLICABLE, "Not kept this period").map(patch => patch?.disposition))
            .toEqual([InformationRequestResponseDisposition.NOT_APPLICABLE]);
    });

    it("holds an answer that needs an explanation until one is given, and names it as awaited", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const firstRequirement = {
            ...requirement("template-binding-a", "template-requirement-a", "field-definition-a"),
            permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.UNAVAILABLE],
        };
        const stored = response("runtime-requirement-a", "template-binding-a", "template-requirement-a");
        const key = responseKey(firstRequirement.id, occurrence.occurrencePath);
        const patchesFor = (answers: ResponseAnswerEdits) =>
            buildResponsePatches([occurrence], [group], [firstRequirement], [firstBinding], [stored], conditionScopeMap([]), {}, answers);
        const awaitedFor = (answers: ResponseAnswerEdits) =>
            explanationsAwaited([occurrence], [group], [firstRequirement], [firstBinding], [stored], conditionScopeMap([]), {}, answers);
        const unavailable = (narrative?: string) => ({
            [key]: {
                disposition: InformationRequestResponseDisposition.UNAVAILABLE,
                narrative
            }
        });

        expect(patchesFor(unavailable())).toEqual([]);
        expect(awaitedFor(unavailable())).toEqual([key]);
        expect(patchesFor(unavailable("  "))).toEqual([]);
        expect(awaitedFor(unavailable("The source is unavailable"))).toEqual([]);
        expect(patchesFor(unavailable("The source is unavailable"))).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            narrative: "The source is unavailable",
        }]);
    });

    it("keeps an amended answer with one choice of answer only when the respondent asks to keep it", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");
        const amended = {
            ...response("runtime-requirement-a", "template-binding-a", "template-requirement-a"),
            disposition: InformationRequestResponseDisposition.PROVIDED,
            reconfirmationRequired: true,
        };
        const key = responseKey(firstRequirement.id, occurrence.occurrencePath);
        const patchesFor = (answers: Record<string, {
            reconfirmed?: boolean
        }>, stored = amended) => buildResponsePatches(
            [occurrence], [group], [firstRequirement], [firstBinding], [stored], new Map(), {}, answers,
        );

        expect(patchesFor({})).toEqual([]);
        expect(patchesFor({[key]: {reconfirmed: true}}))
            .toEqual([{
                requirementId: "runtime-requirement-a",
                disposition: InformationRequestResponseDisposition.PROVIDED
            }]);
        expect(patchesFor({[key]: {reconfirmed: true}}, {...amended, reconfirmationRequired: false})).toEqual([]);
    });

    it("sends no patch for a Field Requirement the server named no runtime Requirement for", () => {
        const firstBinding = binding("binding-a", "field-contract-a", "field-definition-a", "first");
        const firstRequirement = requirement("template-binding-a", "template-requirement-a", "field-definition-a");

        expect(buildResponsePatches(
            [occurrence],
            [group],
            [firstRequirement],
            [firstBinding],
            [],
            new Map(),
            {[responseKey(firstRequirement.id, occurrence.occurrencePath)]: {[firstBinding.fieldContractId]: "updated first"}},
        )).toEqual([]);
    });

    it("asks a conditional item whose condition result was withheld exactly when the server returned its response", () => {
        const conditional = {
            ...requirement("conditional-binding", "conditional-template", "conditional-field"),
            conditionalRuleKey: "conditional-rule",
        };
        const answered = {
            informationRequestRequirementId: "runtime-conditional",
            sourceTemplateRequirementId: "conditional-binding",
            sourceTemplateBindingId: "conditional-binding",
            occurrencePath: "items[0]",
            disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
            fieldValues: [] as FieldValueDto[],
            responseRevision: 0,
            updatedAt: Date.parse("2026-09-11T00:00:00Z"),
        } satisfies InformationRequestResponseDto;
        const withheld = conditionScopeMap([]);
        const evaluatedFalse = conditionScopeMap([{
            ruleKey: "conditional-rule",
            expressionVersion: 1,
            occurrencePath: "items[0]",
            state: InformationRequestConditionEvaluationState.FALSE,
        }]);

        expect(isRequirementActive(conditional, "items[0]", withheld, [answered])).toBe(true);
        expect(isRequirementActive(conditional, "items[0]", withheld, [])).toBe(false);
        expect(isRequirementActive(conditional, "items[1]", withheld, [answered])).toBe(false);
        expect(isRequirementActive(conditional, "items[0]", evaluatedFalse, [answered])).toBe(false);
    });
});
